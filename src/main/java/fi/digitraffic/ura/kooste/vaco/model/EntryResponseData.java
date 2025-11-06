package fi.digitraffic.ura.kooste.vaco.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDateTime;

@JsonSerialize
public record EntryResponseData(
    String publicId,
    String context,
    String status,
    String name,
    String format,
    String url,
    LocalDateTime created,
    LocalDateTime started,
    LocalDateTime updated
) {
}
