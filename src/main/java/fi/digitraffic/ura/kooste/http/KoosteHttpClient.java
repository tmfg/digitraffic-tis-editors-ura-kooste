package fi.digitraffic.ura.kooste.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class KoosteHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(KoosteHttpClient.class);

    /**
     *  GET resource in URI and stream contents into ZIP-archive.
     *
     * @param uri URI to download
     * @param fileName Filename inside ZIP-archive
     */
    public static void get(URI uri, File destination, String fileName) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (!KoosteHttpClient.isResponseOk(response.statusCode())) {
                    logger.error("Response status code is {} for uri {}", response.statusCode(), uri);
                    throw new RuntimeException(String.format("Response status code is %d for uri %s", response.statusCode(), uri));
                }
                try (InputStream inputStream = response.body();
                     FileOutputStream fileOutputStream = new FileOutputStream(destination);
                     ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
                    ZipEntry zipEntry = new ZipEntry(fileName);
                    zipOutputStream.putNextEntry(zipEntry);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        zipOutputStream.write(buffer, 0, bytesRead);
                    }
                    zipOutputStream.closeEntry();
                }
                logger.info("File {} downloaded and archived successfully to: {}", uri, destination);
            } catch (Exception e) {
                logger.error("Failed to download and archive file {} to {}", uri, destination, e);
                throw e;
            }
        }
    }

    /**
     *  GET ZIP-resource in URI and stream contents to disk.
     *
     * @param uri URI to download
     */
    public static void get(URI uri, File destination, Map<String, String> headers) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);
            headers.forEach(requestBuilder::header);
            HttpRequest request = requestBuilder.build();
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream inputStream = response.body();
                     FileOutputStream fileOutputStream = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead);
                    }
                }
                logger.info("File {} downloaded successfully to: {}", uri, destination);
            } catch (Exception e) {
                logger.error("Failed to download file {} to {}", uri, destination, e);
                throw e;
            }
        }
    }

    private static boolean isResponseOk(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     *  GET resource in URI and stream contents into ZIP-archive.
     *
     * @param uri URI to download
     */
    public static byte[] get(URI uri, Map<String, String> headers) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);
            return receive(uri, headers, client, requestBuilder);
        }
    }

    /**
     *  Make HTTP POST request.
     *
     * @param uri URI to download
     * @return Created file
     */
    public static byte[] post(URI uri, byte[] body, Map<String, String> headers) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .header("Content-Type", "application/json; charset=utf-8")
                .uri(uri);

            return receive(uri, headers, client, requestBuilder);
        }
    }

    private static byte[] receive(URI uri, Map<String, String> headers, HttpClient client, HttpRequest.Builder requestBuilder) throws IOException, InterruptedException {
        headers.forEach(requestBuilder::header);
        HttpRequest request = requestBuilder.build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream inputStream = response.body()) {
                return inputStream.readAllBytes();
            }
        } catch (Exception e) {
            logger.error("Failed to POST {}", uri, e);
            throw e;
        }
    }
}
