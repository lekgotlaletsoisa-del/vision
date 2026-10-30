package za.co.vision.service;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import za.co.vision.model.BoundaryOptions;

/** Generates an independently owned extraction mask without modifying raw Canny edges. */
final class ContourBoundaries {
    private ContourBoundaries() { }

    static Mat extract(MatScope scope, Mat blurredGray, Mat rawEdges, BoundaryOptions options) {
        Mat mask = scope.own(rawEdges.clone());
        if (options.mode() == BoundaryOptions.Mode.CANNY) return mask;
        if (options.mode() == BoundaryOptions.Mode.SEGMENTATION) {
            int polarity = options.foreground() == BoundaryOptions.Foreground.BRIGHT
                    ? Imgproc.THRESH_BINARY : Imgproc.THRESH_BINARY_INV;
            Imgproc.threshold(blurredGray, mask, 0, 255, polarity | Imgproc.THRESH_OTSU);
        }
        // One pass, small bounded kernels: experiments remain conservative and explicit.
        transform(scope, mask, Imgproc.MORPH_CLOSE, options.closeKernel());
        transform(scope, mask, Imgproc.MORPH_OPEN, options.openKernel());
        return mask;
    }

    private static void transform(MatScope scope, Mat mask, int operation, int size) {
        if (size == 1) return;
        Mat kernel = scope.own(Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(size, size)));
        Imgproc.morphologyEx(mask, mask, operation, kernel);
    }
}
