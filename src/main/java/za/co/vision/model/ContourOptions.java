package za.co.vision.model;

/**
 * Contour tracing and diagnostic approximation controls. Approximation never changes filtering
 * or the original contour measurements.
 * @param retrieval EXTERNAL for outermost traces, TREE for the complete nesting hierarchy
 * @param chain SIMPLE compresses straight runs; NONE retains every boundary step
 * @param approximationPercent Douglas–Peucker epsilon as 0–10 percent of closed perimeter
 */
public record ContourOptions(Retrieval retrieval, Chain chain, double approximationPercent) {
    /** Contour nesting policy. */
    public enum Retrieval {
        /** Outermost traces only. */
        EXTERNAL,
        /** All traces and their parent/child relationships; includes holes and double Canny outlines. */
        TREE
    }
    /** Boundary point storage policy. */
    public enum Chain {
        /** Compress horizontal, vertical and diagonal straight runs. */
        SIMPLE,
        /** Store every boundary step; can require much more native memory. */
        NONE
    }
    /**
     * Supplies defaults and validates a finite approximation percentage.
     * @throws IllegalArgumentException for a nonfinite or out-of-range epsilon percentage
     */
    public ContourOptions {
        retrieval = retrieval == null ? Retrieval.EXTERNAL : retrieval;
        chain = chain == null ? Chain.SIMPLE : chain;
        if (!Double.isFinite(approximationPercent) || approximationPercent < 0 || approximationPercent > 10) {
            throw new IllegalArgumentException("Approximation must be between 0 and 10 percent of perimeter.");
        }
    }
    /**
     * Supplies the original retrieval/chain behavior and default diagnostic tolerance.
     * @return external, compressed contours and a 1-percent diagnostic approximation
     */
    public static ContourOptions defaults() { return new ContourOptions(null, null, 1); }
}
