package fi.digitraffic.ura.kooste.web;

import fi.digitraffic.ura.kooste.publications.PublicationsService;
import fi.digitraffic.ura.kooste.publications.model.Publication;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.List;
import java.util.Locale;


@Path("/")
public class KoosteResource {

    private final PublicationsService publicationsService;

    public KoosteResource(PublicationsService publicationsService) {
        this.publicationsService = publicationsService;
    }

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance index(Locale locale, List<Publication> publications);
    }

    @GET
    public TemplateInstance getPublications() {
        List<Publication> publications = publicationsService.listLatestPublications();
        Locale locale = Locale.of("fi");
        return Templates.index(locale, publications)
            .setLocale(locale)
            .setAttribute("bundle", "publications");
    }

}
