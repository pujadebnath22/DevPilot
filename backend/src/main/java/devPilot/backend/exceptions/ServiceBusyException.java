package devPilot.backend.exceptions;

/** Temporary capacity problem (e.g. indexing queue full). Mapped to HTTP 503. */
public class ServiceBusyException extends RuntimeException {
    public ServiceBusyException(String message) {
        super(message);
    }
}
