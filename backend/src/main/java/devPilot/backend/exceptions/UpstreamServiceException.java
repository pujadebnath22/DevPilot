package devPilot.backend.exceptions;

/**
 * An external dependency (embedding model, chat model, vector store) failed.
 * Mapped to HTTP 502 with the given user-facing message.
 */
public class UpstreamServiceException extends RuntimeException {
    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
