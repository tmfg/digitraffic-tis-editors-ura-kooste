package fi.digitraffic.ura.kooste.vaco.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;

@JsonSerialize
public record EntryResponse(
    EntryResponseData data,
    Map<String, Map<String, Link>> links
) {

    @Override
    public String toString() {
        return "EntryResponse{" +
            "publicId=" + data.publicId() +
            " created=" + data.created() +
            " status=" + data.status() +
            " name=" + data.name() +
            '}';
    }
}
