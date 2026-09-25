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
}
