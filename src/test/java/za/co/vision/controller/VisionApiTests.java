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

@SpringBootTest
@AutoConfigureMockMvc
class VisionApiTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void dashboardAndReadinessRenderWithLoadedNatives() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"))
                .andExpect(content().string(containsString("Vision Lab")))
                .andExpect(content().string(containsString("bootstrap-5.3.8.min.css")))
                .andExpect(content().string(containsString("value=\"50.0\"")));
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
                {"cannyLow", "200"}}) {
            mvc.perform(multipart("/api/vision/process").file(image()).param(control[0], control[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        }
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
