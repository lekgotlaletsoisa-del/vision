package za.co.vision.service;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import za.co.vision.model.BoundaryOptions;

/** Generates masks without modifying the raw Canny input; disabled operations share their input. */
final class ContourBoundaries {
    private ContourBoundaries() { }

    record Stages(Mat closed, Mat opened, Mat active, String source) { }

    static Mat extract(MatScope scope, Mat blurredGray, Mat rawEdges, BoundaryOptions options) {
        return stages(scope, blurredGray, rawEdges, options).active();
    }

    static Stages stages(MatScope scope, Mat blurredGray, Mat rawEdges, BoundaryOptions options) {
        Mat closed = transform(scope, rawEdges, Imgproc.MORPH_CLOSE, options.closeKernel(), options.shape());
        Mat opened = transform(scope, closed, Imgproc.MORPH_OPEN, options.openKernel(), options.shape());
        return switch (options.mode()) {
            case CANNY -> new Stages(closed, opened, rawEdges, "raw");
            case CANNY_CLOSED -> new Stages(closed, opened, closed, "closed");
            case MORPHOLOGY -> new Stages(closed, opened, opened, "opened");
            case SEGMENTATION -> {
                Mat regions = scope.own(new Mat());
                int polarity = options.foreground() == BoundaryOptions.Foreground.BRIGHT
                        ? Imgproc.THRESH_BINARY : Imgproc.THRESH_BINARY_INV;
                Imgproc.threshold(blurredGray, regions, 0, 255, polarity | Imgproc.THRESH_OTSU);
                Mat regionClosed = transform(scope, regions, Imgproc.MORPH_CLOSE, options.closeKernel(), options.shape());
                yield new Stages(closed, opened,
                        transform(scope, regionClosed, Imgproc.MORPH_OPEN, options.openKernel(), options.shape()), "segmentation");
            }
        };
    }

    private static Mat transform(MatScope scope, Mat input, int operation, int size, BoundaryOptions.Shape shape) {
        if (size == 1) return input;
        Mat output = scope.own(new Mat());
        Mat kernel = scope.own(Imgproc.getStructuringElement(
                shape == BoundaryOptions.Shape.RECTANGLE ? Imgproc.MORPH_RECT : Imgproc.MORPH_ELLIPSE,
                new Size(size, size)));
        if (operation == Imgproc.MORPH_OPEN) {
            // Explicit erosion then dilation is opening. One iteration, same kernel and default border policy.
            Imgproc.erode(input, output, kernel);
            Imgproc.dilate(output, output, kernel);
        } else {
            Imgproc.morphologyEx(input, output, operation, kernel);
        }
        return output;
    }
}
