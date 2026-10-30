package za.co.vision.service;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import za.co.vision.config.OpenCvRuntime;
import static org.junit.jupiter.api.Assertions.*;

class ContourGeometryTests {
    @BeforeAll
    static void load() { new OpenCvRuntime(); }

    @Test
    void rectangleHasExactShapeDescriptors() {
        try (MatScope scope = new MatScope()) {
            var shape = scope.own(new MatOfPoint(new Point(20, 30), new Point(140, 30),
                    new Point(140, 110), new Point(20, 110)));
            var result = ContourGeometry.measure(shape, 1, 200, 200);
            assertEquals(1.5, result.aspectRatio(), 1e-6);
            assertEquals(4 * Math.PI * 9600 / (400 * 400), result.circularity(), 1e-6);
            assertEquals(1, result.solidity(), 1e-6);
            assertEquals(9600.0 / (121 * 81), result.extent(), 1e-6);
            assertFalse(result.touchesImageBoundary());
            assertEquals(5, result.withId(5).id());
            assertEquals(result.area(), result.withId(5).area());
        }
    }

    @Test
    void circularContourIsRoundAndSolid() {
        try (MatScope scope = new MatScope()) {
            List<Point> points = new ArrayList<>();
            for (int degrees = 0; degrees < 360; degrees += 2) {
                double radians = Math.toRadians(degrees);
                points.add(new Point(Math.round(100 + 60 * Math.cos(radians)), Math.round(100 + 60 * Math.sin(radians))));
            }
            var circle = scope.own(new MatOfPoint(points.toArray(Point[]::new)));
            var result = ContourGeometry.measure(circle, 1, 240, 240);
            assertEquals(1, result.aspectRatio(), .02);
            assertTrue(result.circularity() > .95 && result.circularity() <= 1);
            assertTrue(result.solidity() > .98 && result.solidity() <= 1);
        }
    }

    @Test
    void narrowRotatedObjectHasLongAxisAspectRatio() {
        try (MatScope scope = new MatScope()) {
            Point[] vertices = new Point[4];
            new org.opencv.core.RotatedRect(new Point(150, 150), new org.opencv.core.Size(160, 8), 35).points(vertices);
            var shape = scope.own(new MatOfPoint(vertices));
            var result = ContourGeometry.measure(shape, 1, 300, 300);
            assertEquals(20, result.aspectRatio(), 2);
            assertEquals(35, result.rotatedRectangle().orientationDegrees(), 1);
            assertTrue(result.circularity() < .2);
        }
    }

    @Test
    void concavePolygonHasSolidityBelowOne() {
        try (MatScope scope = new MatScope()) {
            var shape = scope.own(new MatOfPoint(new Point(10, 10), new Point(90, 10), new Point(90, 30),
                    new Point(30, 30), new Point(30, 90), new Point(10, 90)));
            var result = ContourGeometry.measure(shape, 1, 120, 120);
            assertEquals(2800.0 / 4600, result.solidity(), 1e-6);
            assertEquals(2800.0 / (81 * 81), result.extent(), 1e-6);
        }
    }

    @Test
    void contactWithAnyImageBorderIsMeasuredWithoutRejection() {
        try (MatScope scope = new MatScope()) {
            for (Point point : new Point[]{new Point(0, 10), new Point(10, 0), new Point(99, 10), new Point(10, 99)}) {
                var shape = scope.own(new MatOfPoint(point));
                assertTrue(ContourGeometry.measure(shape, 1, 100, 100).touchesImageBoundary());
            }
            var inner = scope.own(new MatOfPoint(new Point(98, 98)));
            assertFalse(ContourGeometry.measure(inner, 1, 100, 100).touchesImageBoundary());
        }
    }

    @Test
    void degeneratePointAndLineHaveSafeRatios() {
        try (MatScope scope = new MatScope()) {
            for (Point[] points : new Point[][]{{new Point(20, 20)}, {new Point(10, 10), new Point(90, 10)}}) {
                var shape = scope.own(new MatOfPoint(points));
                var result = ContourGeometry.measure(shape, 1, 100, 100);
                assertNull(result.aspectRatio());
                assertEquals(0, result.circularity());
                assertEquals(0, result.solidity());
                assertEquals(0, result.extent());
                assertTrue(result.centroidFallback());
            }
        }
    }
}
