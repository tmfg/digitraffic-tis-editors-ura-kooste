package fi.digitraffic.ura.kooste.vaco;

import fi.digitraffic.ura.kooste.http.KoosteHttpClient;
import fi.digitraffic.ura.kooste.publications.model.Publisher;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

/**
 * Manual smoke test for verifying AuthoritiesDownloadService against a real VACO instance.
 * Skipped by default — only runs when a bearer token is explicitly provided.
 *
 * <p>Prerequisites:</p>
 * <ul>
 *   <li>VACO backend running locally on http://localhost:8080</li>
 *   <li>A valid Azure AD bearer token for the VACO API</li>
 * </ul>
 * 
 * <p>To get a token, copy it for example from browser DevTools (Network tab → any VACO API request →
 * Authorization header)</p>
 *
 * <p>Run with:</p>
 * <pre>
 *   mvn test -Dtest=AuthoritiesDownloadServiceLocalIT -Dtest.vaco.token=eyJ...
 * </pre>
 *
 * <p>Output zip is saved to {@code target/test-output/PETI-NeTEx-all-test.zip} for manual inspection.</p>
 */
@QuarkusTest
@EnabledIfSystemProperty(named = "test.vaco.token", matches = ".+")
class AuthoritiesDownloadServiceLocalIT {

    @Inject
    AuthoritiesDownloadService authoritiesDownloadService;

    @InjectMock
    Tokens tokens;

    /**
     * End-to-end test: downloads PETI NeTEx stops from peti-dev AND authorities.xml from VACO,
     * creates a zip containing both, and verifies the zip entries.
     *
     * <p>Requires VACO running locally on port 8080. Uses peti-dev.fintraffic.fi for stops data.</p>
     */
    @Test
    void createsPetiZipWithAuthorities(@TempDir File tempDir) throws Exception {
        when(tokens.getAccessToken()).thenReturn(System.getProperty("test.vaco.token"));

        // 1. Download authorities.xml from VACO
        byte[] authoritiesXml = authoritiesDownloadService.fetchAuthoritiesXml();
        assertThat("authorities.xml download failed", authoritiesXml, is(notNullValue()));

        // 2. Download PETI stops and create zip with authorities.xml included
        URI petiUri = URI.create("https://digitraffic-tis-peti-dev.aws.fintraffic.cloud/api/fintraffic/v1/stops");
        File zipFile = new File(tempDir, "PETI-NeTEx-all-test.zip");
        KoosteHttpClient.get(petiUri, zipFile, Publisher.NETEX_ARCHIVE_FILENAME, Map.of("authorities.xml", authoritiesXml));

        // 3. Verify zip contains both entries
        assertThat("Zip file was not created", zipFile.exists(), is(true));
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry stopsEntry = zip.getEntry("stops.xml");
            assertThat("stops.xml missing from zip", stopsEntry, is(notNullValue()));
            assertThat("stops.xml should not be empty", stopsEntry.getSize() != 0, is(true));

            ZipEntry authoritiesEntry = zip.getEntry("authorities.xml");
            assertThat("authorities.xml missing from zip", authoritiesEntry, is(notNullValue()));

            String authoritiesContent = new String(zip.getInputStream(authoritiesEntry).readAllBytes());
            assertThat(authoritiesContent, containsString("<PublicationDelivery"));
        }

        // 4. Copy zip to target/ for manual inspection
        File outputDir = new File("target/test-output");
        outputDir.mkdirs();
        File savedZip = new File(outputDir, "PETI-NeTEx-all-test.zip");
        java.nio.file.Files.copy(zipFile.toPath(), savedZip.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Zip saved to: " + savedZip.getAbsolutePath());
    }
}
