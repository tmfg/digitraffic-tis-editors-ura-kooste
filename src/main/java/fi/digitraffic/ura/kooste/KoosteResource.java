package fi.digitraffic.ura.kooste;

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
        public static native TemplateInstance index(List<Publication> publications);
    }

    @GET
    public TemplateInstance doGreeting(String name) {
        List<Publication> publications = publicationsService.listLatestPublications();
        return Templates.index(publications);
    }

}
