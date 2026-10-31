package za.co.vision.model;

/**
 * Explicit experiments for the source-resolution mask supplied to contour extraction.
 * Canny is always computed independently for the raw edge preview and edge-pixel count.
 * ENHANCED detection may conservatively select the closed comparison stage when mode is CANNY;
 * the result always reports its actual contour source and repair decision.
 * @param mode CANNY by default; other sources must be explicitly selected
 * @param closeKernel odd closing kernel side in source pixels, 1–15; 1 disables closing
 * @param openKernel odd opening kernel side in source pixels, 1–15; 1 disables opening
 * @param foreground brightness polarity for segmentation; ignored by edge modes
 * @param shape structuring element used for both operations; defaults to ellipse
 */
public record BoundaryOptions(Mode mode, int closeKernel, int openKernel, Foreground foreground, Shape shape) {
    /**
     * Preserves the original elliptical experiment constructor.
     * @param mode extraction mode
     * @param closeKernel odd closing side in source pixels
     * @param openKernel odd opening side in source pixels
     * @param foreground segmentation polarity
     */
    public BoundaryOptions(Mode mode, int closeKernel, int openKernel, Foreground foreground) {
        this(mode, closeKernel, openKernel, foreground, Shape.ELLIPSE);
    }

    /** Footprint of each morphology kernel. */
    public enum Shape {
        /** Full rectangular footprint, including corners. */
        RECTANGLE,
        /** Rounded footprint; discretization matters for small kernels. */
        ELLIPSE
    }
    /** Source of the contour extraction mask; none is an object recognizer. */
    public enum Mode {
        /** Original Canny mask in STANDARD; ENHANCED may apply guarded small-gap closing. */
        CANNY,
        /** Canny after closing only, before optional opening. */
        CANNY_CLOSED,
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
        shape = shape == null ? Shape.ELLIPSE : shape;
        if (closeKernel < 1 || closeKernel > 15 || closeKernel % 2 == 0
                || openKernel < 1 || openKernel > 15 || openKernel % 2 == 0) {
            throw new IllegalArgumentException("Morphology kernels must be odd integers from 1 to 15.");
        }
    }

    /**
     * Selects raw Canny in STANDARD, with morphology previews available to ENHANCED repair.
     * @return raw Canny options with experimental closing 3 and opening disabled
     */
    public static BoundaryOptions raw() {
        return new BoundaryOptions(Mode.CANNY, 3, 1, Foreground.BRIGHT);
    }
}
