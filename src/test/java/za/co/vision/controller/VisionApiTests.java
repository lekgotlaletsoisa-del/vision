package za.co.vision.controller;

import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "vision.results.directory=${java.io.tmpdir}/vision-tests-${random.uuid}")
@AutoConfigureMockMvc
class VisionApiTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void crawlerPolicyIsSuccessfulPlainTextForGetAndHead() throws Exception {
        mvc.perform(get("/robots.txt")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("User-agent: *\nAllow: /\n"));
        mvc.perform(head("/robots.txt")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    void missingResourcesReturnNotFoundRatherThanProcessingFailures() throws Exception {
        for (String path : new String[]{"/missing-page", "/missing-image.png", "/api/vision/missing"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("The requested resource was not found."));
        }
    }

    @Test
    void dashboardAndReadinessRenderWithLoadedNatives() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"))
                .andExpect(content().string(containsString("Vision Lab")))
                .andExpect(content().string(containsString("bootstrap-5.3.8.min.css")))
                .andExpect(content().string(containsString("data-max-pixels=\"16000000\"")));
        mvc.perform(get("/api/vision/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.opencvVersion").value(startsWith("4.14.")));
    }

    @Test
    void multipartUsesDefaultsAndReturnsStructuredGeometryAndImages() throws Exception {
        mvc.perform(multipart("/api/vision/process").file(image()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.contourCount").value(1))
                .andExpect(jsonPath("$.parameters.cannyLow").value(50))
                .andExpect(jsonPath("$.contours[0].area").isNumber())
                .andExpect(jsonPath("$.contours[0].rotatedRectangle.vertices", hasSize(4)))
                .andExpect(jsonPath("$.parameters.boundary.mode").value("CANNY"))
                .andExpect(jsonPath("$.parameters.filters.excludeBoundary").value(false))
                .andExpect(jsonPath("$.contours[0].aspectRatio").isNumber())
                .andExpect(jsonPath("$.contours[0].circularity").isNumber())
                .andExpect(jsonPath("$.contours[0].solidity").isNumber())
                .andExpect(jsonPath("$.contours[0].extent").isNumber())
                .andExpect(jsonPath("$.contours[0].touchesImageBoundary").value(false))
                .andExpect(jsonPath("$.contours[0].orientationAxis.start.x").isNumber())
                .andExpect(jsonPath("$.contourDrawings[0].path").value(startsWith("M")))
                .andExpect(jsonPath("$.boundaryMaskImage").value(startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.previewWidth").value(240))
                .andExpect(jsonPath("$.previewHeight").value(200))
                .andExpect(jsonPath("$.originalImage").value(startsWith("data:image/jpeg;base64,")))
                .andExpect(jsonPath("$.edgesImage").value(startsWith("data:image/jpeg;base64,")))
                .andExpect(jsonPath("$.contoursImage").value(startsWith("data:image/jpeg;base64,")))
                .andExpect(jsonPath("$.overlayImage").value(startsWith("data:image/jpeg;base64,")));
    }

    @Test
    void controlsOverrideDefaults() throws Exception {
        mvc.perform(multipart("/api/vision/process").file(image())
                        .param("cannyLow", "10").param("cannyHigh", "40")
                        .param("minContourArea", "100000").param("blurKernel", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contourCount").value(0))
                .andExpect(jsonPath("$.parameters.blurKernel").value(1))
                .andExpect(jsonPath("$.parameters.cannyLow").value(10));
    }

    @Test
    void emptyInvalidAndMissingPartsReturnSafeBadRequests() throws Exception {
        mvc.perform(multipart("/api/vision/process")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
        for (byte[] bytes : new byte[][]{new byte[0], "this is not an image".getBytes()}) {
            mvc.perform(multipart("/api/vision/process")
                            .file(new MockMultipartFile("image", "fake.png", "image/png", bytes)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("error"));
        }
    }

    @Test
    void numericBindingAndValidationErrorsAreJson() throws Exception {
        for (String[] control : new String[][]{{"cannyLow", "NaN"}, {"cannyHigh", "Infinity"},
                {"blurKernel", "4"}, {"blurKernel", "text"}, {"minContourArea", "-1"},
                {"cannyLow", "200"}, {"maxContourArea", "50"}, {"maxAspectRatio", "0.5"},
                {"minAspectRatio", "Infinity"}, {"minCircularity", "1.1"}, {"minSolidity", "NaN"},
                {"closeKernel", "4"}, {"openKernel", "17"}, {"processingMode", "YOLO"}, {"foreground", "RED"}, {"kernelShape", "CROSS"}, {"retrievalMode", "LIST"},
                {"chainMode", "FAST"}, {"approximationPercent", "NaN"}, {"approximationPercent", "11"}}) {
            mvc.perform(multipart("/api/vision/process").file(image()).param(control[0], control[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        }
    }

    @Test
    void optionalFilterAndExperimentControlsBindWithoutChangingLegacyFields() throws Exception {
        mvc.perform(multipart("/api/vision/process").file(image())
                        .param("processingMode", "SEGMENTATION").param("foreground", "BRIGHT")
                        .param("blurKernel", "1")
                        .param("closeKernel", "1").param("openKernel", "1")
                        .param("maxContourArea", "20000").param("minAspectRatio", "1.1")
                        .param("maxAspectRatio", "2").param("minCircularity", "0.5")
                        .param("minSolidity", "0.9").param("excludeBoundary", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contourCount").value(1))
                .andExpect(jsonPath("$.detectedContourCount").value(1))
                .andExpect(jsonPath("$.parameters.boundary.mode").value("SEGMENTATION"))
                .andExpect(jsonPath("$.parameters.filters.excludeBoundary").value(true))
                .andExpect(jsonPath("$.parameters.filters.maxAspectRatio").value(2))
                .andExpect(jsonPath("$.contours[0].area").value(9600));
    }

    @Test
    void dashboardContainsOnlyCaptureAndTwoDisplaySwitches() throws Exception {
        var response = mvc.perform(get("/")).andExpect(status().isOk());
        for (String id : new String[]{"camera-button", "show-edges", "show-contours", "geometry-overlay",
                "camera-dialog", "camera-video", "camera-shutter", "preview-image"}) {
            response.andExpect(content().string(containsString("id=\"" + id + "\"")));
        }
        var html = response.andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html)
                .containsOnlyOnce("id=\"show-edges\"").containsOnlyOnce("id=\"show-contours\"")
                .doesNotContain("<select", "<details", "<table", "type=\"file\"", "type=\"number\"",
                        "type=\"range\"", "type=\"submit\"", "show-boxes", "show-centroids", "show-ids",
                        "show-axes", "show-approximation", "load-server-result", "full-size-link");
        org.assertj.core.api.Assertions.assertThat(html.split("type=\"checkbox\"", -1)).hasSize(3);
    }

    @Test
    void closedSourceAndTracingOptionsReturnStageMasksAndMatchingDiagnostics() throws Exception {
        mvc.perform(multipart("/api/vision/process").file(image())
                        .param("processingMode", "CANNY_CLOSED").param("kernelShape", "RECTANGLE")
                        .param("retrievalMode", "TREE").param("chainMode", "NONE")
                        .param("approximationPercent", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contourSource").value("closed"))
                .andExpect(jsonPath("$.stages", hasSize(3)))
                .andExpect(jsonPath("$.stages[0].id").value("raw"))
                .andExpect(jsonPath("$.stages[1].image").value(startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.parameters.tracing.chain").value("NONE"))
                .andExpect(jsonPath("$.contourDiagnostics[0].id").value(1))
                .andExpect(jsonPath("$.contourDiagnostics[0].rotatedExtent").isNumber())
                .andExpect(jsonPath("$.contourDiagnostics[0].approximationVertices").value(4));
    }

    @Test
    void rejectsWrongContentTypeAndOversizedImage() throws Exception {
        mvc.perform(post("/api/vision/process").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.message").isString());
        mvc.perform(multipart("/api/vision/process").file(new MockMultipartFile(
                        "image", "large.png", "image/png", new byte[10_485_761])))
                .andExpect(status().isContentTooLarge()).andExpect(jsonPath("$.message").isString());
    }

    private static MockMultipartFile image() {
        Mat image = Mat.zeros(200, 240, CvType.CV_8UC3);
        MatOfByte bytes = new MatOfByte();
        try {
            Imgproc.rectangle(image, new Point(40, 50), new Point(160, 130), Scalar.all(255), -1);
            Imgcodecs.imencode(".png", image, bytes);
            return new MockMultipartFile("image", "rectangle.png", "image/png", bytes.toArray());
        } finally {
            bytes.release();
            image.release();
        }
    }
}
