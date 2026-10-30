package za.co.vision.service;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import za.co.vision.model.ContourMeasurement;
import za.co.vision.model.ContourDrawing;

/** Renders small, high-contrast browser previews without changing detection or geometry. */
final class PreviewRenderer {
    private static final Scalar BLUE = new Scalar(235, 155, 25);
    private static final Scalar GREEN = new Scalar(60, 220, 25);
    private static final Scalar OUTLINE = new Scalar(25, 30, 25);

    private PreviewRenderer() { }

    /** All dimensions describe display pixels, while measurements remain in source pixels. */
    record Previews(int width, int height, String original, String edges, String contours, String combined,
                    List<ContourDrawing> drawings, String mask) { }

    static Previews render(Mat original, Mat edges, Mat boundaryMask, List<MatOfPoint> contours,
                           List<ContourMeasurement> measurements, int maxDimension, int quality) {
        try (MatScope scope = new MatScope()) {
            double scale = Math.min(1.0, (double) maxDimension / Math.max(original.cols(), original.rows()));
            int width = Math.max(1, (int) Math.round(original.cols() * scale));
            int height = Math.max(1, (int) Math.round(original.rows() * scale));
            Size size = new Size(width, height);
            Mat photograph = scope.own(new Mat());
            Mat mask = scope.own(new Mat());
            Imgproc.resize(original, photograph, size, 0, 0, Imgproc.INTER_AREA);
            // Area averaging plus a nonzero threshold keeps thin source edges during reduction.
            Imgproc.resize(edges, mask, size, 0, 0, Imgproc.INTER_AREA);
            Imgproc.threshold(mask, mask, 0, 255, Imgproc.THRESH_BINARY);
            Mat extractionPreview = scope.own(new Mat());
            Imgproc.resize(boundaryMask, extractionPreview, size, 0, 0, Imgproc.INTER_AREA);
            Imgproc.threshold(extractionPreview, extractionPreview, 0, 255, Imgproc.THRESH_BINARY);
            int edgeWidth = Math.max(2, (int) Math.round(Math.max(width, height) / 400.0));
            Mat edgeKernel = scope.own(Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,
                    new Size(edgeWidth, edgeWidth)));
            Imgproc.dilate(mask, mask, edgeKernel);
            Mat outlineMask = scope.own(new Mat());
            Mat outlineKernel = scope.own(Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3)));
            Imgproc.dilate(mask, outlineMask, outlineKernel);
            Mat edgeView = scope.own(photograph.clone());
            edgeView.setTo(OUTLINE, outlineMask);
            edgeView.setTo(BLUE, mask);
            Mat contourView = scope.own(photograph.clone());
            Mat combinedView = scope.own(edgeView.clone());
            double scaleX = (double) width / original.cols();
            double scaleY = (double) height / original.rows();
            List<ContourDrawing> drawings = new ArrayList<>();
            int drawingPoints = 0;
            for (int i = 0; i < contours.size(); i++) {
                Point[] points = Arrays.stream(contours.get(i).toArray())
                        .map(point -> scaled(point.x, point.y, scaleX, scaleY)).toArray(Point[]::new);
                // Release each temporary contour before advancing to the next one.
                try (MatScope contourScope = new MatScope()) {
                    MatOfPoint2f source = contourScope.own(new MatOfPoint2f(contours.get(i).toArray()));
                    MatOfPoint2f simplified = contourScope.own(new MatOfPoint2f());
                    Imgproc.approxPolyDP(source, simplified, .75 / Math.min(scaleX, scaleY), true);
                    Point[] drawing = simplified.toArray();
                    drawingPoints += drawing.length;
                    if (drawingPoints > 100_000) {
                        throw new VisionException(VisionException.Reason.TOO_COMPLEX,
                                "Too many display boundary points. Increase blur, filter small contours or crop the image.");
                    }
                    StringBuilder path = new StringBuilder();
                    for (int point = 0; point < drawing.length; point++) {
                        path.append(point == 0 ? 'M' : 'L').append(Math.round(drawing[point].x))
                                .append(' ').append(Math.round(drawing[point].y));
                    }
                    path.append('Z');
                    drawings.add(new ContourDrawing(measurements.get(i).id(), path.toString()));
                    MatOfPoint displayContour = contourScope.own(new MatOfPoint(points));
                    ContourMeasurement measurement = measurements.get(i);
                    Point centre = scaled(measurement.centre().x(), measurement.centre().y(), scaleX, scaleY);
                    drawContour(contourView, displayContour, centre, measurement.id(), edgeWidth + 2);
                    drawContour(combinedView, displayContour, centre, measurement.id(), edgeWidth + 2);
                }
            }
            return new Previews(width, height, jpeg(photograph, quality), jpeg(edgeView, quality),
                    jpeg(contourView, quality), jpeg(combinedView, quality), List.copyOf(drawings), png(extractionPreview));
        }
    }

    private static Point scaled(double x, double y, double scaleX, double scaleY) {
        return new Point((x + 0.5) * scaleX - 0.5, (y + 0.5) * scaleY - 0.5);
    }

    private static void drawContour(Mat image, MatOfPoint contour, Point centre, int id, int thickness) {
        List<MatOfPoint> boundary = List.of(contour);
        Imgproc.drawContours(image, boundary, 0, OUTLINE, thickness + 2, Imgproc.LINE_AA);
        Imgproc.drawContours(image, boundary, 0, GREEN, thickness, Imgproc.LINE_AA);
        double fontScale = Math.max(0.6, thickness / 8.0);
        Point label = new Point(Math.max(0, Math.min(image.cols() - 25, centre.x + 8)),
                Math.max(20, Math.min(image.rows() - 1, centre.y - 8)));
        Imgproc.putText(image, "#" + id, label, Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, OUTLINE, 4, Imgproc.LINE_AA);
        Imgproc.putText(image, "#" + id, label, Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, GREEN, 2, Imgproc.LINE_AA);
    }

    private static String jpeg(Mat image, int quality) {
        try (MatScope scope = new MatScope()) {
            MatOfByte output = scope.own(new MatOfByte());
            MatOfInt parameters = scope.own(new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality));
            if (!Imgcodecs.imencode(".jpg", image, output, parameters)) {
                throw new IllegalStateException("OpenCV could not encode a preview image.");
            }
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(output.toArray());
        }
    }

    private static String png(Mat image) {
        try (MatScope scope = new MatScope()) {
            MatOfByte output = scope.own(new MatOfByte());
            if (!Imgcodecs.imencode(".png", image, output)) throw new IllegalStateException("Could not encode the extraction mask.");
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toArray());
        }
    }
}
