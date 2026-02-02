package fi.digitraffic.ura.kooste.vaco;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fi.digitraffic.ura.kooste.http.KoosteHttpClient;
import fi.digitraffic.ura.kooste.publications.PublicationsService;
import fi.digitraffic.ura.kooste.publications.model.Publisher;
import fi.digitraffic.ura.kooste.vaco.model.ConversionConfig;
import fi.digitraffic.ura.kooste.vaco.model.ConversionOption;
import fi.digitraffic.ura.kooste.vaco.model.EntryRequest;
import fi.digitraffic.ura.kooste.vaco.model.EntryResponse;
import fi.digitraffic.ura.kooste.vaco.model.Link;
import fi.digitraffic.ura.kooste.vaco.model.ValidationConfig;
import fi.digitraffic.ura.kooste.vaco.model.ValidationOption;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@ApplicationScoped
public class VacoTask {
    private static final Logger logger = LoggerFactory.getLogger(VacoTask.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    static {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());
    }

    private static final String NETEX_PACKAGE_NAME = "PETI-all-NeTEx.zip";
    private static final String GTFS_PACKAGE_NAME = "PETI-all-GTFS.zip";
    private static final String CONVERSION_RULE = "netex2gtfs.entur";
    private static final String VALIDATION_RULE = "netex.entur";
    private static final String CODESPACE = "FSR";

    private static final Pattern PETI_GTFS_ALL_EXPORT_PATTERN = Pattern.compile("^(?<codespace>(PETI))-GTFS-all-(?<timestamp>\\d{14})\\.zip$");
    private static final String S3_VACO_INPUT_PREFIX = "inbound/vaco/";
    private static final Publisher.DownloadPublisher GTFS_PUBLISHER =
        new Publisher.DownloadPublisher(
            "PETI",
            PETI_GTFS_ALL_EXPORT_PATTERN,
            S3_VACO_INPUT_PREFIX,
            Publisher.PublisherFormat.GTFS,
            "kooste.tasks.vaco.download.url.gtfs",
            "all",
            "inbound/vaco/PETI-GTFS-all-{timestamp}.zip");

    private final PublicationsService publicationsService;
    private final String koosteEnvironment;
    private final String businessId;
    private final URI vacoUri;

    @Inject
    Tokens tokens;

    public VacoTask(
        PublicationsService publicationsService,
        @ConfigProperty(name = "kooste.environment") String koosteEnvironment,
        @ConfigProperty(name = "kooste.business-id") String businessId,
        @ConfigProperty(name = "kooste.tasks.vaco.url") String vacoUri
    ) {
        this.publicationsService = publicationsService;
        this.koosteEnvironment = koosteEnvironment;
        this.businessId = businessId;
        this.vacoUri = URI.create(vacoUri);
    }


    /**
     * Sen NeTEx to GTFS conversion job to VACO. Does not store the returned public id. The conversion result is
     * then retrieved by periodically polling the queue-endpoint. See VacoTask.downloadGTFS -method.
     * @throws IOException
     * @throws InterruptedException
     */
    @Scheduled(cron="${kooste.tasks.vaco.schedule}", timeZone = "Europe/Helsinki")
    @Retry(maxRetries = 3, delay = 10_000L)
    public void queueTask() throws IOException, InterruptedException {
        EntryRequest entry = new EntryRequest(
            "netex",
            NETEX_PACKAGE_NAME,
            this.getNeTExPackageUrl(),
            String.format("%d", System.currentTimeMillis()),
            this.businessId,
            List.of(new ValidationOption(VALIDATION_RULE, new ValidationConfig(CODESPACE, 500))),
            List.of(new ConversionOption(CONVERSION_RULE, new ConversionConfig(CODESPACE, true)))
        );

        postEntryToQueue(entry);
    }

    private String getNeTExPackageUrl() {
        return String.format(this.publicationsService.resolveCloudFrontUrl(this.koosteEnvironment), NETEX_PACKAGE_NAME);
    }

    private void postEntryToQueue(EntryRequest entry) throws IOException, InterruptedException {
        URI uri = this.vacoUri.resolve("/api/queue");
        logger.info("Posting entry to {} with etag {} and businessId {}", uri, entry.etag(), entry.businessId());
        byte[] res = KoosteHttpClient.post(uri, serializeObject(entry), getHeaders());

        EntryResponse entryResponse = deSerializeObject(res, EntryResponse.class);
        logger.info("Got queue response: {}", entryResponse);
    }

    /**
     * Poll for VACO NeTEx to GTFS conversion for every 15min.
     * Download the latest result only if not present in kooste S3 bucket.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Scheduled(every = "15m")
    public void downloadGTFS() throws IOException, InterruptedException {
        logger.info("Downloading GTFS");
        Optional<EntryResponse> latest = getLatestEntry();
        if (latest.isPresent()) {
            EntryResponse entry = latest.get();
            logger.info("Latest GTFS entry: {}", entry);
            URI gtfsUri = getGTFSResultPackageURI(entry).orElseThrow();
            logger.debug("Latest GTFS entry package URI: {}", gtfsUri);
            if (!packageExists(entry)) {
                this.downloadResource(gtfsUri, entry.data().publicId());
            }
            else {
                logger.info("GTFS package already exists: {}", gtfsUri);
            }
        }
    }

    private void downloadResource(URI gtfsUri, String publicId) {
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("vaco-id", publicId);
        this.publicationsService.downloadResource(GTFS_PUBLISHER, gtfsUri, false, getHeaders(), metadata);
    }

    /**
     * Get headers Map constisting of currently valid OAuth2.0 Access Token.
     *
     * @return Map of headers
     */
    private Map<String, String> getHeaders() {
        return Map.of("Authorization", String.format("Bearer %s", tokens.getAccessToken()));
    }

    private boolean packageExists(EntryResponse latest) {
        return this.publicationsService.hasPublishedPublicationWithMetadata(GTFS_PACKAGE_NAME, "vaco-id", latest.data().publicId());
    }

    /**
     * Return Optional Latest Entry from VACO which has result package and processing is done.
     *
     * @return Latest Entry if found.
     * @throws IOException
     * @throws InterruptedException
     */
    private Optional<EntryResponse> getLatestEntry() throws IOException, InterruptedException {
        Comparator<EntryResponse> comparator = Comparator.comparing(e -> e.data().created());
        return Arrays.stream(listEntries())
            .filter(this::isReadyEntry)
            .filter(VacoTask::hasGTFSResultPackage)
            .max(comparator);
    }

    /**
     * List entries in VACO.
     * @return List of entries in VACO.
     * @throws IOException
     * @throws InterruptedException
     */
    private EntryResponse[] listEntries() throws IOException, InterruptedException {
        String path = String.format("/api/queue?businessId=%s&count=1000&name=%s", this.businessId, NETEX_PACKAGE_NAME);
        URI uri = this.vacoUri.resolve(path);
        byte[] res = KoosteHttpClient.get(uri, getHeaders());
        return deSerializeObject(res, EntryResponse[].class);
    }

    private boolean isReadyEntry(EntryResponse entry) {
        return entry.data().name().equalsIgnoreCase(NETEX_PACKAGE_NAME)
            && entry.data().status().equalsIgnoreCase("success")
            && entry.data().url().equalsIgnoreCase(this.getNeTExPackageUrl())
            && entry.data().format().equalsIgnoreCase("netex");
    }

    private static boolean hasGTFSResultPackage(EntryResponse entry) {
        return getGTFSResultPackageURI(entry).isPresent();
    }

    private static Optional<URI> getGTFSResultPackageURI(EntryResponse entry) {
        if (entry.links().containsKey(CONVERSION_RULE)) {
            Map<String, Link> resultMap = entry.links().get(CONVERSION_RULE);
            if (resultMap.containsKey("result")) {
                return Optional.of(URI.create(resultMap.get("result").href()));
            }
        }
        return Optional.empty();
    }

    private static byte[] serializeObject(Object object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(object).getBytes(StandardCharsets.UTF_8);
    }

    private static <T> T deSerializeObject(byte[] data, Class<T> tClass) throws IOException {
        return objectMapper.readValue(data, tClass);
    }
}
