package za.co.vision.model;

/**
 * A durable, immutable successful analysis. The original encoded photograph is stored separately.
 * @param id UUID identifying this version, independent of browser cookies or storage
 * @param savedAt ISO-8601 UTC timestamp at persistence time
 * @param photoType validated original media type, image/jpeg or image/png
 * @param result exact Java/OpenCV response including parameters, stages and source-pixel geometry
 */
public record SavedAnalysis(String id, String savedAt, String photoType, ProcessingResult result) { }
