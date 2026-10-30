package za.co.vision.service;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Mat;

/** Deterministic ownership for request-local OpenCV matrices, including exceptional paths. */
final class MatScope implements AutoCloseable {
    private final List<Mat> matrices = new ArrayList<>();

    <T extends Mat> T own(T matrix) {
        matrices.add(matrix);
        return matrix;
    }

    @Override
    public void close() {
        for (int i = matrices.size() - 1; i >= 0; i--) {
            matrices.get(i).release();
        }
    }
}
