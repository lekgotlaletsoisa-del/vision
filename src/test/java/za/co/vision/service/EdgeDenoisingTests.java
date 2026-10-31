package za.co.vision.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import za.co.vision.config.OpenCvRuntime;
import za.co.vision.config.VisionProperties;
import za.co.vision.model.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reference scenes compare Gaussian and bilateral results using known boundaries, not contour counts alone. */
class EdgeDenoisingTests {
    private static VisionProcessingService service;

    @BeforeAll
    static void load() {
        service = new VisionProcessingService(new VisionProperties(50, 150, 100, 5,
                10_485_760, 16_000_000, 2000, 2, 1600, 88), new OpenCvRuntime());
    }

    @Test
    void fragmentedSurfaceTextureIsReducedWithoutLosingTheRectangle() {
        try (MatScope scope = new MatScope()) {
            Mat image = texturedScene(scope);
            var reference = service.process(encode(scope, image), parameters(DetectionMode.ENHANCED, 3));
            var improved = service.process(encode(scope, image), parameters(DetectionMode.DENOISED, 3));
            var diagnostics = improved.edgeDiagnostics().denoising();
            assertEquals("BILATERAL", diagnostics.filter(), diagnostics.toString());
            assertTrue(diagnostics.candidateShortLength() <= diagnostics.referenceShortLength() * .85);
            assertTrue(diagnostics.boundarySupport() >= .98);
            assertTrue(improved.edgePixelCount() < reference.edgePixelCount());
            var rectangle = improved.contours().stream().filter(c -> c.area() > 40000).findFirst().orElseThrow();
            assertEquals(200, rectangle.boundingRectangle().width(), 3);
            assertEquals(240, rectangle.boundingRectangle().height(), 3);
            assertEquals(1, improved.contours().stream().filter(c -> c.area() > 40000).count());
            assertEquals(600, improved.width()); assertEquals(400, improved.height());
            assertEquals("BILATERAL", improved.edgeDiagnostics().trials().getLast().smoothing());
        }
    }

    @Test
    void defaultCameraSettingsReduceFineTextureWhileKeepingTheKnownBoundary() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(new Mat(300, 500, CvType.CV_8UC3, Scalar.all(100)));
            Imgproc.rectangle(image, new Point(30, 40), new Point(230, 260), Scalar.all(230), -1);
            for (int y = 20; y < 280; y += 30) for (int x = 290; x < 470; x += 30) {
                Imgproc.rectangle(image, new Point(x, y), new Point(x + 2, y + 2), Scalar.all(180), -1);
            }
            byte[] bytes = encode(scope, image);
            var boundary = new BoundaryOptions(BoundaryOptions.Mode.CANNY, 1, 1, null);
            var reference = service.process(bytes, new ProcessingParameters(50, 150, 100, 5, null, boundary, null, DetectionMode.ENHANCED));
            var result = service.process(bytes, new ProcessingParameters(50, 150, 100, 5, null, boundary, null, DetectionMode.DENOISED));
            var diagnostics = result.edgeDiagnostics().denoising();
            assertEquals("BILATERAL", diagnostics.filter());
            assertTrue(diagnostics.candidateShortLength() < diagnostics.referenceShortLength() * .65);
            assertTrue(diagnostics.boundarySupport() >= .98);
            assertTrue(result.edgePixelCount() < reference.edgePixelCount());
            assertEquals(1, result.contourCount());
            assertEquals(200, result.contours().getFirst().boundingRectangle().width(), 3);
            assertEquals(220, result.contours().getFirst().boundingRectangle().height(), 3);
        }
    }

    @Test
    void cleanAndBlankScenesKeepTheExistingResultInsteadOfAddingWorkWithoutBenefit() {
        try (MatScope scope = new MatScope()) {
            for (boolean blank : new boolean[]{false, true}) {
                Mat image = scope.own(Mat.zeros(240, 320, CvType.CV_8UC3));
                if (!blank) Imgproc.rectangle(image, new Point(40, 40), new Point(260, 180), Scalar.all(220), -1);
                byte[] bytes = encode(scope, image);
                var reference = service.process(bytes, parameters(DetectionMode.ENHANCED, 5));
                var improved = service.process(bytes, parameters(DetectionMode.DENOISED, 5));
                assertEquals("GAUSSIAN", improved.edgeDiagnostics().denoising().filter());
                assertEquals(reference.edgesImage(), improved.edgesImage());
                assertEquals(reference.contours(), improved.contours());
                assertNull(improved.edgeDiagnostics().denoising().candidateShortLength());
            }
        }
    }

    @Test
    void smallClosedRegionsAreProtectedEvenWhenTheirPerimeterIsBelowTheLongTraceMinimum() {
        try (MatScope scope = new MatScope()) {
            Mat image = texturedScene(scope, 1600);
            Imgproc.circle(image, new Point(900, 200), 7, Scalar.all(240), -1);
            var improved = service.process(encode(scope, image), parameters(DetectionMode.DENOISED, 3));
            assertTrue(improved.contours().stream().anyMatch(c -> c.area() >= 100 && c.area() < 300
                    && Math.abs(c.centre().x() - 900) < 2 && Math.abs(c.centre().y() - 200) < 2
                    && c.perimeter() < improved.edgeDiagnostics().minimumTraceLength()));
        }
    }

    @Test
    void blurOneAndExplicitSegmentationDoNotSilentlyEnableBilateralSmoothing() {
        try (MatScope scope = new MatScope()) {
            Mat image = texturedScene(scope);
            var noBlur = service.process(encode(scope, image), parameters(DetectionMode.DENOISED, 1));
            assertEquals("GAUSSIAN", noBlur.edgeDiagnostics().denoising().filter());
            assertEquals(0, noBlur.edgeDiagnostics().denoising().diameter());
            var segmentation = new ProcessingParameters(20, 60, 100, 3, null,
                    new BoundaryOptions(BoundaryOptions.Mode.SEGMENTATION, 1, 1, null), null, DetectionMode.DENOISED);
            var regions = service.process(encode(scope, image), segmentation);
            assertEquals("segmentation", regions.contourSource());
            assertEquals(0, regions.edgeDiagnostics().denoising().diameter());
            assertEquals("GAUSSIAN", regions.edgeDiagnostics().denoising().filter());
        }
    }

    @Test
    void lostProtectedBoundaryCausesExactGaussianFallback() {
        try (MatScope scope = new MatScope()) {
            Mat gray = scope.own(new Mat(400, 600, CvType.CV_8UC1, Scalar.all(100)));
            Imgproc.rectangle(gray, new Point(40, 80), new Point(240, 320), Scalar.all(200), -1);
            // A reference edge absent from the candidate photograph must never be accepted as preserved.
            Mat referenceEdges = scope.own(Mat.zeros(400, 600, CvType.CV_8UC1));
            Imgproc.rectangle(referenceEdges, new Point(40, 80), new Point(240, 320), Scalar.all(255), 1);
            Imgproc.rectangle(referenceEdges, new Point(320, 80), new Point(480, 320), Scalar.all(255), 1);
            Imgproc.rectangle(referenceEdges, new Point(520, 10), new Point(524, 14), Scalar.all(255), 1);
            var reference = new EdgeRefinement.Selection(referenceEdges, 20, 60, 40, 20, java.util.List.of());
            var result = EdgeRefinement.denoise(scope, gray, reference, parameters(DetectionMode.DENOISED, 3), 2000);
            assertSame(referenceEdges, result.selection().edges());
            assertEquals("GAUSSIAN", result.diagnostics().filter());
            assertTrue(result.diagnostics().boundarySupport() < .98);
        }
    }

    @Test
    void weakBoundaryRemainsAlongsideTheStrongOne() {
        try (MatScope scope = new MatScope()) {
            Mat image = texturedScene(scope);
            Imgproc.rectangle(image, new Point(300, 70), new Point(500, 300), Scalar.all(125), -1);
            var result = service.process(encode(scope, image), parameters(DetectionMode.DENOISED, 3));
            assertTrue(result.contours().stream().anyMatch(c -> c.area() > 40000 && c.boundingRectangle().x() < 100));
            assertTrue(result.contours().stream().anyMatch(c -> c.area() > 40000 && c.boundingRectangle().x() > 250));
        }
    }

    @Test
    void nearbyRectanglesAndAnIndependentLineStaySeparate() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(new Mat(300, 500, CvType.CV_8UC3, Scalar.all(100)));
            Imgproc.rectangle(image, new Point(20, 60), new Point(110, 230), Scalar.all(230), -1);
            Imgproc.rectangle(image, new Point(117, 60), new Point(207, 230), Scalar.all(230), -1);
            Imgproc.line(image, new Point(240, 30), new Point(240, 270), Scalar.all(230), 2);
            for (int x = 320; x < 470; x += 14) for (int y = 30; y < 270; y += 14) {
                Imgproc.rectangle(image, new Point(x, y), new Point(x + 3, y + 3), Scalar.all(125), -1);
            }
            var result = service.process(encode(scope, image), parameters(DetectionMode.DENOISED, 3));
            var rectangles = result.contours().stream().filter(c -> c.area() > 10000).toList();
            assertEquals(2, rectangles.size());
            assertTrue(rectangles.stream().allMatch(c -> c.boundingRectangle().width() < 100));
            assertTrue(result.contours().stream().anyMatch(c -> c.boundingRectangle().x() > 230
                    && c.boundingRectangle().x() < 245 && c.boundingRectangle().height() > 230));
        }
    }

    @Test
    void zeroMinimumAreaProtectsAllReferenceTracesAndRefusesTextureRemoval() {
        try (MatScope scope = new MatScope()) {
            Mat image = texturedScene(scope);
            byte[] bytes = encode(scope, image);
            var referenceParameters = new ProcessingParameters(20, 60, 0, 3, null,
                    new BoundaryOptions(BoundaryOptions.Mode.CANNY, 1, 1, null), null, DetectionMode.ENHANCED);
            var requested = new ProcessingParameters(20, 60, 0, 3, null, referenceParameters.boundary(), null, DetectionMode.DENOISED);
            var reference = service.process(bytes, referenceParameters);
            var candidate = service.process(bytes, requested);
            assertEquals("GAUSSIAN", candidate.edgeDiagnostics().denoising().filter());
            assertEquals(reference.edgesImage(), candidate.edgesImage());
            assertEquals(reference.contours(), candidate.contours());
        }
    }

    private static Mat texturedScene(MatScope scope) {
        return texturedScene(scope, 600);
    }

    private static Mat texturedScene(MatScope scope, int width) {
        Mat image = scope.own(new Mat(400, width, CvType.CV_8UC3, Scalar.all(100)));
        Imgproc.rectangle(image, new Point(40, 80), new Point(240, 320), Scalar.all(200), -1);
        for (int y = 20; y < 380; y += 14) for (int x = 300; x < 580; x += 14) {
            Imgproc.rectangle(image, new Point(x, y), new Point(x + 3, y + 3), Scalar.all(125), -1);
        }
        return image;
    }

    private static ProcessingParameters parameters(DetectionMode mode, int blur) {
        return new ProcessingParameters(20, 60, 100, blur, null,
                new BoundaryOptions(BoundaryOptions.Mode.CANNY, 1, 1, null), null, mode);
    }

    private static byte[] encode(MatScope scope, Mat image) {
        MatOfByte bytes = scope.own(new MatOfByte());
        assertTrue(Imgcodecs.imencode(".png", image, bytes));
        return bytes.toArray();
    }
}
