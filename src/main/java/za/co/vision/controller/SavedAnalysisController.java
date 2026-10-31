package za.co.vision.controller;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletResponse;
import za.co.vision.model.ContourDiagnostics;
import za.co.vision.model.ContourMeasurement;
import za.co.vision.model.SavedAnalysis;
import za.co.vision.service.AnalysisStore;

/** Read-only HTML, JSON and ordinary image URLs for durable, browser-independent analyses. */
@Controller
public class SavedAnalysisController {
    private final AnalysisStore store;
    private record ContourView(ContourMeasurement measurement, ContourDiagnostics diagnostic,
                               String path, String vertices) { }

    /**
     * Supplies durable result access; reading never reruns OpenCV.
     * @param store atomic original/result storage
     */
    public SavedAnalysisController(AnalysisStore store) { this.store = store; }

    /**
     * Renders visible photographs, settings and measurement tables without requiring JavaScript.
     * Missing/pruned results render an explanatory HTML page with status 404.
     * @param id canonical UUID or latest
     * @param view photo, source, raw, closed, opened or mask; defaults to photo
     * @param contour optional retained contour ID to isolate
     * @param model server-rendered view model
     * @param response response receiving no-store and missing-result status
     * @return saved-result template
     * @throws IOException if stored data cannot be read
     */
    @GetMapping("/results/{id}")
    public String page(@PathVariable String id, @RequestParam(defaultValue = "photo") String view,
                       @RequestParam(required = false) Integer contour, Model model,
                       HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        var found = store.find(id);
        if (found.isEmpty()) {
            response.setStatus(404);
            return "saved-result";
        }
        var saved = found.orElseThrow();
        if (!List.of("photo", "source", "raw", "closed", "opened", "mask").contains(view)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown processing-stage view.");
        }
        var result = saved.result();
        if (contour != null && result.contours().stream().noneMatch(item -> item.id() == contour)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This result has no contour with that ID.");
        }
        var paths = result.contourDrawings().stream().collect(Collectors.toMap(item -> item.id(), item -> item.path()));
        var diagnostics = result.contourDiagnostics().stream().collect(Collectors.toMap(item -> item.id(), item -> item));
        var contours = result.contours().stream().filter(item -> contour == null || item.id() == contour)
                .map(item -> new ContourView(item, diagnostics.get(item.id()), paths.get(item.id()),
                        item.rotatedRectangle().vertices().stream().map(point -> point.x() + "," + point.y())
                                .collect(Collectors.joining(" ")))).toList();
        model.addAttribute("saved", saved);
        model.addAttribute("result", result);
        model.addAttribute("contours", contours);
        model.addAttribute("view", view);
        model.addAttribute("selected", contour);
        model.addAttribute("baseImage", switch (view) {
            case "photo" -> "edges";
            case "source" -> "original";
            default -> view;
        });
        return "saved-result";
    }

    /**
     * Retrieves the exact saved response for dashboard restoration in another browser.
     * @param id canonical UUID or latest
     * @return metadata and complete original-resolution analysis with bounded inline previews
     * @throws IOException if reading fails
     * @throws ResponseStatusException with 404 when absent
     */
    @GetMapping("/api/vision/results/{id}")
    @ResponseBody
    public ResponseEntity<SavedAnalysis> analysis(@PathVariable String id) throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(require(id));
    }

    /**
     * Exposes compact measurements and ordinary image links for readers such as ChatGPT.
     * This representation omits base64 image payloads and display paths.
     * @param id canonical UUID or latest
     * @return metadata, actual parameters, contour measurements, diagnostics and stage-image URLs
     * @throws IOException if reading fails
     * @throws ResponseStatusException with 404 when absent
     */
    @GetMapping("/api/vision/results/{id}/measurements")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> measurements(@PathVariable String id) throws IOException {
        var saved = require(id);
        var result = saved.result();
        String base = "/results/" + saved.id();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", saved.id()); body.put("savedAt", saved.savedAt()); body.put("url", base);
        body.put("width", result.width()); body.put("height", result.height());
        body.put("contourSource", result.contourSource()); body.put("parameters", result.parameters());
        body.put("contourCount", result.contourCount()); body.put("detectedContourCount", result.detectedContourCount());
        body.put("edgePixelCount", result.edgePixelCount()); body.put("boundaryPixelCount", result.boundaryPixelCount());
        body.put("contours", result.contours());
        body.put("diagnostics", result.contourDiagnostics().stream().map(SavedAnalysisController::diagnosticSummary).toList());
        body.put("images", Map.of("original", base + "/images/original", "overlay", base + "/images/overlay", "mask", base + "/images/mask"));
        body.put("stages", result.stages().stream().map(stage -> Map.of("id", stage.id(), "label", stage.label(),
                "pixelCount", stage.pixelCount(), "image", base + "/images/" + stage.id())).toList());
        body.put("originalPhoto", base + "/original");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /**
     * Returns a bounded preview as an ordinary decodable JPEG/PNG response, never a browser Blob URL.
     * @param id canonical UUID or latest
     * @param kind original, edges, contours, overlay, mask, raw, closed or opened
     * @return image bytes with their exact MIME type and no-store policy
     * @throws IOException if reading fails
     * @throws ResponseStatusException with 404 for unknown versions/images
     */
    @GetMapping("/results/{id}/images/{kind}")
    @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable String id, @PathVariable String kind) throws IOException {
        var result = require(id).result();
        String data = switch (kind) {
            case "original" -> result.originalImage();
            case "edges" -> result.edgesImage();
            case "contours" -> result.contoursImage();
            case "overlay" -> result.overlayImage();
            case "mask" -> result.boundaryMaskImage();
            default -> result.stages().stream().filter(stage -> stage.id().equals(kind))
                    .findFirst().orElseThrow(() -> missing("Unknown processing image.")).image();
        };
        boolean jpeg = data.startsWith("data:image/jpeg;base64,");
        if (!jpeg && !data.startsWith("data:image/png;base64,")) throw new IOException("Invalid saved image encoding.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(jpeg ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG)
                .body(Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1)));
    }

    /**
     * Retrieves the exact accepted original, enabling reprocessing without another local upload.
     * @param id canonical UUID or latest
     * @return original JPEG/PNG bytes
     * @throws IOException if reading fails
     * @throws ResponseStatusException with 404 when absent
     */
    @GetMapping("/results/{id}/original")
    @ResponseBody
    public ResponseEntity<byte[]> original(@PathVariable String id) throws IOException {
        var photo = store.original(id).orElseThrow(() -> missing("No saved photograph is available."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(photo.mediaType())).body(photo.bytes());
    }

    private SavedAnalysis require(String id) throws IOException {
        return store.find(id).orElseThrow(() -> missing("No saved analysis is available. Process a photograph first."));
    }
    private static Map<String, Object> diagnosticSummary(ContourDiagnostics d) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", d.id()); summary.put("sourceIndex", d.sourceIndex());
        summary.put("nextIndex", d.nextIndex()); summary.put("previousIndex", d.previousIndex());
        summary.put("firstChildIndex", d.firstChildIndex()); summary.put("parentIndex", d.parentIndex());
        summary.put("depth", d.depth()); summary.put("hole", d.hole()); summary.put("pointCount", d.pointCount());
        summary.put("approximationVertices", d.approximationVertices()); summary.put("approximationEpsilon", d.approximationEpsilon());
        summary.put("approximationArea", d.approximationArea()); summary.put("approximationPerimeter", d.approximationPerimeter());
        summary.put("rotatedRectangleArea", d.rotatedRectangleArea()); summary.put("rotatedExtent", d.rotatedExtent());
        return summary;
    }
    private static ResponseStatusException missing(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
