package fi.digitraffic.ura.kooste.metrics;

import fi.digitraffic.ura.kooste.publications.model.Publication;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Publishes size and freshness metrics for exported publications. Metric names, namespace and
 * dimension names (Codespace/Label/Format) feed the alarms defined in digitraffic-tis-editors-infra
 * and must stay in sync with that repo (see DPO-4871).
 */
@ApplicationScoped
public class PublicationMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(PublicationMetricsService.class);
    private static final String NAMESPACE = "Kooste";
    private static final String LOCAL_ENVIRONMENT = "local";

    private final CloudWatchClient cloudWatchClient;
    private final boolean enabled;

    public PublicationMetricsService(CloudWatchClient cloudWatchClient,
                                     @ConfigProperty(name = "kooste.environment") String environment) {
        this.cloudWatchClient = cloudWatchClient;
        // Locally there's no reliable AWS region/credentials configuration for a plain SDK client
        // (unlike the quarkus-managed S3 client, which has explicit %dev overrides) - skip rather
        // than spam warnings on every local S3CopyTask run.
        this.enabled = !LOCAL_ENVIRONMENT.equals(environment);
    }

    public void publishMetrics(Publication publication) {
        if (!enabled) {
            logger.debug("Skipping metrics publishing for {} in local environment", publication.fileName());
            return;
        }
        try {
            Instant now = Instant.now();
            List<Dimension> dimensions = List.of(
                Dimension.builder().name("Codespace").value(publication.codespace()).build(),
                Dimension.builder().name("Label").value(publication.label()).build(),
                Dimension.builder().name("Format").value(publication.format()).build()
            );

            long ageSeconds = Duration.between(publication.timestamp().toInstant(), now).getSeconds();

            cloudWatchClient.putMetricData(PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(
                    MetricDatum.builder()
                        .metricName("PublicationSizeBytes")
                        .dimensions(dimensions)
                        .unit(StandardUnit.BYTES)
                        .value((double) publication.sizeBytes())
                        .timestamp(now)
                        .build(),
                    MetricDatum.builder()
                        .metricName("PublicationAgeSeconds")
                        .dimensions(dimensions)
                        .unit(StandardUnit.SECONDS)
                        .value((double) ageSeconds)
                        .timestamp(now)
                        .build())
                .build());
        } catch (Exception e) {
            // Best-effort: a CloudWatch outage must not block publishing exports.
            logger.warn("Failed to publish metrics for {}", publication.fileName(), e);
        }
    }
}
