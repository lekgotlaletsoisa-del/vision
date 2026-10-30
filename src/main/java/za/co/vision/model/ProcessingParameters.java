package za.co.vision.model;

/**
 * Validated controls for one processing request. Canny uses a 3-pixel Sobel aperture
 * and L2 gradient magnitude; all geometry stays at the original image resolution.
 * @param cannyLow lower hysteresis threshold, from 0 to 255
 * @param cannyHigh upper hysteresis threshold, greater than the lower and at most 255
 * @param minContourArea inclusive minimum polygon area in square pixels
 * @param blurKernel odd Gaussian kernel side length from 1 to 31; 1 disables smoothing
 */
public record ProcessingParameters(double cannyLow, double cannyHigh,
                                   double minContourArea, int blurKernel) {
    /**
     * Checks numeric limits, finiteness and threshold order.
     * @throws IllegalArgumentException if any control is invalid
     */
    public ProcessingParameters {
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
    }
}
