package fi.digitraffic.ura.kooste;

import java.time.ZonedDateTime;

public record Publication(String name, ZonedDateTime timestamp, String url) {
}
