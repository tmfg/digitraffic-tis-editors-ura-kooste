package fi.digitraffic.ura.kooste.vaco.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record ConversionConfig(String codespace, boolean stopsOnly) {
}
