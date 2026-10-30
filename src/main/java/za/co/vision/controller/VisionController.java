package za.co.vision.controller;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import za.co.vision.config.OpenCvRuntime;
import za.co.vision.config.VisionProperties;
import za.co.vision.model.ProcessingParameters;
import za.co.vision.model.ProcessingResult;
import za.co.vision.service.VisionException;
import za.co.vision.service.VisionProcessingService;

/** Thin MVC adapter for the dashboard, readiness and multipart processing API. */
@Controller
public class VisionController {
    private final VisionProcessingService service;
    private final VisionProperties properties;
    private final OpenCvRuntime runtime;

    /**
     * Supplies the processing and presentation dependencies.
     * @param service Java OpenCV pipeline
     * @param properties validated defaults and resource limits
     * @param runtime loaded native runtime
     */
    public VisionController(VisionProcessingService service, VisionProperties properties, OpenCvRuntime runtime) {
        this.service = service;
        this.properties = properties;
        this.runtime = runtime;
    }

    /**
     * Renders the laboratory with configuration-driven controls.
     * @param model Thymeleaf model
     * @return dashboard template name
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("defaults", properties.defaults());
        model.addAttribute("limits", properties);
        return "index";
    }

    /**
     * Reports readiness after native initialization has succeeded.
     * @return readiness and the successfully loaded native version
     */
    @GetMapping("/api/vision/health")
    @ResponseBody
    public Map<String, String> health() {
        return Map.of("status", "ready", "opencvVersion", runtime.version());
    }

    /**
     * Processes one image; omitted parameters use application defaults.
     * @param image required encoded JPEG/PNG multipart part
     * @param cannyLow optional lower Canny threshold
     * @param cannyHigh optional upper Canny threshold
     * @param minContourArea optional minimum polygon area in px²
     * @param blurKernel optional odd Gaussian kernel side length
     * @return non-cacheable JSON images and pixel measurements
     * @throws IOException when the multipart image cannot be read
     * @throws IllegalArgumentException when processing parameters are invalid
     * @throws VisionException when image validation or processing budgets refuse the request
     */
    @PostMapping(value = "/api/vision/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ProcessingResult> process(@RequestParam("image") MultipartFile image,
            @RequestParam(required = false) Double cannyLow,
            @RequestParam(required = false) Double cannyHigh,
            @RequestParam(required = false) Double minContourArea,
            @RequestParam(required = false) Integer blurKernel) throws IOException {
        var defaults = properties.defaults();
        var parameters = new ProcessingParameters(
                cannyLow == null ? defaults.cannyLow() : cannyLow,
                cannyHigh == null ? defaults.cannyHigh() : cannyHigh,
                minContourArea == null ? defaults.minContourArea() : minContourArea,
                blurKernel == null ? defaults.blurKernel() : blurKernel);
        if (image.getSize() > properties.maxUploadBytes()) {
            throw new VisionException(VisionException.Reason.TOO_LARGE, "Image exceeds the configured upload limit.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.process(image.getBytes(), parameters));
    }
}
