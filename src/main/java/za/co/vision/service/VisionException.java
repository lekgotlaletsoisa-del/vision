package za.co.vision.service;

/** A processing refusal that can be safely explained to the caller. */
public class VisionException extends RuntimeException {
    /** Reasons mapped to HTTP responses by the API layer. */
    public enum Reason {
        /** Empty, unsupported or undecodable image. */
        INVALID_IMAGE,
        /** Encoded size or decoded pixel budget exceeded. */
        TOO_LARGE,
        /** Image contains more contours than the configured processing budget. */
        TOO_COMPLEX,
        /** All native processing slots are occupied; retry later. */
        BUSY
    }

    /** Processing category retained for HTTP status mapping. */
    private final Reason reason;

    /**
     * Creates a refusal with a message safe for display.
     * @param reason processing category
     * @param message user-facing explanation
     */
    public VisionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Identifies the processing refusal category.
     * @return category for HTTP status mapping
     */
    public Reason reason() {
        return reason;
    }
}
