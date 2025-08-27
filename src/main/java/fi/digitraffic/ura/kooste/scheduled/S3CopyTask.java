package fi.digitraffic.ura.kooste.scheduled;

import fi.digitraffic.ura.kooste.publications.PublicationsService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class S3CopyTask {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PublicationsService publicationsService;

    public S3CopyTask(PublicationsService publicationsService) {
        this.publicationsService = publicationsService;
    }

    @Scheduled(every = "{kooste.tasks.s3copy.schedule}")
    void scanAndCopy() {
        logger.info("Polling S3");
        publicationsService.publishLatestPublications();
    }
}
