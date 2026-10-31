package za.co.vision.controller;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real embedded multipart parser, which MockMvc bypasses. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VisionHttpTests {
    @LocalServerPort
    private int port;

    @Test
    void allNineteenMultipartPartsFitTheBoundedParserBudget() throws Exception {
        var fields = new LinkedHashMap<String, String>();
        fields.put("cannyLow", "50"); fields.put("cannyHigh", "150");
        fields.put("minContourArea", "100"); fields.put("blurKernel", "1");
        fields.put("maxContourArea", "20000"); fields.put("minAspectRatio", "1");
        fields.put("maxAspectRatio", "3"); fields.put("minCircularity", "0");
        fields.put("minSolidity", "0"); fields.put("excludeBoundary", "true");
        fields.put("processingMode", "SEGMENTATION"); fields.put("closeKernel", "1");
        fields.put("openKernel", "1"); fields.put("foreground", "BRIGHT");
        fields.put("kernelShape", "RECTANGLE"); fields.put("retrievalMode", "TREE");
        fields.put("chainMode", "NONE"); fields.put("approximationPercent", "2");
        String boundary = "VisionSyntheticBoundary";
        var body = new ByteArrayOutputStream();
        for (var field : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                    + field.getKey() + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"synthetic.png\""
                + "\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        Mat image = Mat.zeros(180, 240, CvType.CV_8UC3);
        MatOfByte encoded = new MatOfByte();
        try {
            Imgproc.rectangle(image, new Point(30, 30), new Point(150, 110), Scalar.all(255), -1);
            assertTrue(Imgcodecs.imencode(".png", image, encoded));
            body.write(encoded.toArray());
        } finally { encoded.release(); image.release(); }
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/vision/process"))
                    .timeout(Duration.ofSeconds(20)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals("SEGMENTATION", JsonPath.read(response.body(), "$.parameters.boundary.mode"));
            assertEquals("TREE", JsonPath.read(response.body(), "$.parameters.tracing.retrieval"));
            assertEquals("RECTANGLE", JsonPath.read(response.body(), "$.parameters.boundary.shape"));
            assertEquals(1, (int) JsonPath.read(response.body(), "$.contourCount"));
            assertEquals(true, JsonPath.read(response.body(), "$.parameters.filters.excludeBoundary"));
        }
    }
}
