package xyz.tcheeric.cashu.wallet.proto.service;

/**
 * Exception thrown when NUT-12 DLEQ verification fails.
 */
public class DLEQVerificationException extends RuntimeException {

    public DLEQVerificationException(String message) {
        super(message);
    }

    public DLEQVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
