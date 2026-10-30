package za.co.vision.service;

import java.util.Arrays;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfInt;
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
        return measure(contour, id, 0, 0);
    }

    /** Zero image dimensions mean boundary contact is unknown and reported false. */
    static ContourMeasurement measure(MatOfPoint contour, int id, int imageWidth, int imageHeight) {
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
            double dx = width * Math.cos(Math.toRadians(angle)) / 2;
            double dy = width * Math.sin(Math.toRadians(angle)) / 2;
            var axis = width < 1e-9 ? null : new ContourMeasurement.Axis(
                    new Coordinate(box.center.x - dx, box.center.y - dy),
                    new Coordinate(box.center.x + dx, box.center.y + dy));
            MatOfInt indices = scope.own(new MatOfInt());
            Imgproc.convexHull(contour, indices);
            Point[] polygon = contour.toArray();
            Point[] hullPoints = Arrays.stream(indices.toArray()).mapToObj(index -> polygon[index]).toArray(Point[]::new);
            MatOfPoint hull = scope.own(new MatOfPoint(hullPoints));
            double hullArea = Imgproc.contourArea(hull);
            double circularity = perimeter > 0 ? 4 * Math.PI * area / (perimeter * perimeter) : 0;
            double solidity = hullArea > 0 ? area / hullArea : 0;
            double extent = bounds.width > 0 && bounds.height > 0 ? area / ((double) bounds.width * bounds.height) : 0;
            boolean touchesBoundary = imageWidth > 0 && imageHeight > 0
                    && (bounds.x <= 0 || bounds.y <= 0 || (long) bounds.x + bounds.width >= imageWidth
                    || (long) bounds.y + bounds.height >= imageHeight);
            Point[] vertices = new Point[4];
            box.points(vertices);
            return new ContourMeasurement(id, area, perimeter,
                    new BoundingRectangle(bounds.x, bounds.y, bounds.width, bounds.height), centre,
                    fallback, new OrientedRectangle(coordinate(box.center), width, height,
                    width < 1e-9 ? null : angle,
                    Arrays.stream(vertices).map(ContourGeometry::coordinate).toList()),
                    height > 1e-9 ? width / height : null, circularity, solidity, extent, touchesBoundary, axis);
        }
    }

    private static Coordinate coordinate(Point point) {
        return new Coordinate(point.x, point.y);
    }
}
