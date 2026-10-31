package za.co.vision.service;

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import za.co.vision.config.OpenCvRuntime;
import za.co.vision.config.VisionProperties;
import za.co.vision.model.*;
import static org.junit.jupiter.api.Assertions.*;

/** Analytic experiments distinguish restored gaps, accidental connections and trace topology. */
class ContourExperimentTests {
    private static VisionProcessingService service;

    @BeforeAll
    static void load() {
        service = new VisionProcessingService(new VisionProperties(50, 150, 100, 5,
                10_485_760, 16_000_000, 2000, 2, 1600, 88), new OpenCvRuntime());
    }

    @Test
    void completeRectangleAndBrokenBoundaryHaveDifferentAreaDespiteBothReturningClosedTraces() {
        try (MatScope scope = new MatScope()) {
            Mat complete = scope.own(Mat.zeros(120, 160, CvType.CV_8UC1));
            Imgproc.rectangle(complete, new Point(30, 30), new Point(130, 90), Scalar.all(255), 1);
            Mat broken = scope.own(complete.clone());
            Imgproc.line(broken, new Point(75, 30), new Point(77, 30), Scalar.all(0), 1);
            var before = measureMask(scope, broken);
            var intact = measureMask(scope, complete);
            var stages = ContourBoundaries.stages(scope, broken, broken, boundary(BoundaryOptions.Mode.MORPHOLOGY, 5, 1, BoundaryOptions.Shape.RECTANGLE));
            var repaired = measureMask(scope, stages.active());
            assertEquals(6000, intact.area(), .01);
            assertTrue(before.area() < 10, "An open one-pixel outline is traced back on itself; closed=true does not restore its region.");
            assertEquals(intact.area(), repaired.area(), .01);
            assertEquals(0, Core.norm(complete, stages.closed(), Core.NORM_INF));
            assertEquals(0, broken.get(30, 76)[0], "Keep the raw broken boundary for comparison.");
        }
    }

    @Test
    void nearbyRectanglesStayIndependentWithSmallKernelButExcessiveClosingMergesThem() {
        try (MatScope scope = new MatScope()) {
            Mat mask = scope.own(Mat.zeros(120, 180, CvType.CV_8UC1));
            Imgproc.rectangle(mask, new Point(20, 30), new Point(70, 90), Scalar.all(255), -1);
            Imgproc.rectangle(mask, new Point(76, 30), new Point(130, 90), Scalar.all(255), -1);
            for (var shape : BoundaryOptions.Shape.values()) {
                var small = ContourBoundaries.stages(scope, mask, mask, boundary(BoundaryOptions.Mode.MORPHOLOGY, 3, 1, shape));
                var excessive = ContourBoundaries.stages(scope, mask, mask, boundary(BoundaryOptions.Mode.MORPHOLOGY, 9, 1, shape));
                assertEquals(2, components(scope, small.active()));
                assertEquals(1, components(scope, excessive.active()));
            }
        }
    }

    @Test
    void closingCanAttachNearbyGroutLineAndReduceRotatedBoxFit() {
        try (MatScope scope = new MatScope()) {
            Mat mask = scope.own(Mat.zeros(180, 180, CvType.CV_8UC1));
            Imgproc.rectangle(mask, new Point(40, 60), new Point(100, 120), Scalar.all(255), -1);
            Imgproc.line(mask, new Point(106, 10), new Point(106, 165), Scalar.all(255), 1);
            var joined = ContourBoundaries.stages(scope, mask, mask, boundary(BoundaryOptions.Mode.MORPHOLOGY, 9, 1, BoundaryOptions.Shape.RECTANGLE));
            assertEquals(2, components(scope, mask));
            assertEquals(1, components(scope, joined.active()));
            var geometry = measureMask(scope, joined.active());
            double boxArea = geometry.rotatedRectangle().width() * geometry.rotatedRectangle().height();
            assertTrue(boxArea > 3600 * 2, "The box follows the line as well as the rectangle.");
            assertTrue(geometry.area() / boxArea < .6, "Low fit warns about empty box area, not object identity.");
        }
    }

    @Test
    void openingRemovesSeveralNoisyShapesAndKernelFootprintsAreMeasurablyDifferent() {
        try (MatScope scope = new MatScope()) {
            Mat mask = scope.own(Mat.zeros(100, 140, CvType.CV_8UC1));
            Imgproc.rectangle(mask, new Point(40, 30), new Point(100, 80), Scalar.all(255), -1);
            for (int x = 5; x < 30; x += 6) Imgproc.rectangle(mask, new Point(x, 5), new Point(x + 1, 6), Scalar.all(255), -1);
            var rect = ContourBoundaries.stages(scope, mask, mask, boundary(BoundaryOptions.Mode.MORPHOLOGY, 1, 5, BoundaryOptions.Shape.RECTANGLE));
            var ellipse = ContourBoundaries.stages(scope, mask, mask, boundary(BoundaryOptions.Mode.MORPHOLOGY, 1, 5, BoundaryOptions.Shape.ELLIPSE));
            assertEquals(6, components(scope, mask));
            assertEquals(1, components(scope, rect.active()));
            assertEquals(1, components(scope, ellipse.active()));
            assertTrue(Core.norm(rect.active(), ellipse.active(), Core.NORM_INF) > 0, "Ellipse rounds corners while a rectangle retains these corners.");
            // The explicit erode/dilate implementation agrees with morphologyEx opening.
            Mat expected = scope.own(new Mat());
            Mat kernel = scope.own(Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5)));
            Imgproc.morphologyEx(mask, expected, Imgproc.MORPH_OPEN, kernel);
            assertEquals(0, Core.norm(expected, ellipse.active(), Core.NORM_INF));
        }
    }

    @Test
    void stagesAndActiveSourceMatchForAllModesWithDisabledOperationsPreservingRaw() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(160, 220, CvType.CV_8UC3));
            Imgproc.rectangle(image, new Point(30, 30), new Point(160, 120), Scalar.all(255), -1);
            byte[] bytes = encode(scope, image);
            for (var mode : BoundaryOptions.Mode.values()) {
                var result = process(bytes, boundary(mode, 1, 1, BoundaryOptions.Shape.RECTANGLE), ContourOptions.defaults(), 0);
                assertEquals(List.of("raw", "closed", "opened"), result.stages().stream().map(ProcessingStage::id).toList());
                assertEquals(1, result.stages().stream().map(ProcessingStage::image).distinct().count());
                for (var stage : result.stages()) {
                    assertEquals(result.edgePixelCount(), stage.pixelCount());
                    var encoded = scope.own(new MatOfByte(Base64.getDecoder().decode(stage.image().split(",")[1])));
                    Mat decoded = scope.own(Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_GRAYSCALE));
                    assertEquals(stage.pixelCount(), Core.countNonZero(decoded));
                }
                if (mode != BoundaryOptions.Mode.SEGMENTATION) {
                    assertEquals(result.stages().getFirst().image(), result.boundaryMaskImage());
                    assertEquals(result.edgePixelCount(), result.boundaryPixelCount());
                } else assertEquals("segmentation", result.contourSource());
            }
            var closed = process(bytes, boundary(BoundaryOptions.Mode.CANNY_CLOSED, 3, 3, BoundaryOptions.Shape.ELLIPSE), ContourOptions.defaults(), 0);
            assertEquals("closed", closed.contourSource());
            assertEquals(closed.stages().get(1).image(), closed.boundaryMaskImage());
            assertEquals(0, closed.stages().get(2).pixelCount(), "Opening may erase one-pixel Canny boundaries without changing the chosen closed source.");
        }
    }

    @Test
    void treeHierarchyIncludesHolesAndReferencesFilteredParentsByExtractionIndex() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(200, 240, CvType.CV_8UC3));
            Imgproc.rectangle(image, new Point(20, 20), new Point(200, 170), Scalar.all(255), -1);
            Imgproc.rectangle(image, new Point(60, 50), new Point(160, 130), Scalar.all(0), -1);
            byte[] bytes = encode(scope, image);
            var boundary = boundary(BoundaryOptions.Mode.SEGMENTATION, 1, 1, BoundaryOptions.Shape.ELLIPSE);
            var external = process(bytes, boundary, ContourOptions.defaults(), 0);
            var tree = process(bytes, boundary, new ContourOptions(ContourOptions.Retrieval.TREE, null, 1), 0);
            assertEquals(1, external.contourCount());
            assertEquals(2, tree.contourCount());
            var outer = tree.contourDiagnostics().stream().filter(d -> d.depth() == 0).findFirst().orElseThrow();
            var hole = tree.contourDiagnostics().stream().filter(ContourDiagnostics::hole).findFirst().orElseThrow();
            assertEquals(outer.sourceIndex(), hole.parentIndex());
            assertEquals(hole.sourceIndex(), outer.firstChildIndex());
            var filtered = service.process(bytes, new ProcessingParameters(50, 150, 0, 1,
                    new ContourFilters(15000.0, null, null, 0, 0, false), boundary,
                    new ContourOptions(ContourOptions.Retrieval.TREE, null, 1)));
            assertEquals(1, filtered.contourCount());
            assertEquals(1, filtered.contourDiagnostics().getFirst().id());
            assertEquals(hole.sourceIndex(), filtered.contourDiagnostics().getFirst().sourceIndex());
            assertEquals(outer.sourceIndex(), filtered.contourDiagnostics().getFirst().parentIndex());
        }
    }

    @Test
    void denseChainsAndApproximationPreserveOriginalRotatedRectangleMeasurements() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(260, 340, CvType.CV_8UC3));
            RotatedRect rectangle = new RotatedRect(new Point(170, 130), new Size(190, 34), 32);
            Point[] vertices = new Point[4]; rectangle.points(vertices);
            Imgproc.fillConvexPoly(image, scope.own(new MatOfPoint(vertices)), Scalar.all(255));
            byte[] bytes = encode(scope, image);
            var boundary = boundary(BoundaryOptions.Mode.SEGMENTATION, 1, 1, BoundaryOptions.Shape.ELLIPSE);
            var simple = process(bytes, boundary, new ContourOptions(null, ContourOptions.Chain.SIMPLE, .1), 0);
            var dense = process(bytes, boundary, new ContourOptions(null, ContourOptions.Chain.NONE, 2), 0);
            assertEquals(simple.contours(), dense.contours(), "Chain storage and approximation do not replace the measured trace.");
            var geometry = dense.contours().getFirst();
            var diagnostic = dense.contourDiagnostics().getFirst();
            assertTrue(diagnostic.pointCount() > simple.contourDiagnostics().getFirst().pointCount());
            assertEquals(4, diagnostic.approximationVertices());
            assertEquals(32, geometry.rotatedRectangle().orientationDegrees(), 1);
            assertTrue(geometry.aspectRatio() > 5);
            assertTrue(diagnostic.rotatedExtent() > .93);
            assertEquals(geometry.area() / diagnostic.rotatedRectangleArea(), diagnostic.rotatedExtent(), 1e-9);
            assertEquals(geometry.perimeter() * .02, diagnostic.approximationEpsilon(), 1e-9);
        }
    }

    @Test
    void approximationRejectsInvalidParameters() {
        for (double value : new double[]{-1, 10.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new ContourOptions(null, null, value));
        }
    }

    private static BoundaryOptions boundary(BoundaryOptions.Mode mode, int close, int open, BoundaryOptions.Shape shape) {
        return new BoundaryOptions(mode, close, open, BoundaryOptions.Foreground.BRIGHT, shape);
    }
    private static ProcessingResult process(byte[] bytes, BoundaryOptions boundary, ContourOptions tracing, double minArea) {
        return service.process(bytes, new ProcessingParameters(50, 150, minArea, 1, ContourFilters.none(), boundary, tracing));
    }
    private static int components(MatScope scope, Mat mask) {
        return Imgproc.connectedComponents(mask, scope.own(new Mat())) - 1;
    }
    private static ContourMeasurement measureMask(MatScope scope, Mat mask) {
        var contours = new java.util.ArrayList<MatOfPoint>();
        try { Imgproc.findContours(mask, contours, scope.own(new Mat()), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE); }
        finally { contours.forEach(scope::own); }
        assertEquals(1, contours.size());
        return ContourGeometry.measure(contours.getFirst(), 1, mask.cols(), mask.rows());
    }
    private static byte[] encode(MatScope scope, Mat image) {
        var bytes = scope.own(new MatOfByte());
        assertTrue(Imgcodecs.imencode(".png", image, bytes));
        return bytes.toArray();
    }
}
