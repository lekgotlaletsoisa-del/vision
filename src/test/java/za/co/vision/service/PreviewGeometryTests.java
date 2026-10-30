package za.co.vision.service;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.*;
import za.co.vision.config.OpenCvRuntime;
import static org.junit.jupiter.api.Assertions.*;

class PreviewGeometryTests {
    @BeforeAll
    static void load() { new OpenCvRuntime(); }

    @Test
    void displayResizingNeverChangesSourcePathsBoxesCentroidsOrAxes() {
        try (MatScope scope = new MatScope()) {
            Mat original = scope.own(Mat.zeros(3000, 4000, CvType.CV_8UC3));
            Mat edges = scope.own(Mat.zeros(3000, 4000, CvType.CV_8UC1));
            MatOfPoint contour = scope.own(new MatOfPoint(new Point(800, 600), new Point(3200, 600),
                    new Point(3200, 2400), new Point(800, 2400)));
            var measure = ContourGeometry.measure(contour, 1, 4000, 3000);
            var large = PreviewRenderer.render(original, edges, edges, List.of(contour), List.of(measure), 1600, 88);
            var small = PreviewRenderer.render(original, edges, edges, List.of(contour), List.of(measure), 400, 88);
            assertEquals(1600, large.width()); assertEquals(1200, large.height());
            assertEquals(400, small.width()); assertEquals(300, small.height());
            assertEquals(large.drawings(), small.drawings());
            assertTrue(small.drawings().getFirst().path().contains("800 600"));
            assertTrue(small.drawings().getFirst().path().contains("3200 2400"));
            assertEquals(4_320_000, measure.area());
            assertEquals(2000, measure.centre().x());
            assertEquals(800, measure.orientationAxis().start().x(), .001);
            assertEquals(3200, measure.orientationAxis().end().x(), .001);
            assertEquals(1500, measure.orientationAxis().start().y(), .001);
            assertEquals(1500, measure.orientationAxis().end().y(), .001);
        }
    }

    @Test
    void rotatedAxisFollowsReturnedBoxAndCentroidMarkerRemainsIndependent() {
        try (MatScope scope = new MatScope()) {
            Point[] corners = new Point[4];
            new RotatedRect(new Point(200, 200), new Size(160, 30), 35).points(corners);
            MatOfPoint contour = scope.own(new MatOfPoint(corners));
            var geometry = ContourGeometry.measure(contour, 1, 400, 400);
            var axis = geometry.orientationAxis();
            double dx = axis.end().x() - axis.start().x(), dy = axis.end().y() - axis.start().y();
            assertEquals(geometry.rotatedRectangle().width(), Math.hypot(dx, dy), .001);
            assertEquals(geometry.rotatedRectangle().orientationDegrees(), Math.toDegrees(Math.atan2(dy, dx)), .001);
            assertEquals(geometry.rotatedRectangle().centre().x(), (axis.start().x() + axis.end().x()) / 2, .001);
        }
    }
}
