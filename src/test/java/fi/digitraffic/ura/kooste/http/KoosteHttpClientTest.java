package fi.digitraffic.ura.kooste.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KoosteHttpClientTest {

    private static HttpServer server;
    private static URI baseUri;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stops", exchange -> {
            byte[] body = "<stops>test</stops>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.createContext("/error", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.getResponseBody().close();
        });
        server.start();
        baseUri = URI.create("http://localhost:" + server.getAddress().getPort());
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void createsZipWithPrimaryEntryOnly(@TempDir File tempDir) throws Exception {
        File destination = new File(tempDir, "output.zip");

        KoosteHttpClient.get(baseUri.resolve("/stops"), destination, "stops.xml", null);

        List<String> entries = zipEntryNames(destination);
        assertThat(entries.size(), is(1));
        assertThat(entries.get(0), is(equalTo("stops.xml")));
        assertThat(zipEntryContent(destination, "stops.xml"), is(equalTo("<stops>test</stops>")));
    }

    @Test
    void createsZipWithExtraEntries(@TempDir File tempDir) throws Exception {
        File destination = new File(tempDir, "output.zip");
        byte[] authoritiesXml = "<authorities>data</authorities>".getBytes(StandardCharsets.UTF_8);

        KoosteHttpClient.get(
            baseUri.resolve("/stops"),
            destination,
            "stops.xml",
            Map.of("authorities.xml", authoritiesXml));

        List<String> entries = zipEntryNames(destination);
        assertThat(entries.size(), is(2));
        // stops.xml must be first — URA backend (NetexFileStopPlaceLoader) reads only the first zip entry
        assertThat(entries.get(0), is(equalTo("stops.xml")));
        assertThat(entries.get(1), is(equalTo("authorities.xml")));
        assertThat(zipEntryContent(destination, "stops.xml"), is(equalTo("<stops>test</stops>")));
        assertThat(zipEntryContent(destination, "authorities.xml"), is(equalTo("<authorities>data</authorities>")));
    }

    @Test
    void throwsOnHttpError(@TempDir File tempDir) {
        File destination = new File(tempDir, "output.zip");

        assertThrows(RuntimeException.class, () ->
            KoosteHttpClient.get(baseUri.resolve("/error"), destination, "stops.xml", null));
    }

    private static List<String> zipEntryNames(File zipFile) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                zis.closeEntry();
            }
        }
        return names;
    }

    private static String zipEntryContent(File zipFile, String entryName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    zis.closeEntry();
                    return content;
                }
                zis.closeEntry();
            }
        }
        throw new AssertionError("Entry not found: " + entryName);
    }
}
