package za.co.vision.model;

/**
 * Trace diagnostics for a retained contour. Hierarchy references are zero-based extraction indices,
 * including filtered-out traces, never result IDs. A closed OpenCV polygon does not establish
 * that the photographed object's boundary is complete. TREE areas do not subtract hole areas.
 * @param id retained result ID, matching measurements and overlays
 * @param sourceIndex zero-based index in this request's complete extraction result
 * @param nextIndex next sibling, or -1
 * @param previousIndex previous sibling, or -1
 * @param firstChildIndex first child, or -1
 * @param parentIndex parent, or -1
 * @param depth nesting depth from an outermost trace
 * @param hole odd nesting depth; a topological interpretation of this mask only
 * @param pointCount stored boundary points before diagnostic approximation
 * @param approximationVertices vertices after approxPolyDP with closed=true
 * @param approximationEpsilon source-pixel tolerance, percentage times closed perimeter
 * @param approximationArea closed approximation polygon area in px²
 * @param approximationPerimeter closed approximation perimeter in px
 * @param rotatedRectangleArea long side times short side in px²
 * @param rotatedExtent contour polygon area divided by rotated rectangle area; null for zero-area box
 * @param approximationPath closed SVG path in original coordinates, independent of preview resolution
 */
public record ContourDiagnostics(int id, int sourceIndex, int nextIndex, int previousIndex,
                                 int firstChildIndex, int parentIndex, int depth, boolean hole,
                                 int pointCount, int approximationVertices, double approximationEpsilon,
                                 double approximationArea, double approximationPerimeter,
                                 double rotatedRectangleArea, Double rotatedExtent, String approximationPath) { }
