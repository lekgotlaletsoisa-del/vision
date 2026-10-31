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
 * @param trials evaluated Canny settings, in deterministic order
 */
public record EdgeDiagnostics(double cannyLow, double cannyHigh, double minimumTraceLength,
                              double minimumTraceSpan, boolean closingApplied,
                              String closingDecision, List<Trial> trials) {
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
     */
    public record Trial(double low, double high, int edgePixels, double sustainedLength,
                        double shortLength, boolean eligible) { }
}
