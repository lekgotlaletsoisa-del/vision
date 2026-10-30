package za.co.vision.model;

/**
 * Display-only boundary path generated in Java; source geometry is unchanged.
 * @param id retained contour ID matching the measurement
 * @param path SVG path in original image coordinates, simplified within 0.75 preview pixels
 */
public record ContourDrawing(int id, String path) { }
