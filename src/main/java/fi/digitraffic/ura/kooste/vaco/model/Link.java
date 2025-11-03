package fi.digitraffic.ura.kooste.vaco.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record Link(String href, String method) {
}
