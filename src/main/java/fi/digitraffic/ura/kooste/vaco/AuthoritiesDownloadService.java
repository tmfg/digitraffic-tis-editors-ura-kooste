package fi.digitraffic.ura.kooste.vaco;

import fi.digitraffic.ura.kooste.http.KoosteHttpClient;
import io.quarkus.oidc.client.Tokens;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;

@ApplicationScoped
public class AuthoritiesDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(AuthoritiesDownloadService.class);

    private static final String AUTHORITIES_PATH = "/api/exports/netex/authorities";

    private final URI vacoBaseUri;
    private final Tokens tokens;

    public AuthoritiesDownloadService(
        @ConfigProperty(name = "kooste.tasks.vaco.url") String vacoUrl,
        Tokens tokens
    ) {
        String base = vacoUrl.endsWith("/") ? vacoUrl.substring(0, vacoUrl.length() - 1) : vacoUrl;
        this.vacoBaseUri = URI.create(base);
        this.tokens = tokens;
    }

    /**
     * Downloads authorities.xml from VACO. Returns the raw XML bytes, or null if the download fails.
     */
    public byte[] fetchAuthoritiesXml() {
        try {
            URI uri = resolveAuthoritiesUri();
            Map<String, String> headers = getHeaders();
            byte[] response = KoosteHttpClient.get(uri, headers);
            if (response.length == 0) {
                logger.warn("VACO returned empty authorities response");
                return null;
            }
            logger.info("Downloaded authorities.xml ({} bytes)", response.length);
            return response;
        } catch (Exception e) {
            logger.warn("Failed to download authorities.xml from VACO, PETI NeTEx zip will be published without it", e);
            return null;
        }
    }

    URI resolveAuthoritiesUri() {
        return vacoBaseUri.resolve(AUTHORITIES_PATH);
    }

    private Map<String, String> getHeaders() {
        return Map.of(
            "Authorization", String.format("Bearer %s", tokens.getAccessToken()),
            "Accept", "application/xml"
        );
    }
}
