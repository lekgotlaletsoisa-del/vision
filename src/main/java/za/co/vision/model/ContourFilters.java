package za.co.vision.model;

/**
 * Optional, inclusive filters on source-resolution geometry. A null upper area/ratio
 * or lower ratio disables that bound; circularity/solidity zero disable those minima.
 * @param maxContourArea optional maximum area in px², from 0 to 16,000,000
 * @param minAspectRatio optional minimum long-side/short-side ratio, at least 1
 * @param maxAspectRatio optional maximum ratio, at least 1
 * @param minCircularity minimum circularity from 0 to 1
 * @param minSolidity minimum solidity from 0 to 1
 * @param excludeBoundary whether to reject contours touching the image border
 */
public record ContourFilters(Double maxContourArea, Double minAspectRatio, Double maxAspectRatio,
                             double minCircularity, double minSolidity, boolean excludeBoundary) {
    /**
     * Validates finite bounds and their order.
     * @throws IllegalArgumentException if a bound is nonfinite, invalid or reversed
     */
    public ContourFilters {
        if (maxContourArea != null && (!Double.isFinite(maxContourArea) || maxContourArea < 0 || maxContourArea > 16_000_000)) {
            throw new IllegalArgumentException("Maximum contour area must be 0–16,000,000 px² or omitted.");
        }
        if (minAspectRatio != null && (!Double.isFinite(minAspectRatio) || minAspectRatio < 1)
                || maxAspectRatio != null && (!Double.isFinite(maxAspectRatio) || maxAspectRatio < 1)
                || minAspectRatio != null && maxAspectRatio != null && minAspectRatio > maxAspectRatio) {
            throw new IllegalArgumentException("Aspect ratio bounds must be finite, at least 1 and ordered.");
        }
        if (!Double.isFinite(minCircularity) || minCircularity < 0 || minCircularity > 1
                || !Double.isFinite(minSolidity) || minSolidity < 0 || minSolidity > 1) {
            throw new IllegalArgumentException("Minimum circularity and solidity must be between 0 and 1.");
        }
    }

    /**
     * Preserves the legacy minimum-area behavior.
     * @return disabled additional filters
     */
    public static ContourFilters none() {
        return new ContourFilters(null, null, null, 0, 0, false);
    }

    /**
     * Tests geometry without modifying the contour or its measurements.
     * Undefined aspect ratios pass when both aspect bounds are disabled.
     * @param contour original-resolution measurements
     * @param minimumArea existing minimum polygon area in px²
     * @return true when every enabled criterion passes
     */
    public boolean accepts(ContourMeasurement contour, double minimumArea) {
        Double ratio = contour.aspectRatio();
        return contour.area() >= minimumArea
                && (maxContourArea == null || contour.area() <= maxContourArea)
                && (minAspectRatio == null || ratio != null && ratio >= minAspectRatio)
                && (maxAspectRatio == null || ratio != null && ratio <= maxAspectRatio)
                && contour.circularity() >= minCircularity && contour.solidity() >= minSolidity
                && (!excludeBoundary || !contour.touchesImageBoundary());
    }
}
