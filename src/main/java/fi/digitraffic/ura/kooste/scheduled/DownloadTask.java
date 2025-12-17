package fi.digitraffic.ura.kooste.scheduled;

import fi.digitraffic.ura.kooste.publications.PublicationsService;
import fi.digitraffic.ura.kooste.publications.model.Publisher;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ApplicationScoped
public class DownloadTask {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PublicationsService publicationsService;

    @ConfigProperty(name = "kooste.tasks.download.enabled", defaultValue = "true")
    boolean enabled;

    public DownloadTask(PublicationsService publicationsService) {
        this.publicationsService = publicationsService;
    }

    @Scheduled(cron="${kooste.tasks.download.schedule}", timeZone = "Europe/Helsinki")
    @Retry(maxRetries = 3, delay = 10_000L)
    void download() {
        if (!enabled) {
            logger.info("Download task is disabled. Skipping execution.");
            return;
        }
        Publisher.PUBLISHERS.forEach(publisher -> {
            if (publisher instanceof Publisher.DownloadPublisher downloadPublisher) {
                logger.info("Attempting to download url {}", downloadPublisher.getURI());
                publicationsService.downloadResource(downloadPublisher);
            }
        });
    }
}
