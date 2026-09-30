package fi.digitraffic.ura.kooste.publications.model;

import java.time.ZonedDateTime;

public record Publication(String codespace,
                          String label,
                          ZonedDateTime timestamp,
                          String url,
                          String fileName,
                          String format,
                          long sizeBytes) {

    public Publication {
        codespace = codespace.toUpperCase();
    }

    public String formattedSize() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        } else if (sizeBytes < 1024 * 1024) {
            return String.format("%.1f KiB", sizeBytes / 1024.0);
        } else {
            return String.format("%.1f MiB", sizeBytes / 1024.0 / 1024.0);
        }
    }
}
