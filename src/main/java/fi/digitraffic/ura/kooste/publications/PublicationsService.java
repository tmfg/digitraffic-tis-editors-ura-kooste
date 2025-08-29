package fi.digitraffic.ura.kooste.publications;

import com.github.slugify.Slugify;
import fi.digitraffic.ura.kooste.http.KoosteHttpClient;
import fi.digitraffic.ura.kooste.publications.model.Publication;
import fi.digitraffic.ura.kooste.publications.model.Publisher;
import fi.digitraffic.ura.kooste.publications.model.Publisher.IPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.internet.MimeUtility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class PublicationsService {

    public static final DateTimeFormatter TIMESTAMP_PATTERN = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String TEMP_FILE_PREFIX = "kooste";

    private static final Logger logger = LoggerFactory.getLogger(PublicationsService.class);
    private static final ZoneId HELSINKI_TZ = ZoneId.of("Europe/Helsinki");

    private static final String UNSPECIFIED_LABEL = "UNSPECIFIED";

    private final AtomicReference<List<Publication>> publications = new AtomicReference<>(List.of());

    private final S3Client s3Client;
    private final S3TransferManager s3TransferManager;
    private final String fromBucket;
    private final String fromUttuPrefix;
    private final String toBucket;
    private final String toPrefix;
    private final String cloudFrontUrl;
    private final Slugify slugger;
    private final String fromDownloadPrefix;

    public PublicationsService(S3Client s3Client,
                               S3TransferManager s3TransferManager,
                               @ConfigProperty(name = "kooste.tasks.s3copy.from.bucket") String fromBucket,
                               @ConfigProperty(name = "kooste.tasks.s3copy.from.uttu.prefix") String fromUttuPrefix,
                               @ConfigProperty(name = "kooste.tasks.s3copy.from.download.prefix") String fromDownloadPrefix,
                               @ConfigProperty(name = "kooste.tasks.s3copy.to.bucket") String toBucket,
                               @ConfigProperty(name = "kooste.tasks.s3copy.to.prefix") String toPrefix,
                               @ConfigProperty(name = "kooste.environment") String koosteEnvironment) {
        this.s3Client = Objects.requireNonNull(s3Client);
        this.s3TransferManager = Objects.requireNonNull(s3TransferManager);
        this.fromBucket = Objects.requireNonNull(fromBucket);
        this.fromUttuPrefix = Objects.requireNonNull(fromUttuPrefix);
        this.fromDownloadPrefix = Objects.requireNonNull(fromDownloadPrefix);
        this.toBucket = Objects.requireNonNull(toBucket);
        this.toPrefix = Objects.requireNonNull(toPrefix);
        this.cloudFrontUrl = resolveCloudFrontUrl(Objects.requireNonNull(koosteEnvironment));
        this.slugger = Slugify.builder().build();
    }

    private static String resolveCloudFrontUrl(String environment) {
        return switch (environment) {
            case "prd" -> "https://rae.fintraffic.fi/exports/%s";
            case "tst" -> "https://rae-test.fintraffic.fi/exports/%s";
            case "dev" -> "https://digitraffic-tis-ura-dev.aws.fintraffic.cloud/exports/%s";
            default -> "http://cloudfront.localhost/exports/%s";
        };
    }

    public List<Publication> listLatestPublications() {
        return List.copyOf(publications.get());
    }

    public void publishLatestPublications() {
        this.publications.set(Publisher.PUBLISHERS
            .parallelStream()
            .flatMap(service -> this.publishLatestPublications(service).stream())
            .sorted(Comparator.comparing(Publication::codespace).thenComparing(Publication::label))
            .toList());
    }

    private List<Publication> publishLatestPublications(IPublisher publisher) {
        List<Publication> availablePublications = listAllAvailablePublications(publisher);
        List<Publication> allLatest = resolveLatest(availablePublications);
        return getExportedPublications(allLatest, publisher);
    }

    public String resolvePrefix(IPublisher publisher) {
        if (publisher.getPublisherType().equals(Publisher.PublisherType.S3)) {
            return this.fromUttuPrefix;
        }
        return this.fromDownloadPrefix;
    }

    private List<Publication> listAllAvailablePublications(IPublisher publisher) {
        String fromPrefix = this.resolvePrefix(publisher);
        List<Publication> availablePublications = listObjectsInBucket(fromBucket, fromPrefix).stream()
            .map(s3Object -> this.extractPublication(s3Object, publisher, fromPrefix))
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

    private List<Publication> getExportedPublications(List<Publication> allLatest, IPublisher publisher) {
        List<Publication> exportedPublications = new ArrayList<>();
        allLatest.forEach(p -> {
            String objectName = createObjectName(p.codespace(), p.label(), p.format());
            try {
                exportedPublications.add(export(p, objectName));
            } catch (InterruptedException e) {
                logger.error("Thread interrupted! Interrupting current thread...", e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                logger.warn("Execution failed while copying {}", p, e);
            }
        });

        if (publisher.mergeContents()) {
            exportedPublications.add(combineAllAndExport(exportedPublications, publisher));
        }

        exportedPublications.sort(Comparator.comparing(Publication::codespace)
            .thenComparing(Publication::label));
        return exportedPublications;
    }

    private String createObjectName(String codespace, String label, String format) {
        return codespace + "-" + slugger.slugify(label) + "-" + format + ".zip";
    }

    private Publication export(Publication p, String objectName) throws InterruptedException, ExecutionException {
        s3TransferManager.copy(copy -> {
            copy.copyObjectRequest(object -> {
                object.sourceBucket(fromBucket)
                    .sourceKey(p.url())
                    .destinationBucket(toBucket)
                    .destinationKey(pathify(toPrefix, objectName));
            });
        }).completionFuture().get();
        return new Publication(p.codespace(), p.label(), p.timestamp(), buildCloudFrontUrl(objectName), objectName, p.format());
    }

    /**
     * Combines given list of Publication zip files into one big zip file and uploads that with given static name.
     *
     * @param publications Publications and a combined publication
     */
    private Publication combineAllAndExport(List<Publication> publications, IPublisher publisher) {
        String allPublication = "all";
        String aggregateName = publisher.name();
        String objectName = createObjectName(aggregateName, allPublication, publisher.format().displayName);

        logger.debug("Generating merged contents for {} from latest publications", objectName);

        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        try (ZipOutputStream outputZip = new ZipOutputStream(outputBytes)) {
            publications.forEach(publication -> {
                String s3Path = pathify(toPrefix, publication.fileName());
                logger.debug("Merging s3://{}/{} into {}", toBucket, s3Path, objectName);
                try (InputStream inputBytes = s3Client.getObject(getObject -> getObject.bucket(toBucket).key(s3Path));
                     ZipInputStream inputZip = new ZipInputStream(inputBytes)) {
                    ZipEntry entry;
                    while ((entry = inputZip.getNextEntry()) != null) {
                        String entryName = entry.getName();
                        // Avoid duplicate entries
                        if (!entry.isDirectory()) {
                            logger.trace("Copying {} from {} to {}", entryName, publication.fileName(), objectName);
                            outputZip.putNextEntry(new ZipEntry(entryName));
                            outputZip.write(inputZip.readAllBytes());
                            outputZip.closeEntry();
                        } else {
                            logger.trace("Current entry {} is a directory, skipping", entryName);
                        }
                        inputZip.closeEntry();
                    }
                } catch (IOException e) {
                    logger.warn("Failed to create combined 'all' publication", e);
                }
            });
        } catch (IOException e) {
            logger.error("Failed to merge zips, check logs for info", e);
        }

        ByteArrayInputStream finalOutput = new ByteArrayInputStream(outputBytes.toByteArray());
        s3Client.putObject(putObjectRequest -> {
            String s3Path = pathify(toPrefix, objectName);
            logger.debug("Copying merged publication to s3://{}/{}", toBucket, s3Path);
            putObjectRequest.bucket(toBucket).key(s3Path);
        }, RequestBody.fromInputStream(finalOutput, outputBytes.size()));

        return new Publication(
            aggregateName,
            allPublication,
            ZonedDateTime.now(),
            buildCloudFrontUrl(objectName),
            objectName,
            publisher.format().displayName);
    }

    /**
     * Guard against extra slashes in path parts etc. Will not add path separators to start/end of final string.
     *
     * @param parts Parts which may have extra slashes etc.
     * @return Normalized path.
     */
    protected static String pathify(String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String part : parts) {
            if (first) {
                first = false;
            } else {
                if (!part.startsWith("/")) {
                    part = "/" + part;
                }
            }
            if (part.endsWith("/")) {
                part = part.substring(0, part.length() - 1);
            }
            sb.append(part);
        }
        return sb.toString();
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

    Optional<Publication> extractPublication(S3Object s3Object, IPublisher publisher, String fromPrefix) {
        String key = s3Object.key();
        Map<String, String> metadata = mimeDecodeValues(s3Client.headObject(b -> b.bucket(fromBucket).key(s3Object.key())).metadata());

        String fileName = removePrefix(fromPrefix, key);

        Matcher matcher = publisher.exportPattern().matcher(fileName);

        if (matcher.matches()) {
            logger.debug("File {} with metadata {} accepted, converting to publication", fileName, metadata);
            return Optional.of(new Publication(
                matcher.group("codespace"),
                metadata.getOrDefault(publisher.exportPrefix() + ".name", UNSPECIFIED_LABEL),
                LocalDateTime.parse(matcher.group("timestamp"), TIMESTAMP_PATTERN).atZone(ZoneOffset.UTC).withZoneSameInstant(HELSINKI_TZ),
                key,
                fileName,
                publisher.format().displayName));
        } else {
            logger.debug("Key {} did not match expected file pattern", key);
            return Optional.empty();
        }
    }

    /**
     * Detects MIME encoded values and converts them to usable format.
     *
     * @param metadata Metadata to scan.
     * @return Decoded metadata.
     */
    protected static Map<String, String> mimeDecodeValues(Map<String, String> metadata) {
        Map<String, String> decodedMetadata = HashMap.newHashMap(metadata.size());

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String value = entry.getValue();
            if (value.startsWith("=?UTF-8?")) {
                try {
                    value = MimeUtility.decodeText(value);
                } catch (UnsupportedEncodingException e) {
                    logger.warn("Non-decodeable metadata value '{}={}'", entry.getKey(), entry.getValue(), e);
                }
            }
            decodedMetadata.put(
                entry.getKey(),
                value
            );
        }

        return decodedMetadata;
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

    public void downloadResource(Publisher.DownloadPublisher publisher) {
        File file = null;
        try {
            file =  File.createTempFile(TEMP_FILE_PREFIX, ".zip");
            KoosteHttpClient.get(publisher.getURI(), file, publisher.fileName());
            String objectName = this.downloadedObjectName(publisher);
            Map<String, String> metadata = Map.of(publisher.exportPrefix() + ".name", "all");
            upload(this.fromBucket, objectName, metadata, file.toPath());
            logger.info("Uploaded {} to bucket {} path {}", file.toPath(), fromBucket, objectName);
        } catch (Exception e) {
            logger.error("Failed to download resource {}", publisher.getURI(), e);
            throw new RuntimeException(e);
        } finally {
            if (file != null && file.exists() && file.delete()) {
                logger.trace("Deleted temp-file {}", file);
            }
        }
    }

    private void upload(String bucket, String objectName, Map<String, String> metadata, Path sourcePath) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectName)
                .metadata(metadata)
                .build(),
            sourcePath
        );
    }

    private String downloadedObjectName(IPublisher publisher) {
        return this.fromDownloadPrefix +
            publisher.name() +
            "-" +
            publisher.format().displayName +
            "-" +
            PublicationsService.TIMESTAMP_PATTERN.format(ZonedDateTime.now(ZoneOffset.UTC)) +
            ".zip";
    }
}
