package za.co.vision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import za.co.vision.model.ProcessingParameters;

/**
 * Startup-validated defaults and resource budgets, bound from {@code vision.*}.
 * @param cannyLow default lower Canny threshold
 * @param cannyHigh default upper Canny threshold
 * @param minContourArea default minimum polygon area in px²
 * @param blurKernel default odd Gaussian kernel side length
 * @param maxUploadBytes maximum encoded input bytes, also enforced independently of multipart limits
 * @param maxPixels maximum width times height, checked before OpenCV decodes the image
 * @param maxContours maximum discovered contours before geometry/encoding is refused
 * @param maxConcurrentProcessing maximum simultaneous native processing requests per JVM
 * @param previewMaxDimension longest displayed image side in pixels, from 320 to 2048
 * @param previewJpegQuality display image JPEG quality, from 50 to 95
 */
@ConfigurationProperties("vision")
public record VisionProperties(double cannyLow, double cannyHigh, double minContourArea,
                               int blurKernel, long maxUploadBytes, long maxPixels,
                               int maxContours, int maxConcurrentProcessing,
                               int previewMaxDimension, int previewJpegQuality) {
    /**
     * Validates controls and positive budgets at startup.
     * @throws IllegalArgumentException if defaults or budgets are invalid
     */
    public VisionProperties {
        new ProcessingParameters(cannyLow, cannyHigh, minContourArea, blurKernel);
        if (maxUploadBytes < 1 || maxPixels < 1 || maxContours < 1 || maxConcurrentProcessing < 1) {
            throw new IllegalArgumentException("Vision resource budgets must all be positive.");
        }
        if (previewMaxDimension < 320 || previewMaxDimension > 2048
                || previewJpegQuality < 50 || previewJpegQuality > 95) {
            throw new IllegalArgumentException("Preview dimension must be 320–2048 and JPEG quality 50–95.");
        }
    }

    /**
     * Creates the default controls for a request.
     * @return immutable, validated default controls
     */
    public ProcessingParameters defaults() {
        return new ProcessingParameters(cannyLow, cannyHigh, minContourArea, blurKernel);
    }
}
