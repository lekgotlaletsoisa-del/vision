package za.co.vision.service;

import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import za.co.vision.config.OpenCvRuntime;
import za.co.vision.config.VisionProperties;
import za.co.vision.model.ProcessingParameters;
import static org.junit.jupiter.api.Assertions.*;

class VisionProcessingServiceTests {
    private static OpenCvRuntime runtime;
    private static VisionProcessingService service;
    private static final ProcessingParameters DEFAULTS = new ProcessingParameters(50, 150, 100, 5);

    @BeforeAll
    static void loadNatives() {
        runtime = new OpenCvRuntime();
        service = new VisionProcessingService(properties(16_000_000, 10_485_760, 2000), runtime);
    }

    private static VisionProperties properties(long pixels, long bytes, int contours) {
        return new VisionProperties(50, 150, 100, 5, bytes, pixels, contours, 2, 1600, 88);
    }

    @Test
    void rectangleHasAnalyticallyKnownGeometry() {
        try (MatScope scope = new MatScope()) {
            MatOfPoint rectangle = scope.own(new MatOfPoint(new Point(20, 30), new Point(140, 30),
                    new Point(140, 110), new Point(20, 110)));
            var geometry = ContourGeometry.measure(rectangle, 1);
            assertEquals(9600, geometry.area(), 1e-6);
            assertEquals(400, geometry.perimeter(), 1e-6);
            assertEquals(80, geometry.centre().x(), 1e-6);
            assertEquals(70, geometry.centre().y(), 1e-6);
            assertEquals(20, geometry.boundingRectangle().x());
            assertEquals(30, geometry.boundingRectangle().y());
            assertEquals(121, geometry.boundingRectangle().width());
            assertEquals(81, geometry.boundingRectangle().height());
            assertEquals(120, geometry.rotatedRectangle().width(), 1e-5);
            assertEquals(80, geometry.rotatedRectangle().height(), 1e-5);
            assertEquals(0, geometry.rotatedRectangle().orientationDegrees(), 1e-5);
            assertFalse(geometry.centroidFallback());
        }
    }

    @Test
    void rectangleSurvivesTheEntireRasterPipeline() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(200, 240, CvType.CV_8UC3));
            Imgproc.rectangle(image, new Point(40, 50), new Point(160, 130), Scalar.all(255), -1);
            var result = service.process(encode(image), DEFAULTS);
            assertEquals(1, result.contourCount());
            assertEquals(240, result.width());
            assertEquals(200, result.height());
            var rectangle = result.contours().getFirst();
            // Canny outlines fall about one pixel around the ideal boundary.
            assertEquals(9600, rectangle.area(), 400);
            assertEquals(400, rectangle.perimeter(), 12);
            assertEquals(100, rectangle.centre().x(), 1);
            assertEquals(90, rectangle.centre().y(), 1);
            assertTrue(result.edgePixelCount() > 300);
            assertTrue(result.processingMillis() > 0);
            for (String dataUrl : new String[]{result.originalImage(), result.edgesImage(),
                    result.contoursImage(), result.overlayImage()}) {
                assertTrue(dataUrl.startsWith("data:image/jpeg;base64,"));
                MatOfByte bytes = scope.own(new MatOfByte(Base64.getDecoder().decode(dataUrl.split(",", 2)[1])));
                Mat decoded = scope.own(Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_UNCHANGED));
                assertEquals(image.size(), decoded.size());
            }
        }
    }

    @Test
    void overlaysKeepThePhotographAndDrawEdgesAndContoursInTheirOwnColours() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(new Mat(200, 240, CvType.CV_8UC3, new Scalar(30, 60, 90)));
            Imgproc.rectangle(image, new Point(40, 50), new Point(180, 150), Scalar.all(230), -1);
            // An internal mark contributes Canny edges but is omitted by RETR_EXTERNAL.
            Imgproc.line(image, new Point(80, 90), new Point(140, 90), Scalar.all(10), 3);
            var result = service.process(encode(image), DEFAULTS);
            assertEquals(1, result.contourCount());
            Mat original = decode(scope, result.originalImage());
            Mat edges = decode(scope, result.edgesImage());
            Mat contours = decode(scope, result.contoursImage());
            Mat combined = decode(scope, result.overlayImage());
            for (Mat output : new Mat[]{original, edges, contours, combined}) {
                assertEquals(3, output.channels());
                assertEquals(image.size(), output.size());
                assertArrayEquals(image.get(0, 0), output.get(0, 0), 3);
                assertArrayEquals(image.get(120, 100), output.get(120, 100), 3);
            }
            assertEquals(0, countColour(scope, original, new Scalar(235, 155, 25)));
            assertTrue(countColour(scope, edges, new Scalar(235, 155, 25)) > 0);
            assertEquals(0, countColour(scope, edges, new Scalar(60, 220, 25)));
            assertTrue(countColour(scope, contours, new Scalar(60, 220, 25)) > 0);
            assertEquals(0, countColour(scope, contours, new Scalar(235, 155, 25)));
            assertTrue(countColour(scope, combined, new Scalar(235, 155, 25)) > 0);
            assertTrue(countColour(scope, combined, new Scalar(60, 220, 25)) > 0);
            var filtered = service.process(encode(image), new ProcessingParameters(50, 150, 100_000, 5));
            assertEquals(filtered.originalImage(), filtered.contoursImage());
            assertEquals(filtered.edgesImage(), filtered.overlayImage());
        }
    }

    @Test
    void phonePhotoHasSmallVisiblePreviewsAndOriginalResolutionGeometry() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(new Mat(3000, 4000, CvType.CV_8UC3));
            org.opencv.core.Core.setRNGSeed(42);
            org.opencv.core.Core.randu(image, 80, 100);
            Imgproc.rectangle(image, new Point(800, 600), new Point(3200, 2400), Scalar.all(235), -1);
            Imgproc.line(image, new Point(1200, 1000), new Point(2800, 1000), Scalar.all(30), 5);
            MatOfByte encodedPhoto = scope.own(new MatOfByte());
            assertTrue(Imgcodecs.imencode(".jpg", image, encodedPhoto));
            assertTrue(encodedPhoto.total() < 10_485_760);
            var result = service.process(encodedPhoto.toArray(), DEFAULTS);
            assertEquals(4000, result.width());
            assertEquals(3000, result.height());
            assertEquals(1600, result.previewWidth());
            assertEquals(1200, result.previewHeight());
            assertEquals(1, result.contourCount());
            assertEquals(4_320_000, result.contours().getFirst().area(), 15_000);
            assertEquals(2000, result.contours().getFirst().centre().x(), 2);
            assertEquals(1500, result.contours().getFirst().centre().y(), 2);
            long imageBytes = 0;
            for (String dataUrl : new String[]{result.originalImage(), result.edgesImage(),
                    result.contoursImage(), result.overlayImage()}) {
                imageBytes += dataUrl.length();
                Mat decoded = decode(scope, dataUrl);
                assertEquals(1600, decoded.cols());
                assertEquals(1200, decoded.rows());
            }
            assertTrue(imageBytes < 8_000_000, "Display results must fit comfortably on a mobile connection.");
            Mat phoneView = scope.own(new Mat());
            Imgproc.resize(decode(scope, result.overlayImage()), phoneView, new org.opencv.core.Size(360, 270));
            assertTrue(countColour(scope, phoneView, new Scalar(235, 155, 25)) > 20,
                    "Edges must remain visible when the image is fitted to a phone.");
            assertTrue(countColour(scope, phoneView, new Scalar(60, 220, 25)) > 100,
                    "Contours must remain visible when the image is fitted to a phone.");
        }
    }

    @Test
    void blankImageHasNoEdgesOrContours() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(120, 160, CvType.CV_8UC3));
            var result = service.process(encode(image), DEFAULTS);
            assertEquals(0, result.contourCount());
            assertEquals(0, result.detectedContourCount());
            assertEquals(0, result.edgePixelCount());
            assertTrue(result.contours().isEmpty());
        }
    }

    @Test
    void separateObjectsAndAreaFilterProduceExpectedCounts() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(240, 360, CvType.CV_8UC3));
            Imgproc.rectangle(image, new Point(20, 30), new Point(150, 170), Scalar.all(255), -1);
            Imgproc.rectangle(image, new Point(240, 50), new Point(260, 70), Scalar.all(255), -1);
            byte[] bytes = encode(image);
            var all = service.process(bytes, new ProcessingParameters(50, 150, 0, 3));
            assertEquals(2, all.contourCount());
            assertTrue(all.contours().get(0).area() > all.contours().get(1).area());
            assertEquals(1, all.contours().get(0).id());
            assertEquals(2, all.contours().get(1).id());
            var filtered = service.process(bytes, new ProcessingParameters(50, 150, 1000, 3));
            assertEquals(2, filtered.detectedContourCount());
            assertEquals(1, filtered.contourCount());
            assertEquals(0, service.process(bytes, new ProcessingParameters(50, 150, 100_000, 3)).contourCount());
        }
    }

    @Test
    void higherThresholdsRemoveLowContrastEdges() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(180, 220, CvType.CV_8UC3));
            Imgproc.rectangle(image, new Point(40, 40), new Point(160, 140), Scalar.all(30), -1);
            byte[] bytes = encode(image);
            var sensitive = service.process(bytes, new ProcessingParameters(5, 15, 0, 5));
            var strict = service.process(bytes, new ProcessingParameters(200, 250, 0, 5));
            assertTrue(sensitive.edgePixelCount() > 0);
            assertEquals(1, sensitive.contourCount());
            assertEquals(0, strict.edgePixelCount());
            assertEquals(0, strict.contourCount());
        }
    }

    @Test
    void emptyMalformedAndUnsupportedUploadsAreRejected() {
        assertEquals(VisionException.Reason.INVALID_IMAGE,
                assertThrows(VisionException.class, () -> service.process(new byte[0], DEFAULTS)).reason());
        assertThrows(VisionException.class, () -> service.process(null, DEFAULTS));
        assertThrows(VisionException.class, () -> service.process("invalid image".getBytes(), DEFAULTS));
        // A GIF signature must not bypass JPEG/PNG validation.
        byte[] gif = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
        assertThrows(VisionException.class, () -> service.process(gif, DEFAULTS));
    }

    @Test
    void encodedAndDecodedSizeBudgetsAreEnforced() {
        var smallBudget = new VisionProcessingService(properties(100, 10_485_760, 2000), runtime);
        var tinyUpload = new VisionProcessingService(properties(1000, 2, 2000), runtime);
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(20, 20, CvType.CV_8UC3));
            byte[] bytes = encode(image);
            assertEquals(VisionException.Reason.TOO_LARGE,
                    assertThrows(VisionException.class, () -> smallBudget.process(bytes, DEFAULTS)).reason());
            assertEquals(VisionException.Reason.TOO_LARGE,
                    assertThrows(VisionException.class, () -> tinyUpload.process(bytes, DEFAULTS)).reason());
        }
    }

    @Test
    void excessiveContourCountIsRejectedAndSlotIsReleased() {
        var limited = new VisionProcessingService(properties(100_000, 10_485_760, 1), runtime);
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(100, 100, CvType.CV_8UC3));
            Imgproc.rectangle(image, new Point(10, 10), new Point(30, 30), Scalar.all(255), -1);
            Imgproc.rectangle(image, new Point(60, 60), new Point(80, 80), Scalar.all(255), -1);
            assertEquals(VisionException.Reason.TOO_COMPLEX,
                    assertThrows(VisionException.class, () -> limited.process(encode(image), DEFAULTS)).reason());
            image.setTo(Scalar.all(0));
            assertEquals(0, limited.process(encode(image), DEFAULTS).contourCount());
        }
    }

    @Test
    void jpegIsAcceptedAndPreservesDimensions() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(new Mat(80, 120, CvType.CV_8UC3, Scalar.all(230)));
            MatOfByte bytes = scope.own(new MatOfByte());
            assertTrue(Imgcodecs.imencode(".jpg", image, bytes));
            var result = service.process(bytes.toArray(), DEFAULTS);
            assertEquals(120, result.width());
            assertEquals(80, result.height());
        }
    }

    @Test
    void degenerateShapesHaveFiniteSafeGeometry() {
        try (MatScope scope = new MatScope()) {
            MatOfPoint line = scope.own(new MatOfPoint(new Point(10, 10), new Point(50, 10)));
            var lineGeometry = ContourGeometry.measure(line, 1);
            assertEquals(0, lineGeometry.area());
            assertTrue(lineGeometry.centroidFallback());
            assertEquals(30, lineGeometry.centre().x(), 1e-6);
            assertEquals(0, lineGeometry.rotatedRectangle().orientationDegrees(), 1e-6);
            MatOfPoint point = scope.own(new MatOfPoint(new Point(10, 20)));
            var pointGeometry = ContourGeometry.measure(point, 2);
            assertTrue(pointGeometry.centroidFallback());
            assertNull(pointGeometry.rotatedRectangle().orientationDegrees());
            assertEquals(0, pointGeometry.perimeter());
        }
    }

    @Test
    void rotatedAndTallRectanglesFollowLongAxisConvention() {
        try (MatScope scope = new MatScope()) {
            for (double angle : new double[]{0, 30, 90, 135}) {
                Point[] corners = new Point[4];
                var rotated = new org.opencv.core.RotatedRect(new Point(200, 200),
                        new org.opencv.core.Size(100, 40), angle);
                rotated.points(corners);
                MatOfPoint contour = scope.own(new MatOfPoint(corners));
                var box = ContourGeometry.measure(contour, 1).rotatedRectangle();
                assertTrue(box.width() >= box.height());
                assertEquals(angle, box.orientationDegrees(), 1.0);
                assertEquals(100, box.width(), 2.0);
                assertEquals(40, box.height(), 2.0);
            }
        }
    }

    @Test
    void allUserControlsRejectInvalidAndNonFiniteValues() {
        for (double bad : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new ProcessingParameters(bad, 150, 100, 5));
            assertThrows(IllegalArgumentException.class, () -> new ProcessingParameters(50, 150, bad, 5));
        }
        assertThrows(IllegalArgumentException.class, () -> new ProcessingParameters(150, 50, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new ProcessingParameters(50, 256, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new ProcessingParameters(50, 150, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new ProcessingParameters(50, 150, 0, 33));
        assertThrows(IllegalArgumentException.class, () -> new ProcessingParameters(50, 150, 0, 0));
        assertDoesNotThrow(() -> new ProcessingParameters(0, 255, 0, 1));
    }

    private static Mat decode(MatScope scope, String dataUrl) {
        MatOfByte bytes = scope.own(new MatOfByte(Base64.getDecoder().decode(dataUrl.split(",", 2)[1])));
        return scope.own(Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_UNCHANGED));
    }

    private static int countColour(MatScope scope, Mat image, Scalar colour) {
        Mat mask = scope.own(new Mat());
        Scalar lower = new Scalar(colour.val[0] - 45, colour.val[1] - 45, colour.val[2] - 45);
        Scalar upper = new Scalar(colour.val[0] + 45, colour.val[1] + 45, colour.val[2] + 45);
        org.opencv.core.Core.inRange(image, lower, upper, mask);
        return org.opencv.core.Core.countNonZero(mask);
    }

    private static byte[] encode(Mat image) {
        try (MatScope scope = new MatScope()) {
            MatOfByte bytes = scope.own(new MatOfByte());
            assertTrue(Imgcodecs.imencode(".png", image, bytes));
            return bytes.toArray();
        }
    }
}
