package fi.digitraffic.ura.kooste.metrics;

import fi.digitraffic.ura.kooste.publications.model.Publication;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicationMetricsServiceTest {

    @Test
    void publishesSizeAndAgeMetricsWithCodespaceLabelFormatDimensions() {
        CloudWatchClient cloudWatchClient = mock(CloudWatchClient.class);
        PublicationMetricsService service = new PublicationMetricsService(cloudWatchClient, "prd");

        ZonedDateTime timestamp = ZonedDateTime.now(ZoneOffset.UTC).minusHours(2);
        Publication publication = new Publication("PETI", "all", timestamp, "url", "PETI-all-NeTEx.zip", "NeTEx", 12345L);

        service.publishMetrics(publication);

        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        assertThat(request.namespace(), is("Kooste"));
        assertThat(request.metricData(), hasSize(2));

        Map<String, MetricDatum> byName = request.metricData().stream()
            .collect(Collectors.toMap(MetricDatum::metricName, d -> d));

        assertThat(byName.keySet(), containsInAnyOrder("PublicationSizeBytes", "PublicationAgeSeconds"));
        assertThat(byName.get("PublicationSizeBytes").value(), is(12345.0));
        assertThat(byName.get("PublicationAgeSeconds").value(), is(closeTo(7200.0, 5.0)));

        List<Dimension> dimensions = byName.get("PublicationSizeBytes").dimensions();
        Map<String, String> dimensionValues = dimensions.stream()
            .collect(Collectors.toMap(Dimension::name, Dimension::value));
        assertThat(dimensionValues, equalTo(Map.of("Codespace", "PETI", "Label", "all", "Format", "NeTEx")));
    }

    @Test
    void doesNotThrowWhenCloudWatchCallFails() {
        CloudWatchClient cloudWatchClient = mock(CloudWatchClient.class);
        when(cloudWatchClient.putMetricData(any(PutMetricDataRequest.class))).thenThrow(new RuntimeException("boom"));

        PublicationMetricsService service = new PublicationMetricsService(cloudWatchClient, "prd");
        Publication publication = new Publication("URA", "all", ZonedDateTime.now(), "url", "URA-all-NeTEx.zip", "NeTEx", 100L);

        service.publishMetrics(publication);
    }

    /**
     * Locally there's no reliable AWS region/credentials setup for the plain CloudWatch SDK client
     * (unlike the quarkus-managed S3 client, which has explicit %dev overrides), so metrics publishing
     * is skipped entirely rather than failing/logging a warning on every local S3CopyTask run.
     */
    @Test
    void skipsPublishingInLocalEnvironment() {
        CloudWatchClient cloudWatchClient = mock(CloudWatchClient.class);
        PublicationMetricsService service = new PublicationMetricsService(cloudWatchClient, "local");
        Publication publication = new Publication("URA", "all", ZonedDateTime.now(), "url", "URA-all-NeTEx.zip", "NeTEx", 100L);

        service.publishMetrics(publication);

        verify(cloudWatchClient, never()).putMetricData(any(PutMetricDataRequest.class));
    }
}
