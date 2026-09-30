package fi.digitraffic.ura.kooste.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

/**
 * No quarkiverse extension exists for CloudWatch (unlike S3), so the client is a plain
 * AWS SDK v2 client. Region and credentials are resolved via the default provider chains,
 * same as on ECS Fargate task role.
 */
public class CloudWatchClientProducer {

    @Produces
    @ApplicationScoped
    public CloudWatchClient cloudWatchClient() {
        return CloudWatchClient.create();
    }
}
