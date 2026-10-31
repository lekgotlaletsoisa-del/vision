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

/** Native fixtures test continuity gains, noise refusal and conservative repair failures. */
class EdgeRefinementTests {
    private static VisionProcessingService service;

    @BeforeAll
    static void load() {
        service = new VisionProcessingService(new VisionProperties(50, 150, 100, 5,
                10_485_760, 16_000_000, 2000, 2, 1600, 88), new OpenCvRuntime());
    }

    @Test
    void longZeroAreaTraceQualifiesWithoutAdmittingSmallNoiseOrBypassingOtherFilters() {
        try (MatScope scope = new MatScope()) {
            MatOfPoint line = scope.own(new MatOfPoint(new Point(0, 40), new Point(240, 40)));
            MatOfPoint noise = scope.own(new MatOfPoint(new Point(10, 10), new Point(14, 10)));
            var measured = ContourGeometry.measure(line, 1, 300, 200);
            assertEquals(0, measured.area());
            assertFalse(ContourFilters.none().accepts(measured, 100));
            assertTrue(ContourFilters.none().accepts(measured, 100, 40, 20));
            assertFalse(ContourFilters.none().accepts(ContourGeometry.measure(noise, 2), 100, 40, 20));
            assertFalse(new ContourFilters(null, null, null, .1, 0, false).accepts(measured, 100, 40, 20));
            assertFalse(new ContourFilters(null, null, null, 0, 0, true).accepts(measured, 100, 40, 20));
            // A high-perimeter scribble in a tiny region must not qualify on length alone.
            var scribble = scope.own(new MatOfPoint(new Point(10, 10), new Point(19, 19), new Point(10, 19),
                    new Point(19, 10), new Point(10, 10), new Point(19, 19)));
            assertFalse(ContourFilters.none().accepts(ContourGeometry.measure(scribble, 3), 100, 40, 20));
        }
    }

    @Test
    void croppedPanelBoundarySurvivesEvenThoughItDoesNotEncloseThePanel() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(360, 480, CvType.CV_8UC3));
            Imgproc.rectangle(image, new Point(0, 0), new Point(280, 300), Scalar.all(190), -1);
            byte[] bytes = encode(scope, image);
            var standard = service.process(bytes, parameters(DetectionMode.STANDARD));
            var improved = service.process(bytes, parameters(DetectionMode.ENHANCED));
            assertEquals(0, standard.contourCount());
            assertEquals(1, improved.contourCount());
            assertTrue(improved.contours().getFirst().area() < 100);
            assertTrue(improved.contours().getFirst().perimeter() > 1000);
            assertTrue(improved.contours().getFirst().touchesImageBoundary());
            assertEquals(480, improved.width()); assertEquals(360, improved.height());
            assertEquals(improved.contourCount(), improved.contourDrawings().size());
        }
    }

    @Test
    void lowerTrialRecoversFaintRectangleWithoutLosingTheStrongRectangle() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(300, 500, CvType.CV_8UC3));
            Imgproc.rectangle(image, new Point(30, 40), new Point(180, 240), Scalar.all(255), -1);
            Imgproc.rectangle(image, new Point(280, 40), new Point(450, 240), Scalar.all(32), -1);
            byte[] bytes = encode(scope, image);
            var standard = service.process(bytes, parameters(DetectionMode.STANDARD));
            var improved = service.process(bytes, parameters(DetectionMode.ENHANCED));
            assertEquals(1, standard.contourCount());
            assertEquals(2, improved.contourCount());
            assertTrue(improved.edgeDiagnostics().cannyHigh() < 150);
            assertEquals(4, improved.edgeDiagnostics().trials().size());
            assertEquals(150, improved.parameters().cannyHigh(), "Keep the requested baseline for reproducibility.");
            assertTrue(improved.contours().stream().anyMatch(c -> c.boundingRectangle().x() < 200));
            assertTrue(improved.contours().stream().anyMatch(c -> c.boundingRectangle().x() > 200));
        }
    }

    @Test
    void sensitiveTrialWithManyShortSpecksIsRefused() {
        try (MatScope scope = new MatScope()) {
            Mat gray = scope.own(Mat.zeros(320, 480, CvType.CV_8UC1));
            Imgproc.rectangle(gray, new Point(30, 40), new Point(160, 260), Scalar.all(255), -1);
            for (int y = 10; y < 310; y += 12) for (int x = 210; x < 470; x += 12) gray.put(y, x, 40);
            var noBlur = new ProcessingParameters(50, 150, 100, 1, null, null, null, DetectionMode.ENHANCED);
            var selection = EdgeRefinement.select(scope, gray, noBlur, 2000);
            assertEquals(150, selection.high(), "More noise is not a sensitivity improvement.");
            var sensitive = selection.trials().stream().filter(t -> t.high() == 75).findFirst().orElseThrow();
            assertTrue(sensitive.shortLength() > 0);
            assertFalse(sensitive.eligible());
        }
    }

    @Test
    void guardedThreePixelClosingSealsSmallGapButLeavesRawUntouched() {
        try (MatScope scope = new MatScope()) {
            Mat mask = scope.own(Mat.zeros(120, 160, CvType.CV_8UC1));
            Imgproc.rectangle(mask, new Point(30, 30), new Point(130, 90), Scalar.all(255), 1);
            mask.put(30, 76, 0);
            var parameters = parameters(DetectionMode.ENHANCED);
            var stages = ContourBoundaries.stages(scope, mask, mask, parameters.boundary());
            var repair = EdgeRefinement.repair(mask, stages.closed(), parameters, 2000, 40, 20);
            assertTrue(repair.applied(), repair.reason());
            assertEquals(255, stages.closed().get(30, 76)[0]);
            assertEquals(0, mask.get(30, 76)[0]);
        }
    }

    @Test
    void repairRefusesNearbyShapesAndRectangleBesideStraightLine() {
        try (MatScope scope = new MatScope()) {
            for (boolean useLine : new boolean[]{false, true}) {
                Mat mask = scope.own(Mat.zeros(140, 180, CvType.CV_8UC1));
                Imgproc.rectangle(mask, new Point(30, 30), new Point(80, 100), Scalar.all(255), -1);
                if (useLine) Imgproc.line(mask, new Point(82, 10), new Point(82, 125), Scalar.all(255), 1);
                else Imgproc.rectangle(mask, new Point(82, 30), new Point(130, 100), Scalar.all(255), -1);
                var parameters = parameters(DetectionMode.ENHANCED);
                var stages = ContourBoundaries.stages(scope, mask, mask, parameters.boundary());
                var repair = EdgeRefinement.repair(mask, stages.closed(), parameters, 2000, 40, 20);
                assertFalse(repair.applied());
                assertTrue(repair.reason().contains("count changed"));
            }
        }
    }

    @Test
    void repairRefusesLargeKernelAndOpeningWhileHonoringExplicitSources() {
        try (MatScope scope = new MatScope()) {
            Mat mask = scope.own(Mat.zeros(100, 160, CvType.CV_8UC1));
            Imgproc.rectangle(mask, new Point(20, 20), new Point(70, 80), Scalar.all(255), 1);
            for (var boundary : new BoundaryOptions[]{
                    new BoundaryOptions(BoundaryOptions.Mode.CANNY, 1, 1, null),
                    new BoundaryOptions(BoundaryOptions.Mode.CANNY, 9, 1, null),
                    new BoundaryOptions(BoundaryOptions.Mode.CANNY, 3, 3, null),
                    new BoundaryOptions(BoundaryOptions.Mode.CANNY_CLOSED, 3, 1, null)}) {
                var parameters = new ProcessingParameters(50, 150, 100, 5, null, boundary, null, DetectionMode.ENHANCED);
                var stages = ContourBoundaries.stages(scope, mask, mask, boundary);
                assertFalse(EdgeRefinement.repair(mask, stages.closed(), parameters, 2000, 40, 20).applied());
            }
        }
    }

    @Test
    void completeRotatedElongatedRectangleKeepsSourceGeometryAndBlankIsValid() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(260, 360, CvType.CV_8UC3));
            var rectangle = new RotatedRect(new Point(180, 130), new Size(220, 35), 28);
            Point[] vertices = new Point[4]; rectangle.points(vertices);
            Imgproc.fillConvexPoly(image, scope.own(new MatOfPoint(vertices)), Scalar.all(255));
            var result = service.process(encode(scope, image), parameters(DetectionMode.ENHANCED));
            assertEquals(1, result.contourCount());
            assertTrue(result.contours().getFirst().aspectRatio() > 5);
            assertEquals(28, result.contours().getFirst().rotatedRectangle().orientationDegrees(), 1);
            Mat blank = scope.own(Mat.zeros(260, 360, CvType.CV_8UC3));
            var empty = service.process(encode(scope, blank), parameters(DetectionMode.ENHANCED));
            assertEquals(0, empty.edgePixelCount()); assertEquals(0, empty.contourCount());
            assertEquals(150, empty.edgeDiagnostics().cannyHigh());
            assertFalse(empty.edgeDiagnostics().closingApplied());
        }
    }

    @Test
    void repairRefusesLargePixelChangesEvenWhenExternalCountIsUnchanged() {
        try (MatScope scope = new MatScope()) {
            Mat mask = scope.own(Mat.zeros(100, 100, CvType.CV_8UC1));
            Imgproc.rectangle(mask, new Point(10, 10), new Point(90, 90), Scalar.all(255), -1);
            for (int y = 15; y < 85; y += 3) for (int x = 15; x < 85; x += 3) {
                Imgproc.rectangle(mask, new Point(x, y), new Point(x + 1, y + 1), Scalar.all(0), -1);
            }
            var parameters = parameters(DetectionMode.ENHANCED);
            var stages = ContourBoundaries.stages(scope, mask, mask, parameters.boundary());
            var repair = EdgeRefinement.repair(mask, stages.closed(), parameters, 2000, 40, 20);
            assertFalse(repair.applied());
            assertTrue(repair.reason().contains("pixel budget"));
        }
    }

    @Test
    void unavoidablyComplexEnhancedRequestReleasesItsProcessingSlot() {
        var limited = new VisionProcessingService(new VisionProperties(50, 150, 100, 1,
                10_485_760, 16_000_000, 1, 1, 1600, 88), new OpenCvRuntime());
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(200, 320, CvType.CV_8UC3));
            for (int x = 20; x < 300; x += 60) Imgproc.rectangle(image, new Point(x, 40), new Point(x + 30, 150), Scalar.all(255), -1);
            byte[] bytes = encode(scope, image);
            var parameters = parameters(DetectionMode.ENHANCED);
            assertEquals(VisionException.Reason.TOO_COMPLEX, assertThrows(VisionException.class, () -> limited.process(bytes, parameters)).reason());
            Mat blank = scope.own(Mat.zeros(200, 320, CvType.CV_8UC3));
            assertEquals(0, limited.process(encode(scope, blank), parameters).contourCount());
        }
    }

    private static ProcessingParameters parameters(DetectionMode mode) {
        return new ProcessingParameters(50, 150, 100, 5, null,
                new BoundaryOptions(BoundaryOptions.Mode.CANNY, 3, 1, null, BoundaryOptions.Shape.RECTANGLE), null, mode);
    }

    private static byte[] encode(MatScope scope, Mat image) {
        MatOfByte encoded = scope.own(new MatOfByte());
        assertTrue(Imgcodecs.imencode(".png", image, encoded));
        return encoded.toArray();
    }
}
