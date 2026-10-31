package za.co.vision.model;

/**
 * An unannotated binary processing-stage preview. Counts always use original resolution.
 * @param id raw, closed or opened; also used by contourSource when that stage supplies contours
 * @param label human-readable operation and parameters
 * @param image bounded PNG data URL; thin pixels may combine when downsampled
 * @param pixelCount nonzero pixels before preview reduction or display thickening
 */
public record ProcessingStage(String id, String label, String image, int pixelCount) { }
