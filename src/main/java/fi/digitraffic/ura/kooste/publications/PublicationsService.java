package fi.digitraffic.ura.kooste.publications;

import com.github.slugify.Slugify;
import fi.digitraffic.ura.kooste.publications.model.Publication;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class PublicationsService {

    private static final String UTTU_EXPORT_PREFIX = "no.entur.uttu.export";
    private static final Pattern EXPORT_KEY_PATTERN = Pattern.compile("^(rb_)?(?<codespace>.{3}).+(?<timestamp>\\d{14})\\.(?<fileExtension>.{3})$");
    private static final ZoneId HELSINKI_TZ = ZoneId.of("Europe/Helsinki");
    private static final String UNSPECIFIED_LABEL = "UNSPECIFIED";
    public static final DateTimeFormatter UTTU_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicReference<List<Publication>> publications = new AtomicReference<>(List.of());

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final S3Client s3Client;
    private final S3TransferManager s3TransferManager;
    private final String fromBucket;
    private final String fromPrefix;
    private final String toBucket;
    private final String toPrefix;
    private final String cloudFrontUrl;
    private final Slugify slugger;

    public PublicationsService(S3Client s3Client,
                               S3TransferManager s3TransferManager,
                               @ConfigProperty(name = "kooste.tasks.s3copy.from.bucket") String fromBucket,
                               @ConfigProperty(name = "kooste.tasks.s3copy.from.prefix") String fromPrefix,
                               @ConfigProperty(name = "kooste.tasks.s3copy.to.bucket") String toBucket,
                               @ConfigProperty(name = "kooste.tasks.s3copy.to.prefix") String toPrefix,
                               @ConfigProperty(name = "kooste.tasks.s3copy.cloudFrontUriPattern") String cloudFrontUrl) {
        this.s3Client = Objects.requireNonNull(s3Client);
        this.s3TransferManager = Objects.requireNonNull(s3TransferManager);
        this.fromBucket = Objects.requireNonNull(fromBucket);
        this.fromPrefix = Objects.requireNonNull(fromPrefix);
        this.toBucket = Objects.requireNonNull(toBucket);
        this.toPrefix = Objects.requireNonNull(toPrefix);
        this.cloudFrontUrl = Objects.requireNonNull(cloudFrontUrl);
        this.slugger = Slugify.builder().build();
    }

    public List<Publication> listLatestPublications() {
        return List.copyOf(publications.get());
    }

    public void publishLatestPublications() {
        List<Publication> availablePublications = listAllAvailablePublications();
        List<Publication> allLatest = resolveLatest(availablePublications);
        List<Publication> exportedPublications = getExportedPublications(allLatest);
        this.publications.set(exportedPublications);
    }
    private List<Publication> listAllAvailablePublications() {
        List<Publication> availablePublications = listObjectsInBucket(fromBucket, fromPrefix).stream()
            .map(this::extractPublication)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
        logger.debug("Found {} publications", availablePublications.size());
        return availablePublications;
    }

    private List<Publication> resolveLatest(List<Publication> publications) {
        Map<String, Map<String, List<Publication>>> r = publications.stream()
            .collect(
                Collectors.groupingBy(Publication::codespace,
                    Collectors.groupingBy(Publication::label)));

        List<Publication> allLatest = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<Publication>>> byCodespace : r.entrySet()) {
            for (Map.Entry<String, List<Publication>> byLabel : byCodespace.getValue().entrySet()) {
                List<Publication> categorizedPublications = new ArrayList<>(byLabel.getValue());
                categorizedPublications.sort(Comparator.comparing(Publication::timestamp));
                Publication latest = categorizedPublications.getLast();
                logger.trace("Latest detected publication {}", latest);
                allLatest.add(latest);
            }
        }
        logger.debug("Reduced {} publications to {} latest publications", publications.size(), allLatest.size());
        return allLatest;
    }

    private List<Publication> getExportedPublications(List<Publication> allLatest) {
        List<Publication> exportedPublications = new ArrayList<>();
        allLatest.forEach(p -> {
            String objectName = p.codespace() + "-" + slugger.slugify(p.label()) + ".zip";
            try {
                s3TransferManager.copy(copy -> {
                    copy.copyObjectRequest(object -> {
                        object.sourceBucket(fromBucket)
                            .sourceKey(p.url())
                            .destinationBucket(toBucket)
                            .destinationKey(toPrefix + "/" + objectName);
                    });
                }).completionFuture().get();
                exportedPublications.add(new Publication(p.codespace(), p.label(), p.timestamp(), buildCloudFrontUrl(objectName), objectName));
            } catch (InterruptedException e) {
                logger.error("Thread interrupted! Interrupting current thread...", e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                logger.warn("Execution failed while copying {}", p, e);
            }
        });
        exportedPublications.sort(Comparator.comparing(Publication::codespace)
            .thenComparing(Publication::label));
        return exportedPublications;
    }

    private List<S3Object> listObjectsInBucket(String bucket, String prefix) {
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .build();
        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);
        List<S3Object> contents = listObjectsV2Response.contents();
        logger.trace("Number of objects in the bucket: {}", contents.size());
        if (logger.isTraceEnabled()) {
            contents.forEach(s -> logger.trace(s.toString()));
        }
        return contents;
    }

    Optional<Publication> extractPublication(S3Object s3Object) {
        String key = s3Object.key();
        Map<String, String> metadata = s3Client.headObject(b -> b.bucket(fromBucket).key(s3Object.key())).metadata();

        String fileName = removePrefix(fromPrefix, key);

        Matcher matcher = EXPORT_KEY_PATTERN.matcher(fileName);

        if (matcher.matches()) {
            logger.debug("File {} with metadata {} accepted, converting to publication", fileName, metadata);
            return Optional.of(new Publication(
                matcher.group("codespace"),
                metadata.getOrDefault(UTTU_EXPORT_PREFIX + ".name", UNSPECIFIED_LABEL),
                LocalDateTime.parse(matcher.group("timestamp"), UTTU_TIMESTAMP).atZone(HELSINKI_TZ),
                key,
                fileName));
        } else {
            logger.debug("Key {} did not match expected file pattern", key);
            return Optional.empty();
        }
    }

    private String removePrefix(String prefix, String s) {
        if (s.startsWith(prefix)) {
            return s.substring(prefix.length());
        } else {
            return s;
        }
    }

    private String buildCloudFrontUrl(String objectName) {
        return cloudFrontUrl.formatted(objectName);
    }
}