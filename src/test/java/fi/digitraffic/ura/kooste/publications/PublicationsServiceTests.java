package fi.digitraffic.ura.kooste.publications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;

class PublicationsServiceTests {

    @Test
    void pathifyConcatenations() {
        assertAll(
            assertPath("/foo/bar", "/foo", "/bar"),
            assertPath("/foo/bar", "/foo", "bar"),
            assertPath("/foo/bar", "/foo/", "/bar"),
            assertPath("foo/bar", "foo", "/bar"),
            assertPath("foo/bar", "foo", "bar"),
            assertPath("foo/bar", "foo/", "/bar")
        );
    }

    private Executable assertPath(String expected, String... parts) {
        return () -> assertThat("Expected '" + expected + "' from " + Arrays.toString(parts), PublicationsService.pathify(parts), equalTo(expected));
    }

    /**
     * This is a known defect in AWS Java SDK v2 which AWS is not going to fix.
     *
     * @see <a href="https://github.com/aws/aws-sdk-java-v2/issues/1828">GitHub.com: aws/aws-sdk-java-v2 issue #1828</a>
     * @see <a href="https://github.com/aws/aws-sdk-java-v2/issues/2624">GitHub.com: aws/aws-sdk-java-v2 issue #2624</a>
     */
    @Test
    void decodesMimeEncodedMetadataValues() {
        String decodedValue = "I'm a complex value with åäö";

        byte[] utf8Bytes = decodedValue.getBytes(StandardCharsets.UTF_8);
        String mimeEncodedValue = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(utf8Bytes) + "?=";

        Map<String, String> metadata = Map.of("normal", "normal", "decodedButFaulty", decodedValue, "encoded", mimeEncodedValue);
        Map<String, String> expectedDecodedMetadata = Map.of("normal", "normal", "decodedButFaulty", decodedValue, "encoded", decodedValue);

        assertThat(PublicationsService.mimeDecodeValues(metadata), equalTo(expectedDecodedMetadata));
    }
}
