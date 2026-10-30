package za.co.vision.model;

import java.util.List;

/**
 * Immutable geometry for one retained external contour; no native objects escape.
 * Coordinates use image origin at top left, X rightwards and Y downwards.
 * @param id one-based ID matching the label in the overlay, sorted by decreasing area
 * @param area polygon area in px², not the count of enclosed raster pixels
 * @param perimeter closed polygon perimeter in px
 * @param boundingRectangle axis-aligned rectangle with integer raster dimensions
 * @param centre moment centroid, or rotated-rectangle centre when m00 is zero
 * @param centroidFallback true when a zero-area contour required the fallback
 * @param rotatedRectangle minimum-area rectangle and normalized orientation
 */
public record ContourMeasurement(int id, double area, double perimeter,
                                 BoundingRectangle boundingRectangle, Coordinate centre,
                                 boolean centroidFallback, OrientedRectangle rotatedRectangle) {
    /**
     * Image-space point, which may be subpixel or slightly outside the image for rotated boxes.
     * @param x horizontal coordinate in px
     * @param y vertical coordinate in px
     */
    public record Coordinate(double x, double y) { }

    /**
     * Axis-aligned raster bounds; the last included pixel is (x+width-1, y+height-1).
     * @param x leftmost pixel coordinate
     * @param y topmost pixel coordinate
     * @param width raster width in px
     * @param height raster height in px
     */
    public record BoundingRectangle(int x, int y, int width, int height) { }

    /**
     * Rectangle normalized to width ≥ height. Angle follows the width (long) axis,
     * clockwise from positive X in image coordinates, modulo 180, in [0,180).
     * If OpenCV width is shorter than height, they are swapped and 90° is added.
     * Squares have an arbitrary equivalent side; a point has a null orientation.
     * @param centre geometric rectangle centre, independent of contour centroid
     * @param width long side length in px
     * @param height short side length in px, possibly zero for a line
     * @param orientationDegrees normalized angle, null only for a point
     * @param vertices four original OpenCV box corners in cyclic order
     */
    public record OrientedRectangle(Coordinate centre, double width, double height,
                                    Double orientationDegrees, List<Coordinate> vertices) {
        /** Copies the vertex list so callers cannot mutate results. */
        public OrientedRectangle {
            vertices = List.copyOf(vertices);
        }
    }
}
