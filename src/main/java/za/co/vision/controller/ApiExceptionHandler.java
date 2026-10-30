package za.co.vision.controller;

import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import za.co.vision.service.VisionException;

/** Maps expected failures to concise JSON errors without exposing internals or image bytes. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Creates the global API error adapter. */
    public ApiExceptionHandler() { }

    /**
     * Maps processing refusals to 400, 413, 422 or 429.
     * @param exception safe processing refusal
     * @return non-cacheable error JSON
     */
    @ExceptionHandler(VisionException.class)
    public ResponseEntity<Map<String, String>> processing(VisionException exception) {
        HttpStatus status = switch (exception.reason()) {
            case INVALID_IMAGE -> HttpStatus.BAD_REQUEST;
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case TOO_COMPLEX -> HttpStatus.UNPROCESSABLE_CONTENT;
            case BUSY -> HttpStatus.TOO_MANY_REQUESTS;
        };
        return error(status, exception.getMessage());
    }

    /**
     * Handles multipart limits at the servlet boundary.
     * @param exception container upload rejection
     * @return 413 error JSON
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> uploadSize(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "Upload exceeds the configured size limit. Choose a smaller image.");
    }

    /**
     * Handles invalid numeric controls, missing parts and malformed multipart requests.
     * @param exception binding or validation failure
     * @return 400 error JSON
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class, MissingServletRequestParameterException.class,
            MultipartException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception exception) {
        String message = exception instanceof IllegalArgumentException ? exception.getMessage()
                : "Supply an image part and valid numeric processing parameters as multipart/form-data.";
        return error(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Handles unsupported request encodings.
     * @param exception unsupported content type
     * @return 415 error JSON
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> mediaType(HttpMediaTypeNotSupportedException exception) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use multipart/form-data with an image part.");
    }

    /**
     * Logs unexpected failures server-side and provides a generic message to the browser.
     * @param exception unexpected processing or input/output error
     * @return 500 error JSON
     */
    @ExceptionHandler({Exception.class, IOException.class})
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        LOG.error("Vision processing request failed", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Processing failed. Try another image or check the application log.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(Map.of("status", "error", "message", message));
    }
}
