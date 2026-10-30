package za.co.vision.model;

/**
 * Explicit experiments for the source-resolution mask supplied to contour extraction.
 * Canny is always computed independently for the raw edge preview and edge-pixel count.
 * @param mode CANNY by default; experiments must be explicitly selected
 * @param closeKernel odd elliptical closing kernel side in source pixels, 1–15; 1 disables closing
 * @param openKernel odd elliptical opening kernel side in source pixels, 1–15; 1 disables opening
 * @param foreground brightness polarity for segmentation; ignored by edge modes
 */
public record BoundaryOptions(Mode mode, int closeKernel, int openKernel, Foreground foreground) {
    /** Source of the contour extraction mask; none is an object recognizer. */
    public enum Mode {
        /** Original Canny mask, without morphology. */
        CANNY,
        /** Experimental Canny closing followed by optional opening; may merge/remove edges. */
        MORPHOLOGY,
        /** Separate experimental Otsu brightness-region segmentation followed by morphology. */
        SEGMENTATION
    }

    /** Foreground assumption for the brightness-segmentation experiment. */
    public enum Foreground {
        /** Values above Otsu's threshold form foreground regions. */
        BRIGHT,
        /** Values below Otsu's threshold form foreground regions. */
        DARK
    }

    /**
     * Validates kernels and supplies missing enum defaults.
     * @throws IllegalArgumentException when a kernel is not odd or outside 1–15
     */
    public BoundaryOptions {
        mode = mode == null ? Mode.CANNY : mode;
        foreground = foreground == null ? Foreground.BRIGHT : foreground;
        if (closeKernel < 1 || closeKernel > 15 || closeKernel % 2 == 0
                || openKernel < 1 || openKernel > 15 || openKernel % 2 == 0) {
            throw new IllegalArgumentException("Morphology kernels must be odd integers from 1 to 15.");
        }
    }

    /**
     * Selects the unchanged Canny pipeline. Kernel defaults apply only if an experiment is selected.
     * @return raw Canny options with experimental closing 3 and opening disabled
     */
    public static BoundaryOptions raw() {
        return new BoundaryOptions(Mode.CANNY, 3, 1, Foreground.BRIGHT);
    }
}
