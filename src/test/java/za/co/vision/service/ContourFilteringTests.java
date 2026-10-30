package za.co.vision.service;

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
import za.co.vision.model.ContourFilters;
import za.co.vision.model.ProcessingParameters;
import static org.junit.jupiter.api.Assertions.*;

class ContourFilteringTests {
    private static VisionProcessingService service;
    @BeforeAll
    static void load() {
        service = new VisionProcessingService(new VisionProperties(50, 150, 100, 5,
                10_485_760, 16_000_000, 2000, 2, 1600, 88), new OpenCvRuntime());
    }

    @Test
    void multipleShapesAndStraightBackgroundLinesAreFilteredOnlyAsConfigured() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(600, 900, CvType.CV_8UC3));
            Imgproc.line(image, new Point(0, 40), new Point(899, 40), Scalar.all(255), 2);
            Imgproc.rectangle(image, new Point(100, 150), new Point(260, 320), Scalar.all(255), -1);
            Imgproc.circle(image, new Point(550, 230), 75, Scalar.all(255), -1);
            Imgproc.rectangle(image, new Point(350, 420), new Point(650, 428), Scalar.all(255), -1);
            MatOfByte encoded = scope.own(new MatOfByte());
            assertTrue(Imgcodecs.imencode(".png", image, encoded));
            byte[] bytes = encoded.toArray();
            var raw = service.process(bytes, new ProcessingParameters(50, 150, 0, 3));
            assertTrue(raw.detectedContourCount() >= 4);
            assertEquals(raw.detectedContourCount(), raw.contourCount());
            assertTrue(raw.contours().stream().anyMatch(item -> item.touchesImageBoundary()));
            var noBorder = service.process(bytes, parameters(0, new ContourFilters(null, null, null, 0, 0, true)));
            assertEquals(raw.detectedContourCount(), noBorder.detectedContourCount());
            assertTrue(noBorder.contourCount() < raw.contourCount());
            assertTrue(noBorder.contours().stream().noneMatch(item -> item.touchesImageBoundary()));
            var compact = service.process(bytes, parameters(100, new ContourFilters(null, null, 4.0, 0, 0, false)));
            assertEquals(2, compact.contourCount());
            var round = service.process(bytes, parameters(100, new ContourFilters(null, null, null, .85, 0, false)));
            assertEquals(1, round.contourCount());
            assertTrue(round.contours().getFirst().circularity() >= .85);
            var narrow = service.process(bytes, parameters(100, new ContourFilters(null, 10.0, null, 0, 0, false)));
            assertTrue(narrow.contourCount() >= 1);
            assertTrue(narrow.contours().stream().allMatch(item -> item.aspectRatio() >= 10));
            var small = service.process(bytes, parameters(100, new ContourFilters(5000.0, null, null, 0, 0, false)));
            assertTrue(small.contourCount() >= 1);
            assertTrue(small.contours().stream().allMatch(item -> item.area() <= 5000));
            for (var result : new za.co.vision.model.ProcessingResult[]{compact, round, narrow, small}) {
                assertEquals(raw.detectedContourCount(), result.detectedContourCount());
            }
        }
    }

    @Test
    void inclusiveBoundsAndDegenerateRatiosHaveExplicitPolicy() {
        try (MatScope scope = new MatScope()) {
            var rectangle = scope.own(new MatOfPoint(new Point(0, 0), new Point(100, 0), new Point(100, 50), new Point(0, 50)));
            var measured = ContourGeometry.measure(rectangle, 1, 200, 200);
            var exact = new ContourFilters(5000.0, 2.0, 2.0, measured.circularity(), 1, false);
            assertTrue(exact.accepts(measured, 5000));
            assertFalse(new ContourFilters(null, null, null, 0, 0, true).accepts(measured, 0));
            assertFalse(new ContourFilters(4999.0, null, null, 0, 0, false).accepts(measured, 0));
            assertFalse(new ContourFilters(null, null, null, .9, 0, false).accepts(measured, 0));
            var point = scope.own(new MatOfPoint(new Point(20, 20)));
            var degenerate = ContourGeometry.measure(point, 1, 200, 200);
            assertTrue(ContourFilters.none().accepts(degenerate, 0));
            assertFalse(new ContourFilters(null, 1.0, null, 0, 0, false).accepts(degenerate, 0));
            assertFalse(new ContourFilters(null, null, 10.0, 0, 0, false).accepts(degenerate, 0));
        }
    }

    @Test
    void solidityDistinguishesConcavityButDoesNotRecognizeBackground() {
        try (MatScope scope = new MatScope()) {
            var concave = scope.own(new MatOfPoint(new Point(10, 10), new Point(90, 10), new Point(90, 30),
                    new Point(30, 30), new Point(30, 90), new Point(10, 90)));
            var narrowLineRegion = scope.own(new MatOfPoint(new Point(10, 10), new Point(190, 10),
                    new Point(190, 14), new Point(10, 14)));
            var solidOnly = new ContourFilters(null, null, null, 0, .9, false);
            assertFalse(solidOnly.accepts(ContourGeometry.measure(concave, 1), 0));
            assertTrue(solidOnly.accepts(ContourGeometry.measure(narrowLineRegion, 1), 0),
                    "A convex grout-like region can legitimately pass solidity filtering.");
        }
    }

    @Test
    void invalidBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ContourFilters(Double.NaN, null, null, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new ContourFilters(null, .5, null, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new ContourFilters(null, 5.0, 2.0, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new ContourFilters(null, null, Double.POSITIVE_INFINITY, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new ContourFilters(null, null, null, 1.1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new ContourFilters(null, null, null, 0, -1, false));
        assertThrows(IllegalArgumentException.class, () -> parameters(100, new ContourFilters(50.0, null, null, 0, 0, false)));
    }

    private static ProcessingParameters parameters(double area, ContourFilters filters) {
        return new ProcessingParameters(50, 150, area, 3, filters);
    }
}
