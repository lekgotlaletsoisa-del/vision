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
     * Validates an encoded JPEG/PNG, runs Canny and external-contour extraction, and encodes results.
     * Detection and geometry use the original resolution. Display previews are bounded in size;
     * EXIF orientation is ignored consistently for every output image.
     * All matrices are released before return, including on validation or OpenCV failure.
     * @param bytes encoded image bytes, never retained by this service
     * @param parameters validated controls for this request
     * @return JPEG preview data URLs and immutable geometry in original-image pixels
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
            Mat edges = scope.own(new Mat());
            Mat hierarchy = scope.own(new Mat());
            Imgproc.cvtColor(original, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, blurred,
                    new Size(parameters.blurKernel(), parameters.blurKernel()), 0);
            Imgproc.Canny(blurred, edges, parameters.cannyLow(), parameters.cannyHigh(), 3, true);
            Mat contourInput = scope.own(edges.clone());
            List<MatOfPoint> contours = new ArrayList<>();
            try {
                // External-only retrieval avoids counting both sides of a closed Canny outline.
                Imgproc.findContours(contourInput, contours, hierarchy,
                        Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            } finally {
                contours.forEach(scope::own);
            }
            if (contours.size() > properties.maxContours()) {
                throw new VisionException(TOO_COMPLEX,
                        "Too many contours. Increase blur or Canny thresholds, or crop the image and retry.");
            }
            List<MatOfPoint> retained = contours.stream()
                    .filter(contour -> Imgproc.contourArea(contour) >= parameters.minContourArea())
                    .sorted(Comparator.comparingDouble((MatOfPoint contour) -> Imgproc.contourArea(contour))
                            .reversed().thenComparingInt(contour -> Imgproc.boundingRect(contour).y)
                            .thenComparingInt(contour -> Imgproc.boundingRect(contour).x))
                    .toList();
            List<ContourMeasurement> measurements = new ArrayList<>();
            for (int i = 0; i < retained.size(); i++) {
                ContourMeasurement measurement = ContourGeometry.measure(retained.get(i), i + 1);
                measurements.add(measurement);
            }
            var previews = PreviewRenderer.render(original, edges, retained, measurements,
                    properties.previewMaxDimension(), properties.previewJpegQuality());
            return new ProcessingResult(original.cols(), original.rows(), previews.width(), previews.height(),
                    retained.size(), contours.size(),
                    Core.countNonZero(edges), (System.nanoTime() - started) / 1_000_000.0,
                    parameters, previews.original(), previews.edges(), previews.contours(), previews.combined(), measurements);
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
