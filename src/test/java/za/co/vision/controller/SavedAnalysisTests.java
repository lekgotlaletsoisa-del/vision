package za.co.vision.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import za.co.vision.service.AnalysisStore;
import za.co.vision.model.ProcessingResult;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "vision.results.directory=${java.io.tmpdir}/vision-tests-${random.uuid}")
@AutoConfigureMockMvc
class SavedAnalysisTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @TempDir Path directory;

    @Test
    void processedResultHasReadableHtmlJsonAndOrdinaryImagesWithoutCookies() throws Exception {
        byte[] photo = image();
        var processed = mvc.perform(multipart("/api/vision/process").file(new MockMultipartFile("image", "rectangle.png", "image/png", photo)))
                .andExpect(status().isOk()).andExpect(header().string("Location", startsWith("/results/"))).andReturn();
        String location = processed.getResponse().getHeader("Location");
        String id = location.substring("/results/".length());
        var result = mapper.readValue(processed.getResponse().getContentAsByteArray(), ProcessingResult.class);
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Actual processing settings")))
                .andExpect(content().string(containsString("Contour measurements")))
                .andExpect(content().string(containsString("#1")))
                .andExpect(content().string(containsString("/images/raw")))
                .andExpect(content().string(not(containsString("data:image/"))))
                .andExpect(content().string(not(containsString("<script"))));
        mvc.perform(get("/api/vision/results/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.contours[0].area").value(result.contours().getFirst().area()));
        mvc.perform(get("/api/vision/results/" + id + "/measurements")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contourSource").value("raw"))
                .andExpect(jsonPath("$.stages", hasSize(3)))
                .andExpect(content().string(not(containsString("base64"))));
        mvc.perform(get(location + "/images/original")).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        mvc.perform(get(location + "/images/raw")).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get(location + "/original")).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(photo));
        mvc.perform(get(location).param("view", "closed").param("contour", "1")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/images/closed")));
        mvc.perform(get(location).param("view", "nonsense")).andExpect(status().isBadRequest());
        mvc.perform(get(location).param("contour", "9999")).andExpect(status().isNotFound());
        mvc.perform(get(location + "/images/missing")).andExpect(status().isNotFound());
    }

    @Test
    void emptyUnknownAndInvalidIdsAre404AndFailedProcessingPreservesLatest() throws Exception {
        var emptyStore = new AnalysisStore(directory, 20, mapper);
        assertTrue(emptyStore.find("latest").isEmpty());
        assertTrue(emptyStore.find("../outside").isEmpty());
        mvc.perform(get("/results/00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound())
                .andExpect(content().string(containsString("No saved analysis available")));
        mvc.perform(get("/api/vision/results/not-a-uuid")).andExpect(status().isNotFound());
        var created = mvc.perform(multipart("/api/vision/process").file(new MockMultipartFile("image", image())))
                .andExpect(status().isOk()).andReturn();
        String id = created.getResponse().getHeader("Location").substring("/results/".length());
        mvc.perform(multipart("/api/vision/process").file(new MockMultipartFile("image", new byte[]{1, 2, 3})))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/vision/results/latest")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void versionsSurviveRestartRemainImmutableAndPruneOnlyCompletedOwnedVersions() throws Exception {
        var created = mvc.perform(multipart("/api/vision/process").file(new MockMultipartFile("image", image()))).andReturn();
        var result = mapper.readValue(created.getResponse().getContentAsByteArray(), ProcessingResult.class);
        var store = new AnalysisStore(directory, 2, mapper);
        Files.writeString(directory.resolve("unrelated.txt"), "preserve me");
        Files.createDirectories(directory.resolve(".pending-incomplete"));
        var first = store.save(image(), result);
        var second = store.save(image(), result);
        var reopened = new AnalysisStore(directory, 2, mapper);
        assertEquals(second, reopened.find("latest").orElseThrow());
        assertEquals(first, reopened.find(first.id()).orElseThrow());
        assertArrayEquals(image(), reopened.original(first.id()).orElseThrow().bytes());
        var third = reopened.save(image(), result);
        assertEquals(third, reopened.find("latest").orElseThrow());
        assertTrue(reopened.find(first.id()).isEmpty());
        assertEquals(second, reopened.find(second.id()).orElseThrow());
        assertTrue(Files.exists(directory.resolve("unrelated.txt")));
        assertTrue(Files.isDirectory(directory.resolve(".pending-incomplete")));
    }

    @Test
    void failedPublicationLeavesThePreviouslyPublishedVersionIntact() throws Exception {
        var created = mvc.perform(multipart("/api/vision/process").file(new MockMultipartFile("image", image()))).andReturn();
        var result = mapper.readValue(created.getResponse().getContentAsByteArray(), ProcessingResult.class);
        Path root = directory.resolve("store");
        var store = new AnalysisStore(root, 20, mapper);
        var previous = store.save(image(), result);
        Path offline = directory.resolve("offline");
        Files.move(root, offline);
        Files.writeString(root, "Storage is temporarily unavailable.");
        assertThrows(java.io.IOException.class, () -> store.save(image(), result));
        Files.delete(root);
        Files.move(offline, root);
        assertEquals(previous, store.find("latest").orElseThrow());
        assertArrayEquals(image(), store.original(previous.id()).orElseThrow().bytes());
    }

    @Test
    void concurrentSavesKeepMatchingPhotosAndResultsAndAnInterruptedWriteDoesNotReplaceLatest() throws Exception {
        var created = mvc.perform(multipart("/api/vision/process").file(new MockMultipartFile("image", image()))).andReturn();
        var result = mapper.readValue(created.getResponse().getContentAsByteArray(), ProcessingResult.class);
        var store = new AnalysisStore(directory, 20, mapper);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var tasks = List.<Callable<String>>of(() -> store.save(new byte[]{(byte) 0x89, 1}, result).id(),
                    () -> store.save(new byte[]{(byte) 0x89, 2}, result).id(),
                    () -> store.save(new byte[]{(byte) 0x89, 3}, result).id());
            var ids = executor.invokeAll(tasks);
            for (int i = 0; i < ids.size(); i++) {
                var id = ids.get(i).get();
                assertEquals(result, store.find(id).orElseThrow().result());
                assertEquals(i + 1, store.original(id).orElseThrow().bytes()[1]);
            }
        }
        var latest = store.find("latest").orElseThrow();
        Files.writeString(directory.resolve(".pending-aborted"), "partial upload");
        var restarted = new AnalysisStore(directory, 20, mapper);
        assertEquals(latest, restarted.find("latest").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new AnalysisStore(directory, 0, mapper));
    }

    @Test
    void preRefinementArchiveLoadsWithoutNewFieldsOrReprocessing() throws Exception {
        var created = mvc.perform(multipart("/api/vision/process").file(new MockMultipartFile("image", image()))).andReturn();
        var result = mapper.readValue(created.getResponse().getContentAsByteArray(), ProcessingResult.class);
        var store = new AnalysisStore(directory, 20, mapper);
        var saved = store.save(image(), result);
        Path file = directory.resolve(saved.id()).resolve("analysis.json");
        var tree = (tools.jackson.databind.node.ObjectNode) mapper.readTree(Files.readAllBytes(file));
        var oldResult = (tools.jackson.databind.node.ObjectNode) tree.get("result");
        oldResult.remove("edgeDiagnostics");
        ((tools.jackson.databind.node.ObjectNode) oldResult.get("parameters")).remove("detectionMode");
        Files.write(file, mapper.writeValueAsBytes(tree));
        var reopened = new AnalysisStore(directory, 20, mapper).find(saved.id()).orElseThrow();
        assertEquals(za.co.vision.model.DetectionMode.STANDARD, reopened.result().parameters().detectionMode());
        assertNull(reopened.result().edgeDiagnostics());
        assertEquals(result.contours(), reopened.result().contours());
        assertArrayEquals(image(), store.original(saved.id()).orElseThrow().bytes());
    }

    private static byte[] image() {
        Mat image = Mat.zeros(160, 220, CvType.CV_8UC3);
        MatOfByte bytes = new MatOfByte();
        try {
            Imgproc.rectangle(image, new Point(30, 30), new Point(160, 120), Scalar.all(255), -1);
            assertTrue(Imgcodecs.imencode(".png", image, bytes));
            return bytes.toArray();
        } finally { bytes.release(); image.release(); }
    }
}
