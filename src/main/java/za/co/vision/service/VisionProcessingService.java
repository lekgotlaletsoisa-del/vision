package za.co.vision.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import za.co.vision.model.ContourOptions;
import za.co.vision.model.EdgeDiagnostics;
import za.co.vision.model.ContourDiagnostics;
import za.co.vision.model.ProcessingStage;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Service;
import za.co.vision.config.OpenCvRuntime;
import za.co.vision.config.VisionProperties;
import za.co.vision.model.ContourMeasurement;
import za.co.vision.model.ProcessingParameters;
import za.co.vision.model.ProcessingResult;
import static za.co.vision.service.VisionException.Reason.*;

/** Java-only, bounded, request-local OpenCV pipeline; stores no uploaded or output files. */
@Service
public class VisionProcessingService {
    private record Candidate(int sourceIndex, MatOfPoint contour, ContourMeasurement measurement) { }
    private final VisionProperties properties;
    private final Semaphore processingSlots;

    /**
     * Creates the processing service after native initialization.
     * @param properties validated defaults and processing budgets
     * @param runtime eagerly loaded native runtime, required before allocating matrices
     */
    public VisionProcessingService(VisionProperties properties, OpenCvRuntime runtime) {
        this.properties = properties;
        this.processingSlots = new Semaphore(properties.maxConcurrentProcessing());
    }

    /**
     * Validates an encoded JPEG/PNG, runs Canny and the selected extraction mode, and encodes results.
     * ENHANCED compares guarded Canny trials and can retain long, low-area traces.
     * Small-gap repair uses the existing closing stage only when conservative guards pass.
     * Explicit contour-source experiments are preserved. Geometry is measured once before inclusive
     * filtering, then retained contours receive stable area-sorted labels within this request.
     * Detection and geometry use the original resolution. Display previews are bounded in size;
     * EXIF orientation is ignored consistently for every output image.
     * All matrices are released before return, including on validation or OpenCV failure.
     * @param bytes encoded image bytes, never retained by this service
     * @param parameters validated controls for this request
     * @return JPEG photograph previews, PNG extraction mask, Java-generated display paths and source-pixel geometry
     * @throws VisionException for invalid input, exceeded budgets or an occupied processing pool
     */
    public ProcessingResult process(byte[] bytes, ProcessingParameters parameters) {
        if (bytes == null || bytes.length == 0) {
            throw new VisionException(INVALID_IMAGE, "Choose a non-empty JPEG or PNG image.");
        }
        if (bytes.length > properties.maxUploadBytes()) {
            throw new VisionException(TOO_LARGE, "Image exceeds the configured upload size limit.");
        }
        if (!processingSlots.tryAcquire()) {
            throw new VisionException(BUSY, "Vision Lab is processing other images. Please try again shortly.");
        }
        try {
            return processWithinBudget(bytes, parameters);
        } finally {
            processingSlots.release();
        }
    }

    private ProcessingResult processWithinBudget(byte[] bytes, ProcessingParameters parameters) {
        long started = System.nanoTime();
        int[] dimensions = inspectHeader(bytes);
        try (MatScope scope = new MatScope()) {
            MatOfByte encoded = scope.own(new MatOfByte(bytes));
            Mat original;
            try {
                original = scope.own(Imgcodecs.imdecode(encoded,
                        Imgcodecs.IMREAD_COLOR | Imgcodecs.IMREAD_IGNORE_ORIENTATION));
            } catch (CvException exception) {
                throw new VisionException(INVALID_IMAGE, "The image could not be decoded. Try another JPEG or PNG.");
            }
            if (original.empty() || original.cols() != dimensions[0] || original.rows() != dimensions[1]) {
                throw new VisionException(INVALID_IMAGE, "The image is damaged or has inconsistent dimensions.");
            }
            Mat gray = scope.own(new Mat());
            Mat blurred = scope.own(new Mat());
            Mat hierarchy = scope.own(new Mat());
            Imgproc.cvtColor(original, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, blurred,
                    new Size(parameters.blurKernel(), parameters.blurKernel()), 0);
            var selection = EdgeRefinement.select(scope, blurred, parameters, properties.maxContours());
            Mat edges = selection.edges();
            var stages = ContourBoundaries.stages(scope, blurred, edges, parameters.boundary());
            var repair = EdgeRefinement.repair(edges, stages.closed(), parameters, properties.maxContours(),
                    selection.minimumLength(), selection.minimumSpan());
            Mat contourInput = repair.applied() ? stages.closed() : stages.active();
            String contourSource = repair.applied() ? "closed" : stages.source();
            var edgeDiagnostics = new EdgeDiagnostics(selection.low(), selection.high(), selection.minimumLength(),
                    selection.minimumSpan(), repair.applied(), repair.reason(), selection.trials());
            int boundaryPixelCount = Core.countNonZero(contourInput);
            List<MatOfPoint> contours = new ArrayList<>();
            try {
                // Since OpenCV 3.2 findContours preserves its source; no full-resolution clone is needed.
                Imgproc.findContours(contourInput, contours, hierarchy,
                        parameters.tracing().retrieval() == ContourOptions.Retrieval.TREE ? Imgproc.RETR_TREE : Imgproc.RETR_EXTERNAL,
                        parameters.tracing().chain() == ContourOptions.Chain.NONE ? Imgproc.CHAIN_APPROX_NONE : Imgproc.CHAIN_APPROX_SIMPLE);
            } finally {
                contours.forEach(scope::own);
            }
            if (contours.size() > properties.maxContours()) {
                throw new VisionException(TOO_COMPLEX,
                        "Too many contours. Increase blur or Canny thresholds, or crop the image and retry.");
            }
            // Bound dense chains before converting native points to Java arrays.
            long pointCount = contours.stream().mapToLong(Mat::total).sum();
            if (pointCount > 1_000_000) {
                throw new VisionException(TOO_COMPLEX, "Too many boundary points. Use SIMPLE chains, increase blur or crop the image.");
            }
            List<Candidate> measured = new ArrayList<>();
            for (int i = 0; i < contours.size(); i++) {
                var contour = contours.get(i);
                measured.add(new Candidate(i, contour, ContourGeometry.measure(contour, 0, original.cols(), original.rows())));
            }
            List<Candidate> candidates = measured.stream()
                    .filter(candidate -> parameters.filters().accepts(candidate.measurement(), parameters.minContourArea(),
                            selection.minimumLength(), selection.minimumSpan()))
                    .sorted(Comparator.comparingDouble((Candidate candidate) -> candidate.measurement().area())
                            .reversed().thenComparingInt(candidate -> candidate.measurement().boundingRectangle().y())
                            .thenComparingInt(candidate -> candidate.measurement().boundingRectangle().x()))
                    .toList();
            List<MatOfPoint> retained = candidates.stream().map(Candidate::contour).toList();
            List<ContourMeasurement> measurements = new ArrayList<>();
            List<ContourDiagnostics> diagnostics = new ArrayList<>();
            int approximationPoints = 0;
            for (int i = 0; i < retained.size(); i++) {
                measurements.add(candidates.get(i).measurement().withId(i + 1));
                var diagnostic = diagnose(candidates.get(i), hierarchy, i + 1, parameters.tracing());
                diagnostics.add(diagnostic);
                approximationPoints += diagnostic.approximationVertices();
                if (approximationPoints > 100_000) {
                    throw new VisionException(TOO_COMPLEX, "Too many approximation vertices. Increase epsilon, filter contours or crop the image.");
                }
            }
            var previews = PreviewRenderer.render(original, edges, contourInput, retained, measurements,
                    properties.previewMaxDimension(), properties.previewJpegQuality());
            var stagePreviews = List.of(
                    new ProcessingStage("raw", "Raw Canny", PreviewRenderer.maskPreview(edges, previews.width(), previews.height()), Core.countNonZero(edges)),
                    new ProcessingStage("closed", "Canny after closing · " + parameters.boundary().shape() + " " + parameters.boundary().closeKernel(),
                            PreviewRenderer.maskPreview(stages.closed(), previews.width(), previews.height()), Core.countNonZero(stages.closed())),
                    new ProcessingStage("opened", "Canny after closing + opening · " + parameters.boundary().shape() + " " + parameters.boundary().openKernel(),
                            PreviewRenderer.maskPreview(stages.opened(), previews.width(), previews.height()), Core.countNonZero(stages.opened())));
            return new ProcessingResult(original.cols(), original.rows(), previews.width(), previews.height(),
                    retained.size(), contours.size(),
                    Core.countNonZero(edges), (System.nanoTime() - started) / 1_000_000.0,
                    parameters, previews.original(), previews.edges(), previews.contours(), previews.combined(),
                    measurements, previews.drawings(), boundaryPixelCount, previews.mask(), stagePreviews, contourSource, diagnostics, edgeDiagnostics);
        }
    }

    private static ContourDiagnostics diagnose(Candidate candidate, Mat hierarchy, int id, ContourOptions options) {
        int index = candidate.sourceIndex();
        double[] links = hierarchy.get(0, index);
        int depth = 0;
        for (int parent = (int) links[3]; parent >= 0; parent = (int) hierarchy.get(0, parent)[3]) depth++;
        var measurement = candidate.measurement();
        double boxArea = measurement.rotatedRectangle().width() * measurement.rotatedRectangle().height();
        try (MatScope scope = new MatScope()) {
            MatOfPoint2f source = scope.own(new MatOfPoint2f(candidate.contour().toArray()));
            MatOfPoint2f approximation = scope.own(new MatOfPoint2f());
            double epsilon = options.approximationPercent() * measurement.perimeter() / 100;
            Imgproc.approxPolyDP(source, approximation, epsilon, true);
            StringBuilder path = new StringBuilder();
            for (var point : approximation.toArray()) {
                path.append(path.isEmpty() ? 'M' : 'L').append(Math.round(point.x)).append(' ').append(Math.round(point.y));
            }
            path.append('Z');
            return new ContourDiagnostics(id, index, (int) links[0], (int) links[1], (int) links[2], (int) links[3],
                    depth, depth % 2 != 0, (int) candidate.contour().total(), (int) approximation.total(), epsilon,
                    Imgproc.contourArea(approximation), Imgproc.arcLength(approximation, true), boxArea,
                    boxArea > 0 ? measurement.area() / boxArea : null, path.toString());
        }
    }

    private int[] inspectHeader(byte[] bytes) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new VisionException(INVALID_IMAGE, "Only valid JPEG and PNG images are supported.");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) {
                    throw new VisionException(INVALID_IMAGE, "Only JPEG and PNG images are supported.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1) {
                    throw new VisionException(INVALID_IMAGE, "Image dimensions must be positive.");
                }
                if ((long) width * height > properties.maxPixels()) {
                    throw new VisionException(TOO_LARGE,
                            "Image exceeds the configured " + properties.maxPixels() + " pixel limit. Resize it and retry.");
                }
                return new int[]{width, height};
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new VisionException(INVALID_IMAGE, "The image header is damaged. Try another JPEG or PNG.");
        }
    }

}
