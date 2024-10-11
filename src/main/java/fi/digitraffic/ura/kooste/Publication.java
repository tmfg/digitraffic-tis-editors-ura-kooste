package fi.digitraffic.ura.kooste;

import java.time.ZonedDateTime;

public record Publication(String codespace,
                          String label,
                          ZonedDateTime timestamp,
                          String url,
                          String fileName) {

    public Publication {
        codespace = codespace.toUpperCase();
    }
}
