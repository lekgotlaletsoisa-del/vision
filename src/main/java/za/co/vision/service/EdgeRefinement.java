package za.co.vision.service;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.imgproc.Imgproc;
import za.co.vision.model.BoundaryOptions;
import za.co.vision.model.DetectionMode;
import za.co.vision.model.EdgeDiagnostics;
import za.co.vision.model.ProcessingParameters;

/** Bounded, deterministic edge heuristics; scores describe trace continuity, never object accuracy. */
final class EdgeRefinement {
    private EdgeRefinement() { }

    record Selection(Mat edges, double low, double high, double minimumLength,
                     double minimumSpan, List<EdgeDiagnostics.Trial> trials) { }
    record Repair(boolean applied, String reason) { }
    private record Summary(int count, int pixels, double sustained, double shortLength, boolean withinBudget) {
        double score() { return sustained - 2 * shortLength; }
    }

    static Selection select(MatScope scope, Mat blurred, ProcessingParameters parameters, int maxContours) {
        Mat baseline = scope.own(new Mat());
        Imgproc.Canny(blurred, baseline, parameters.cannyLow(), parameters.cannyHigh(), 3, true);
        if (parameters.detectionMode() == DetectionMode.STANDARD) {
            return new Selection(baseline, parameters.cannyLow(), parameters.cannyHigh(), 0, 0, List.of());
        }
        double length = Math.max(40, .04 * Math.max(blurred.cols(), blurred.rows()));
        double span = length / 2;
        Summary initial = summarize(baseline, length, span, maxContours);
        Summary best = initial;
        Mat selected = baseline;
        double selectedLow = parameters.cannyLow(), selectedHigh = parameters.cannyHigh();
        List<EdgeDiagnostics.Trial> trials = new ArrayList<>();
        boolean bestEligible = initial.withinBudget() && initial.pixels() <= blurred.total() * .10;
        trials.add(trial(parameters.cannyLow(), parameters.cannyHigh(), initial, bestEligible));
        try (MatScope temporary = new MatScope()) {
            Mat candidate = temporary.own(new Mat());
            Mat common = temporary.own(new Mat());
            for (double factor : new double[]{.7, .5, 1.4}) {
                double high = Math.min(255, parameters.cannyHigh() * factor);
                double low = Math.min(high * .999999, parameters.cannyLow() * factor);
                if (high <= 0 || low >= high) continue;
                Imgproc.Canny(blurred, candidate, low, high, 3, true);
                Summary measured = summarize(candidate, length, span, maxContours);
                Core.bitwise_and(baseline, candidate, common);
                // Preserve baseline detections, cap added edges, and penalize fragmented noise.
                boolean eligible = measured.withinBudget() && measured.pixels() <= blurred.total() * .10
                        && Core.countNonZero(common) >= initial.pixels() * .95
                        && measured.pixels() <= Math.max(initial.pixels() * 1.8, initial.pixels() + blurred.total() * .005)
                        && measured.sustained() >= initial.sustained() * .98
                        && measured.shortLength() <= Math.max(40, Math.max(initial.shortLength() * 1.5, measured.sustained() * .25));
                trials.add(trial(low, high, measured, eligible));
                // Require a material gain; ties keep the earlier, less sensitive setting.
                if (eligible && (!bestEligible || measured.score() > best.score() + Math.max(20, Math.abs(best.score()) * .05))) {
                    if (selected == baseline) selected = scope.own(new Mat());
                    candidate.copyTo(selected);
                    best = measured;
                    bestEligible = true;
                    selectedLow = low;
                    selectedHigh = high;
                }
            }
        }
        return new Selection(selected, selectedLow, selectedHigh, length, span, List.copyOf(trials));
    }

    static Repair repair(Mat raw, Mat closed, ProcessingParameters parameters, int maxContours,
                         double length, double span) {
        var boundary = parameters.boundary();
        if (parameters.detectionMode() != DetectionMode.ENHANCED) return new Repair(false, "Automatic repair disabled (STANDARD).");
        if (boundary.mode() != BoundaryOptions.Mode.CANNY) return new Repair(false, "Explicit contour source retained.");
        if (boundary.closeKernel() != 3 || boundary.openKernel() != 1) {
            return new Repair(false, "Automatic repair requires kernel 3, opening disabled; explicit kernels retained as previews.");
        }
        Summary before = summarize(raw, length, span, maxContours);
        Summary after = summarize(closed, length, span, maxContours);
        int added = after.pixels() - before.pixels();
        if (!before.withinBudget() || !after.withinBudget()) return new Repair(false, "Trace complexity guard refused repair.");
        // Closing can seal one connected broken outline without merging separate components.
        // A count change is ambiguous (including newly enclosed nested traces), so refuse it.
        if (before.count() != after.count()) return new Repair(false, "External trace count changed; possible unrelated-boundary connection.");
        if (added <= 0) return new Repair(false, "No small gaps changed.");
        if (added > Math.max(8, before.pixels() * .08)) return new Repair(false, "Closing changed more than the small-gap pixel budget.");
        return new Repair(true, boundary.shape() + " 3 added " + added + " pixels within the 8%/8-pixel budget; external trace count unchanged. This does not prove object completeness.");
    }

    private static EdgeDiagnostics.Trial trial(double low, double high, Summary summary, boolean eligible) {
        return new EdgeDiagnostics.Trial(low, high, summary.pixels(), summary.sustained(), summary.shortLength(), eligible);
    }

    private static Summary summarize(Mat mask, double length, double span, int maxContours) {
        int pixels = Core.countNonZero(mask);
        try (MatScope scope = new MatScope()) {
            List<MatOfPoint> contours = new ArrayList<>();
            try {
                Imgproc.findContours(mask, contours, scope.own(new Mat()), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            } finally {
                contours.forEach(scope::own);
            }
            if (contours.size() > maxContours || contours.stream().mapToLong(Mat::total).sum() > 1_000_000) {
                return new Summary(contours.size(), pixels, 0, 0, false);
            }
            MatOfPoint2f points = scope.own(new MatOfPoint2f());
            double sustained = 0, shortLength = 0;
            for (MatOfPoint contour : contours) {
                points.fromArray(contour.toArray());
                double perimeter = Imgproc.arcLength(points, true);
                var bounds = Imgproc.boundingRect(contour);
                if (perimeter >= length && Math.hypot(bounds.width - 1.0, bounds.height - 1.0) >= span) sustained += perimeter;
                else shortLength += perimeter;
            }
            return new Summary(contours.size(), pixels, sustained, shortLength, true);
        }
    }
}
