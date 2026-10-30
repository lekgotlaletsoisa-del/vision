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
 * @param aspectRatio long side divided by short side; null when the short side is zero
 * @param circularity 4π times polygon area divided by closed perimeter squared; zero for zero perimeter
 * @param solidity polygon area divided by convex hull area; zero for a zero-area hull
 * @param extent polygon area divided by the inclusive raster bounding rectangle area
 * @param touchesImageBoundary true when contour bounds reach any source image border
 * @param orientationAxis source-coordinate endpoints of the rectangle's long axis; null for a point
 */
public record ContourMeasurement(int id, double area, double perimeter,
                                 BoundingRectangle boundingRectangle, Coordinate centre,
                                 boolean centroidFallback, OrientedRectangle rotatedRectangle,
                                 Double aspectRatio, double circularity, double solidity,
                                 double extent, boolean touchesImageBoundary, Axis orientationAxis) {
    /**
     * Relabels retained contours without recomputing native geometry.
     * @param newId one-based result label
     * @return the same measurements with the new ID
     */
    public ContourMeasurement withId(int newId) {
        return new ContourMeasurement(newId, area, perimeter, boundingRectangle, centre,
                centroidFallback, rotatedRectangle, aspectRatio, circularity, solidity, extent, touchesImageBoundary, orientationAxis);
    }

    /**
     * Long-axis segment computed in Java, centred on the rotated rectangle (not the area centroid).
     * @param start first source-coordinate endpoint
     * @param end opposite source-coordinate endpoint
     */
    public record Axis(Coordinate start, Coordinate end) { }
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
