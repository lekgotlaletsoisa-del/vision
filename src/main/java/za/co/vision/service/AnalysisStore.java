package za.co.vision.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import za.co.vision.model.ProcessingResult;
import za.co.vision.model.SavedAnalysis;

/**
 * Disk-backed successful analyses shared across browser profiles. A complete original/result pair
 * becomes visible only after its staging directory is renamed. Published versions are immutable;
 * the latest alias resolves to the most recently completed save. Access is serialized within this JVM.
 * The configured retention limit applies to completed versions, not unrelated files.
 */
@Service
public class AnalysisStore {
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisStore.class);
    private static final long MAX_JSON_BYTES = 32L * 1024 * 1024;
    private final Path directory;
    private final int retained;
    private final JsonMapper mapper;

    /**
     * Creates or reopens the durable result directory; existing completed versions are preserved.
     * @param directory absolute or working-directory-relative result directory
     * @param retained maximum completed versions to keep, from 1 to 100
     * @param mapper application-configured JSON mapper shared with the HTTP API
     * @throws IOException if the result directory cannot be created or accessed
     * @throws IllegalArgumentException for an invalid retention limit
     */
    public AnalysisStore(@Value("${vision.results.directory:${user.home}/.local/share/vision/results}") Path directory,
                         @Value("${vision.results.retained:20}") int retained, JsonMapper mapper) throws IOException {
        if (retained < 1 || retained > 100) throw new IllegalArgumentException("Result retention must be between 1 and 100.");
        this.directory = directory.toAbsolutePath().normalize();
        this.retained = retained;
        this.mapper = mapper;
        Files.createDirectories(this.directory);
    }

    /**
     * Persists a successful result and the exact accepted encoded original before returning it.
     * Call only after the processing service has validated and decoded the photograph.
     * Failed staging leaves the previous latest version unchanged. Pruning failures are logged
     * without turning an already published successful save into a processing failure.
     * @param photo original JPEG/PNG bytes validated by the processing service
     * @param result successfully computed result belonging to those bytes
     * @return metadata and exact result of the newly published version
     * @throws IOException if writing/publishing fails, or encoded result JSON exceeds 32 MiB
     */
    public synchronized SavedAnalysis save(byte[] photo, ProcessingResult result) throws IOException {
        String id = UUID.randomUUID().toString();
        String type = photo[0] == (byte) 0x89 ? "image/png" : "image/jpeg";
        SavedAnalysis saved = new SavedAnalysis(id, Instant.now().toString(), type, result);
        byte[] json = mapper.writeValueAsBytes(saved);
        if (json.length > MAX_JSON_BYTES) throw new IOException("Saved analysis exceeds its JSON storage budget.");
        Path pending = Files.createTempDirectory(directory, ".pending-");
        try {
            Files.write(pending.resolve("photo"), photo);
            Files.write(pending.resolve("analysis.json"), json);
            // A persisted monotonic timestamp keeps the latest alias deterministic even for rapid saves.
            var existing = versions();
            long latest = existing.isEmpty() ? 0 : modified(existing.getFirst());
            Files.setLastModifiedTime(pending.resolve("analysis.json"),
                    FileTime.fromMillis(Math.max(System.currentTimeMillis(), latest + 1)));
            try {
                Files.move(pending, directory.resolve(id), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(pending, directory.resolve(id));
            }
        } finally {
            if (Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) removeVersion(pending);
        }
        try {
            List<Path> versions = versions();
            for (int i = retained; i < versions.size(); i++) removeVersion(versions.get(i));
        } catch (IOException exception) {
            LOG.warn("Saved analysis published, but old result cleanup failed", exception);
        }
        return saved;
    }

    /**
     * Reads an immutable version or resolves the latest alias, without modifying the saved result.
     * Staging directories and unrelated paths are never candidates.
     * @param id canonical UUID or the literal latest
     * @return empty for an unknown/invalid ID or an empty store
     * @throws IOException for a damaged or unreadable stored result
     */
    public synchronized Optional<SavedAnalysis> find(String id) throws IOException {
        Path version;
        if ("latest".equals(id)) {
            var versions = versions();
            if (versions.isEmpty()) return Optional.empty();
            version = versions.getFirst();
        } else {
            if (!validId(id)) return Optional.empty();
            version = directory.resolve(id);
        }
        Path json = version.resolve("analysis.json");
        if (!Files.isRegularFile(json, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (Files.size(json) > MAX_JSON_BYTES) throw new IOException("Stored analysis exceeds its JSON storage budget.");
        return Optional.of(mapper.readValue(Files.readAllBytes(json), SavedAnalysis.class));
    }

    /**
     * Reads the encoded original for a resolved saved analysis, under the same pruning lock.
     * @param id canonical UUID or latest
     * @return empty if no version exists, otherwise original bytes and their media type
     * @throws IOException if reading fails
     */
    public synchronized Optional<OriginalPhoto> original(String id) throws IOException {
        var saved = find(id);
        if (saved.isEmpty()) return Optional.empty();
        var analysis = saved.orElseThrow();
        return Optional.of(new OriginalPhoto(Files.readAllBytes(directory.resolve(analysis.id()).resolve("photo")), analysis.photoType()));
    }

    /**
     * Encoded original read from storage; no decoding or measurement occurs during retrieval.
     * @param bytes original uploaded JPEG/PNG bytes
     * @param mediaType validated original MIME type
     */
    public record OriginalPhoto(byte[] bytes, String mediaType) { }

    private List<Path> versions() throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> validId(path.getFileName().toString()))
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> Files.isRegularFile(path.resolve("analysis.json"), LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()))
                    .sorted(Comparator.comparingLong(AnalysisStore::modified).reversed()).toList();
        }
    }

    private static long modified(Path version) {
        try { return Files.getLastModifiedTime(version.resolve("analysis.json")).toMillis(); }
        catch (IOException exception) { return Long.MIN_VALUE; }
    }

    private static boolean validId(String id) {
        if (id == null) return false;
        try { return UUID.fromString(id).toString().equals(id); }
        catch (IllegalArgumentException exception) { return false; }
    }

    private static void removeVersion(Path path) throws IOException {
        Files.deleteIfExists(path.resolve("analysis.json"));
        Files.deleteIfExists(path.resolve("photo"));
        Files.deleteIfExists(path);
    }
}
