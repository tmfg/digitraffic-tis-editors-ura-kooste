package fi.digitraffic.ura.kooste;

import fi.digitraffic.ura.kooste.publications.model.Publication;
import fi.digitraffic.ura.kooste.scheduled.S3CopyTask;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@QuarkusTest
class S3CopyTaskTests {

    @Inject
    S3CopyTask s3CopyTask;

    @Test
    void extractsPublicationFromS3ObjectName() {
        // rb_{codespace}{(optional) -flexible-lines}-{yyyyMMddHHmmss}.zip
        S3Object s3Object = S3Object.builder()
            .key("rb_fsr-flexible-lines-20241010114712.zip")
            .lastModified(Instant.now())
            .build();

        Optional<Publication> p = null;//s3CopyTask.extractPublication(s3Object);

        System.out.println("p = " + p);

    }

    public static void main(String[] args) {
        Matcher r = Pattern.compile("^rb_(?<codespace>.{3}).*(?<timestamp>\\d{14})\\.(?<fileExtension>.{3})$").matcher("rb_fsr-flexible-lines-20241010114712.zip");

        System.out.println("r.matches() = " + r.matches());
        Map<String, Integer> groups = r.namedGroups();
        groups.forEach((k, v) -> System.out.println(k + ": " + r.group(v)));
    }
}
