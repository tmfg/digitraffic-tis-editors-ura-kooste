package fi.digitraffic.ura.kooste.scheduled;

import fi.digitraffic.ura.kooste.publications.PublicationsService;
import fi.digitraffic.ura.kooste.publications.model.Publisher;
import fi.digitraffic.ura.kooste.vaco.AuthoritiesDownloadService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


@ApplicationScoped
public class DownloadTask {
    private static final Logger logger = LoggerFactory.getLogger(DownloadTask.class);

    private final PublicationsService publicationsService;
    private final AuthoritiesDownloadService authoritiesDownloadService;

    @ConfigProperty(name = "kooste.tasks.download.enabled", defaultValue = "true")
    boolean enabled;

    public DownloadTask(PublicationsService publicationsService, AuthoritiesDownloadService authoritiesDownloadService) {
        this.publicationsService = publicationsService;
        this.authoritiesDownloadService = authoritiesDownloadService;
    }

    @Scheduled(cron="${kooste.tasks.download.schedule1}", timeZone = "Europe/Helsinki")
    @Scheduled(cron="${kooste.tasks.download.schedule2}", timeZone = "Europe/Helsinki")
    @Retry(maxRetries = 3, delay = 10_000L)
    void download() {
        if (!enabled) {
            logger.info("Download task is disabled. Skipping execution.");
            return;
        }

        // Attempt to fetch authorities.xml. If it fails, we can still proceed with the download of other resources.
        // Rae needs stops data so we don't want to fail the entire task if authorities.xml cannot be fetched.
        byte[] authoritiesXml = authoritiesDownloadService.fetchAuthoritiesXml();
        Map<String, byte[]> extraZipEntries = authoritiesXml != null
            ? Map.of("authorities.xml", authoritiesXml)
            : null;

        // Iterate through publishers and attempt to download resources. If authorities.xml was successfully fetched, it will be included in the download for publishers that require it.
        Publisher.PUBLISHERS.forEach(publisher -> {
            if (publisher instanceof Publisher.DownloadPublisher downloadPublisher) {
                logger.info("Attempting to download url {}", downloadPublisher.getURI());
                Map<String, byte[]> entries = downloadPublisher.includeAuthorities() ? extraZipEntries : null;
                publicationsService.downloadResource(downloadPublisher, entries);
            }
        });
    }
}
