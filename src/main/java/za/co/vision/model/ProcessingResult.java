package za.co.vision.model;

import java.util.List;

/**
 * Browser-ready JSON payload with bounded previews and original-resolution pixel measurements.
 * @param width decoded image width, unchanged by processing
 * @param height decoded image height, unchanged by processing
 * @param previewWidth displayed preview width, possibly smaller than the decoded image
 * @param previewHeight displayed preview height, possibly smaller than the decoded image
 * @param contourCount retained external contours
 * @param detectedContourCount all external contours in the active extraction mask before filtering
 * @param edgePixelCount nonzero pixels in the Canny edge image
 * @param processingMillis elapsed processing time including decoding and preview encoding
 * @param parameters actual controls used for this result
 * @param originalImage normalized source photograph as a bounded JPEG preview data URL
 * @param edgesImage photograph preview with blue Canny edges and dark contrast outlines
 * @param contoursImage photograph preview with green retained contours and ID labels
 * @param overlayImage photograph preview with blue edges and green retained contours/IDs;
 *                     green contours take precedence where the two boundaries overlap
 * @param contours geometry matching contour labels
 * @param contourDrawings display-only contour paths generated in Java, in original coordinates
 * @param boundaryPixelCount nonzero pixels in the active extraction mask before filtering, at source resolution
 * @param boundaryMaskImage bounded, undilated binary extraction-mask preview as a PNG data URL
 */
public record ProcessingResult(int width, int height, int previewWidth, int previewHeight,
                               int contourCount, int detectedContourCount,
                               int edgePixelCount, double processingMillis,
                               ProcessingParameters parameters, String originalImage,
                               String edgesImage, String contoursImage, String overlayImage,
                               List<ContourMeasurement> contours, List<ContourDrawing> contourDrawings,
                               int boundaryPixelCount, String boundaryMaskImage) {
    /** Makes the geometry collection immutable. */
    public ProcessingResult {
        contours = List.copyOf(contours);
        contourDrawings = List.copyOf(contourDrawings);
    }
}
