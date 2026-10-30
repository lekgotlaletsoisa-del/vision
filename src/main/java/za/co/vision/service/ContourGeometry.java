package za.co.vision.service;

import java.util.Arrays;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import za.co.vision.model.ContourMeasurement;
import za.co.vision.model.ContourMeasurement.BoundingRectangle;
import za.co.vision.model.ContourMeasurement.Coordinate;
import za.co.vision.model.ContourMeasurement.OrientedRectangle;

/** Stateless geometry extraction shared by the pipeline and analytic fixture tests. */
final class ContourGeometry {
    private ContourGeometry() { }

    static ContourMeasurement measure(MatOfPoint contour, int id) {
        try (MatScope scope = new MatScope()) {
            MatOfPoint2f points = scope.own(new MatOfPoint2f(contour.toArray()));
            double area = Imgproc.contourArea(contour);
            double perimeter = Imgproc.arcLength(points, true);
            Rect bounds = Imgproc.boundingRect(contour);
            RotatedRect box = Imgproc.minAreaRect(points);
            Moments moments = Imgproc.moments(contour);
            boolean fallback = Math.abs(moments.get_m00()) < 1e-9;
            Coordinate centre = fallback ? coordinate(box.center)
                    : new Coordinate(moments.get_m10() / moments.get_m00(),
                                     moments.get_m01() / moments.get_m00());
            boolean swap = box.size.width < box.size.height;
            double width = Math.max(box.size.width, box.size.height);
            double height = Math.min(box.size.width, box.size.height);
            double angle = ((box.angle + (swap ? 90 : 0)) % 180 + 180) % 180;
            Point[] vertices = new Point[4];
            box.points(vertices);
            return new ContourMeasurement(id, area, perimeter,
                    new BoundingRectangle(bounds.x, bounds.y, bounds.width, bounds.height), centre,
                    fallback, new OrientedRectangle(coordinate(box.center), width, height,
                    width < 1e-9 ? null : angle,
                    Arrays.stream(vertices).map(ContourGeometry::coordinate).toList()));
        }
    }

    private static Coordinate coordinate(Point point) {
        return new Coordinate(point.x, point.y);
    }
}
