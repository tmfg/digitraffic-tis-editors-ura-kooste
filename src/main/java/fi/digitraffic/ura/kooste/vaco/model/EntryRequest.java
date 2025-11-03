package fi.digitraffic.ura.kooste.vaco.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

@JsonSerialize
public record EntryRequest(
    String format,
    String name,
    String url,
    String etag,
    String businessId,
    List<ValidationOption> validations,
    List<ConversionOption> conversions
    ) {}
