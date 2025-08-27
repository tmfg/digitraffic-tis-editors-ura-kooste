package fi.digitraffic.ura.kooste.scheduled;

import fi.digitraffic.ura.kooste.publications.PublicationsService;
import fi.digitraffic.ura.kooste.publications.model.Publisher;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ApplicationScoped
public class DownloadTask {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PublicationsService publicationsService;

    public DownloadTask(PublicationsService publicationsService) {
        this.publicationsService = publicationsService;
    }

    @Scheduled(cron="${kooste.tasks.download.schedule}")
    @Retry(maxRetries = 3, delay = 10_000L)
    void download() {
        Publisher.PUBLISHERS.forEach(publisher -> {
            if (publisher instanceof Publisher.DownloadPublisher downloadPublisher) {
                logger.info("Attempting to download url {}", downloadPublisher.getURI());
                publicationsService.downloadResource(downloadPublisher);
            }
        });
    }
}
