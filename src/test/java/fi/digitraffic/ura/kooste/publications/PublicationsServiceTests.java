package fi.digitraffic.ura.kooste.publications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;

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
}
