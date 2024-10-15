package fi.digitraffic.ura.kooste.web;

import fi.digitraffic.ura.kooste.publications.model.Publication;
import fi.digitraffic.ura.kooste.publications.PublicationsService;
import io.quarkus.amazon.s3.runtime.S3Crt;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.List;
import java.util.Locale;


@Path("/")
public class KoosteResource {

    @Inject
    S3Client s3Client;

    @Inject
    @S3Crt
    S3AsyncClient s3AsyncClient;

    @Inject
    @S3Crt
    S3TransferManager transferManager;

    @Inject
    SecretsManagerClient secretsManagerClient;

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
