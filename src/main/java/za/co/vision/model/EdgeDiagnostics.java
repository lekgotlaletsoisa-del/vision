package za.co.vision.model;

import java.util.List;

/**
 * Explains automatic decisions without claiming object identity or boundary completeness.
 * All lengths and counts refer to the original photograph, never the preview.
 * @param cannyLow selected lower threshold; requested baseline remains in parameters
 * @param cannyHigh selected upper threshold
 * @param minimumTraceLength alternative minimum traced perimeter; zero disables length rescue
 * @param minimumTraceSpan minimum bounding-box diagonal for the same rescue
 * @param closingApplied whether guarded closing replaced raw Canny as the contour source
 * @param closingDecision reason for applying or refusing automatic repair
 * @param trials evaluated Canny settings and smoothing methods, in deterministic order
 * @param denoising bilateral comparison and fallback decision; null in older profiles/archives
 */
public record EdgeDiagnostics(double cannyLow, double cannyHigh, double minimumTraceLength,
                              double minimumTraceSpan, boolean closingApplied,
                              String closingDecision, List<Trial> trials, Denoising denoising) {
    /**
     * Preserves callers and archives that predate the bilateral comparison.
     * @param cannyLow selected lower threshold
     * @param cannyHigh selected upper threshold
     * @param minimumTraceLength alternative traced-perimeter minimum
     * @param minimumTraceSpan alternative bounding-diagonal minimum
     * @param closingApplied whether automatic gap repair was selected
     * @param closingDecision reason for the repair decision
     * @param trials evaluated Canny thresholds
     */
    public EdgeDiagnostics(double cannyLow, double cannyHigh, double minimumTraceLength,
                           double minimumTraceSpan, boolean closingApplied, String closingDecision,
                           List<Trial> trials) {
        this(cannyLow, cannyHigh, minimumTraceLength, minimumTraceSpan, closingApplied, closingDecision, trials, null);
    }
    /** Takes an immutable snapshot of evaluated trials. */
    public EdgeDiagnostics {
        trials = List.copyOf(trials);
    }

    /**
     * Describes a heuristic comparison, not an accuracy or confidence score.
     * Trace perimeter can traverse both sides of an incomplete edge.
     * @param low evaluated lower threshold
     * @param high evaluated upper threshold
     * @param edgePixels nonzero Canny pixels
     * @param sustainedLength summed perimeter of sufficiently long, spatially extended traces
     * @param shortLength summed perimeter of remaining traces
     * @param eligible whether density, complexity and baseline-preservation guards passed
     * @param smoothing GAUSSIAN or BILATERAL; missing values in old archives mean GAUSSIAN
     */
    public record Trial(double low, double high, int edgePixels, double sustainedLength,
                        double shortLength, boolean eligible, String smoothing) {
        /**
         * Creates a legacy Gaussian trial.
         * @param low lower Canny threshold
         * @param high upper Canny threshold
         * @param edgePixels nonzero edge pixels
         * @param sustainedLength long-trace perimeter sum
         * @param shortLength remaining perimeter sum
         * @param eligible whether threshold guards passed
         */
        public Trial(double low, double high, int edgePixels, double sustainedLength,
                     double shortLength, boolean eligible) {
            this(low, high, edgePixels, sustainedLength, shortLength, eligible, "GAUSSIAN");
        }
        /** Normalizes the smoothing label for archives predating this field. */
        public Trial { smoothing = smoothing == null ? "GAUSSIAN" : smoothing; }
    }

    /**
     * Compares noise at the same selected Canny thresholds, without an object-confidence claim.
     * Protected traces meet the area minimum or the sustained length/span criteria.
     * @param filter selected GAUSSIAN or BILATERAL smoothing
     * @param decision selection/fallback reason
     * @param diameter bilateral neighborhood diameter; zero when the comparison is disabled
     * @param sigmaColor bilateral intensity-distance sigma in 8-bit grayscale units
     * @param sigmaSpace bilateral spatial sigma in source pixels
     * @param boundarySupport fraction of protected Gaussian edge pixels within one pixel of candidate edges; null when not evaluated
     * @param referenceShortLength Gaussian short-trace perimeter sum; null when not evaluated
     * @param candidateShortLength bilateral short-trace perimeter sum; null when not evaluated
     */
    public record Denoising(String filter, String decision, int diameter, double sigmaColor, double sigmaSpace,
                            Double boundarySupport, Double referenceShortLength, Double candidateShortLength) { }

}
