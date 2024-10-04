package fi.digitraffic.ura.kooste;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.ZonedDateTime;
import java.util.List;

@ApplicationScoped
public class PublicationsService {
    public List<Publication> listLatestPublications() {
        return List.of(
            new Publication("eka", ZonedDateTime.now(), "http://example.fi"),
            new Publication("toka", ZonedDateTime.now().minusHours(3).minusMinutes(329), "http://example.org"),
            new Publication("kolmas", ZonedDateTime.now().minusHours(28).minusMinutes(432).minusSeconds(404), "http://example.local")
        );
    }
}
