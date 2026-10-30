package za.co.vision.service;

import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import za.co.vision.config.OpenCvRuntime;
import za.co.vision.config.VisionProperties;
import za.co.vision.model.*;
import static org.junit.jupiter.api.Assertions.*;

class ContourBoundaryTests {
    private static VisionProcessingService service;

    @BeforeAll
    static void load() {
        service = new VisionProcessingService(new VisionProperties(50, 150, 100, 5,
                10_485_760, 16_000_000, 2000, 2, 1600, 88), new OpenCvRuntime());
    }

    private static BoundaryOptions options(BoundaryOptions.Mode mode, int close, int open) {
        return new BoundaryOptions(mode, close, open, BoundaryOptions.Foreground.BRIGHT);
    }

    @Test
    void closingReconnectsSmallGapsAndCanMergeNearbyObjects() {
        try (MatScope scope = new MatScope()) {
            Mat edges = scope.own(Mat.zeros(80, 100, CvType.CV_8UC1));
            Imgproc.rectangle(edges, new Point(10, 20), new Point(40, 60), Scalar.all(255), -1);
            Imgproc.rectangle(edges, new Point(44, 20), new Point(80, 60), Scalar.all(255), -1);
            Mat unchanged = ContourBoundaries.extract(scope, edges, edges, options(BoundaryOptions.Mode.MORPHOLOGY, 1, 1));
            Mat small = ContourBoundaries.extract(scope, edges, edges, options(BoundaryOptions.Mode.MORPHOLOGY, 3, 1));
            Mat large = ContourBoundaries.extract(scope, edges, edges, options(BoundaryOptions.Mode.MORPHOLOGY, 7, 1));
            assertEquals(0, Core.norm(edges, unchanged, Core.NORM_INF));
            assertEquals(2, components(scope, small));
            assertEquals(1, components(scope, large), "Closing can also merge separate objects: this is an experiment.");
            assertEquals(0, edges.get(40, 42)[0], "The raw mask must stay untouched.");
            assertEquals(255, large.get(40, 42)[0]);
        }
    }

    @Test
    void openingRemovesNoiseButAlsoErasesThinCannyBoundaries() {
        try (MatScope scope = new MatScope()) {
            Mat edges = scope.own(Mat.zeros(100, 100, CvType.CV_8UC1));
            Imgproc.rectangle(edges, new Point(20, 20), new Point(80, 80), Scalar.all(255), 1);
            edges.put(5, 5, 255);
            Mat raw = ContourBoundaries.extract(scope, edges, edges, options(BoundaryOptions.Mode.CANNY, 15, 15));
            Mat opened = ContourBoundaries.extract(scope, edges, edges, options(BoundaryOptions.Mode.MORPHOLOGY, 1, 3));
            assertEquals(241, Core.countNonZero(raw));
            assertEquals(0, Core.countNonZero(opened));
            assertEquals(241, Core.countNonZero(edges));
        }
    }

    @Test
    void experimentsKeepRawEdgesAndReportTheActiveMaskAndCounts() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(Mat.zeros(200, 300, CvType.CV_8UC3));
            Imgproc.rectangle(image, new Point(30, 30), new Point(130, 150), Scalar.all(255), -1);
            Imgproc.circle(image, new Point(220, 100), 35, Scalar.all(255), -1);
            byte[] bytes = encode(scope, image);
            var raw = service.process(bytes, new ProcessingParameters(50, 150, 0, 1));
            var morph = service.process(bytes, new ProcessingParameters(50, 150, 0, 1,
                    ContourFilters.none(), options(BoundaryOptions.Mode.MORPHOLOGY, 3, 1)));
            var regions = service.process(bytes, new ProcessingParameters(50, 150, 0, 1,
                    ContourFilters.none(), options(BoundaryOptions.Mode.SEGMENTATION, 1, 1)));
            assertEquals(2, regions.detectedContourCount());
            assertEquals(2, regions.contourCount());
            for (var result : new ProcessingResult[]{raw, morph, regions}) {
                assertEquals(raw.edgePixelCount(), result.edgePixelCount());
                assertEquals(raw.edgesImage(), result.edgesImage());
                assertEquals(result.contourCount(), result.contourDrawings().size());
                assertTrue(result.boundaryMaskImage().startsWith("data:image/png;base64,"));
                MatOfByte encoded = scope.own(new MatOfByte(Base64.getDecoder().decode(result.boundaryMaskImage().split(",")[1])));
                Mat mask = scope.own(Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_GRAYSCALE));
                assertEquals(result.boundaryPixelCount(), Core.countNonZero(mask));
            }
            assertEquals(raw.edgePixelCount(), raw.boundaryPixelCount());
            assertTrue(regions.boundaryPixelCount() > raw.edgePixelCount() * 10);
        }
    }

    @Test
    void darkSegmentationAndOptionalBorderFilterUseActualRegionMeasurements() {
        try (MatScope scope = new MatScope()) {
            Mat image = scope.own(new Mat(160, 240, CvType.CV_8UC3, Scalar.all(255)));
            Imgproc.rectangle(image, new Point(0, 20), new Point(60, 120), Scalar.all(0), -1);
            Imgproc.circle(image, new Point(170, 80), 30, Scalar.all(0), -1);
            byte[] bytes = encode(scope, image);
            var options = new BoundaryOptions(BoundaryOptions.Mode.SEGMENTATION, 1, 1, BoundaryOptions.Foreground.DARK);
            var retained = service.process(bytes, new ProcessingParameters(50, 150, 0, 1, ContourFilters.none(), options));
            var filtered = service.process(bytes, new ProcessingParameters(50, 150, 0, 1,
                    new ContourFilters(null, null, null, 0, 0, true), options));
            assertEquals(2, retained.contourCount());
            assertTrue(retained.contours().stream().anyMatch(ContourMeasurement::touchesImageBoundary));
            assertEquals(2, filtered.detectedContourCount());
            assertEquals(1, filtered.contourCount());
            assertFalse(filtered.contours().getFirst().touchesImageBoundary());
        }
    }

    @Test
    void invalidExperimentKernelsAreRejected() {
        for (int kernel : new int[]{-1, 0, 2, 16, 17}) {
            assertThrows(IllegalArgumentException.class, () -> options(BoundaryOptions.Mode.MORPHOLOGY, kernel, 1));
            assertThrows(IllegalArgumentException.class, () -> options(BoundaryOptions.Mode.SEGMENTATION, 1, kernel));
        }
        assertEquals(BoundaryOptions.Mode.CANNY, BoundaryOptions.raw().mode());
    }

    private static int components(MatScope scope, Mat mask) {
        return Imgproc.connectedComponents(mask, scope.own(new Mat())) - 1;
    }

    private static byte[] encode(MatScope scope, Mat image) {
        MatOfByte bytes = scope.own(new MatOfByte());
        assertTrue(Imgcodecs.imencode(".png", image, bytes));
        return bytes.toArray();
    }
}
