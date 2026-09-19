package com.carbonauditor.scanner;

/**
 * Thrown when a scan (local or cloud) is stopped early because the user
 * clicked "Cancel". Unchecked, since it's a normal cooperative-cancellation
 * signal rather than an error condition — callers should treat it
 * differently from a real scan failure (e.g. not show an error dialog, and
 * never persist a cancelled scan to the database).
 */
public class ScanCancelledException extends RuntimeException {
    public ScanCancelledException() {
        super("Scan cancelled by user");
    }
}
