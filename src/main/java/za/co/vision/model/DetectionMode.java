package za.co.vision.model;

/** Selects reproducible legacy processing or bounded automatic edge/trace refinement. */
public enum DetectionMode {
    /** Uses the exact supplied Canny thresholds, minimum area and contour-source options. */
    STANDARD,
    /** Compares bounded threshold trials, retains sustained traces and guards small-gap repair. */
    ENHANCED
}
