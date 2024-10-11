package fi.digitraffic.ura.kooste;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

@ApplicationScoped
@IfBuildProfile("dev")
@Startup
public class DevDataInjector {

    public static final int MAX_DEPTH = 6;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final S3Client s3Client;
    private final String fromBucket;

    public DevDataInjector(S3Client s3Client,
                           @ConfigProperty(name = "kooste.tasks.s3copy.from.bucket") String fromBucket) {
        this.s3Client = s3Client;
        this.fromBucket = fromBucket;
        init();
    }

    void init() {
        try {
            URL devS3 = Thread.currentThread().getContextClassLoader().getResource("dev/s3");
            logger.info("Found dev resources in {}", devS3);
            Path testFileDir = Path.of(devS3.toURI());
            try (Stream<Path> testFiles = Files.find(testFileDir, MAX_DEPTH, (path, attr) -> Files.isRegularFile(path))) {

                testFiles.forEach(file -> {
                    if (Files.isRegularFile(file)) {
                        String relativeName = testFileDir.relativize(file).toString();
                        logger.debug("Injecting test file {} to S3 {}:{}", file, fromBucket, relativeName);
                        s3Client.putObject(request -> request.bucket(fromBucket)
                            .key(relativeName)
                            .metadata(
                                Map.of(
                                    "no.entur.uttu.export.name", "uncategorized")),
                            file);
                    }
                });
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
