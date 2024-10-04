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

import java.time.ZonedDateTime;
import java.util.List;


@Path("/kooste")
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

    private final HelloService service;

    public KoosteResource(HelloService service) {
        this.service = service;
    }

    @CheckedTemplate
    public static class Templates {

        public static native TemplateInstance index(String nav, List<Publication> publications);

    }

    @GET
    @Path("/greeting")
    public TemplateInstance greeting() {
        return doGreeting(null);
    }

    @GET
    @Path("/greeting/{name}")
    public TemplateInstance greeting(String name) {
        return doGreeting(name);
    }

    private TemplateInstance doGreeting(String name) {
        if (name == null || name.isEmpty()) {
            name = "unnamed wanderer";
        }
        List<Publication> publications = List.of(
            new Publication("eka", ZonedDateTime.now(), "http://example.fi"),
            new Publication("eka", ZonedDateTime.now().minusHours(3).minusMinutes(329), "http://example.org"),
            new Publication("eka", ZonedDateTime.now().minusHours(28).minusMinutes(432).minusSeconds(404), "http://example.local")
        );

        return Templates.index("nav TBD", publications);
    }

}
