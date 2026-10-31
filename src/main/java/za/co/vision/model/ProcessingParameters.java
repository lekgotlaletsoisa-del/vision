package za.co.vision.model;

/**
 * Validated controls for one processing request. Canny uses a 3-pixel Sobel aperture
 * and L2 gradient magnitude; all geometry stays at the original image resolution.
 * @param cannyLow lower hysteresis threshold, from 0 to 255
 * @param cannyHigh upper hysteresis threshold, greater than the lower and at most 255
 * @param minContourArea inclusive minimum polygon area in square pixels; ENHANCED also admits sustained long traces
 * @param blurKernel odd Gaussian kernel side length from 1 to 31; 1 disables smoothing
 * @param filters optional shape/maximum-area filters; null selects disabled additional filters
 * @param boundary contour-mask experiment; null selects the original Canny pipeline
 * @param tracing contour retrieval, chain storage and diagnostic approximation options
 * @param detectionMode automatic refinement policy; null preserves STANDARD behavior
 */
public record ProcessingParameters(double cannyLow, double cannyHigh,
                                   double minContourArea, int blurKernel, ContourFilters filters,
                                   BoundaryOptions boundary, ContourOptions tracing, DetectionMode detectionMode) {
    /**
     * Preserves exact legacy processing for existing Java callers and saved requests.
     * @param cannyLow lower Canny threshold
     * @param cannyHigh upper Canny threshold
     * @param minContourArea minimum polygon area in px²
     * @param blurKernel odd Gaussian kernel side
     * @param filters optional geometry filters
     * @param boundary contour source and morphology
     * @param tracing retrieval, chain storage and approximation
     */
    public ProcessingParameters(double cannyLow, double cannyHigh, double minContourArea, int blurKernel,
                                ContourFilters filters, BoundaryOptions boundary, ContourOptions tracing) {
        this(cannyLow, cannyHigh, minContourArea, blurKernel, filters, boundary, tracing, DetectionMode.STANDARD);
    }
    /**
     * Preserves existing callers with default contour tracing.
     * @param cannyLow lower threshold
     * @param cannyHigh upper threshold
     * @param minContourArea minimum area in px²
     * @param blurKernel Gaussian kernel side
     * @param filters optional geometry filters
     * @param boundary contour source and morphology
     */
    public ProcessingParameters(double cannyLow, double cannyHigh, double minContourArea, int blurKernel,
                                ContourFilters filters, BoundaryOptions boundary) {
        this(cannyLow, cannyHigh, minContourArea, blurKernel, filters, boundary, ContourOptions.defaults());
    }
    /**
     * Preserves the original Java constructor and raw minimum-area filtering behavior.
     * @param cannyLow lower threshold
     * @param cannyHigh upper threshold
     * @param minContourArea minimum area in px²
     * @param blurKernel odd Gaussian kernel side length
     */
    public ProcessingParameters(double cannyLow, double cannyHigh, double minContourArea, int blurKernel) {
        this(cannyLow, cannyHigh, minContourArea, blurKernel, ContourFilters.none(), BoundaryOptions.raw());
    }

    /**
     * Selects optional filters while keeping raw Canny extraction.
     * @param cannyLow lower threshold
     * @param cannyHigh upper threshold
     * @param minContourArea minimum area in px²
     * @param blurKernel odd Gaussian kernel side
     * @param filters optional source-resolution filters
     */
    public ProcessingParameters(double cannyLow, double cannyHigh, double minContourArea, int blurKernel,
                                ContourFilters filters) {
        this(cannyLow, cannyHigh, minContourArea, blurKernel, filters, BoundaryOptions.raw());
    }
    /**
     * Checks numeric limits, finiteness and threshold order.
     * @throws IllegalArgumentException if any control is invalid
     */
    public ProcessingParameters {
        detectionMode = detectionMode == null ? DetectionMode.STANDARD : detectionMode;
        filters = filters == null ? ContourFilters.none() : filters;
        boundary = boundary == null ? BoundaryOptions.raw() : boundary;
        tracing = tracing == null ? ContourOptions.defaults() : tracing;
        if (!Double.isFinite(cannyLow) || !Double.isFinite(cannyHigh)
                || cannyLow < 0 || cannyHigh > 255 || cannyLow >= cannyHigh) {
            throw new IllegalArgumentException("Canny thresholds must satisfy 0 ≤ low < high ≤ 255.");
        }
        if (!Double.isFinite(minContourArea) || minContourArea < 0 || minContourArea > 16_000_000) {
            throw new IllegalArgumentException("Minimum contour area must be between 0 and 16,000,000 px².");
        }
        if (blurKernel < 1 || blurKernel > 31 || blurKernel % 2 == 0) {
            throw new IllegalArgumentException("Gaussian kernel must be an odd integer between 1 and 31.");
        }
        if (filters.maxContourArea() != null && filters.maxContourArea() < minContourArea) {
            throw new IllegalArgumentException("Maximum contour area must be at least the minimum area.");
        }
    }
}
