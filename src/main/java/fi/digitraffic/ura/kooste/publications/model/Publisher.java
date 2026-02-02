package fi.digitraffic.ura.kooste.publications.model;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

public class Publisher {
    private static final Pattern KOOSTE_EXPORT_PATTERN = Pattern.compile("^(rb_)?(?<codespace>.{3}).+(?<timestamp>\\d{14})\\.(?<fileExtension>.{3})$");
    private static final Pattern PETI_GTFS_ALL_EXPORT_PATTERN = Pattern.compile("^(?<codespace>(PETI))-GTFS-all-(?<timestamp>\\d{14})\\.zip$");
    private static final Pattern PETI_NETEX_ALL_PATTERN = Pattern.compile("^(?<codespace>(PETI))-NeTEx-all-(?<timestamp>\\d{14})\\.zip$");
    private static final Pattern PETI_NETEX_RAIL_PATTERN = Pattern.compile("^(?<codespace>(PETI))-NeTEx-rail-(?<timestamp>\\d{14})\\.zip$");

    private static final String S3_VACO_INPUT_PREFIX = "inbound/vaco/";
    private static final String S3_PETI_INPUT_PREFIX = "inbound/peti/";
    private static final String S3_UTTU_INPUT_PREFIX = "inbound/uttu/";

    public static final String NETEX_ARCHIVE_FILENAME = "stops.xml";

    public final static List<IPublisher> PUBLISHERS = List.of(
        new S3Publisher("URA", KOOSTE_EXPORT_PATTERN, S3_UTTU_INPUT_PREFIX, PublisherFormat.NETEX, true),
        new DownloadPublisher("PETI", PETI_NETEX_ALL_PATTERN, S3_PETI_INPUT_PREFIX, PublisherFormat.NETEX, "kooste.tasks.download.url.peti.all", "all", "PETI-NeTEx-all-{timestamp}.zip"),
        new DownloadPublisher("PETI", PETI_NETEX_RAIL_PATTERN, S3_PETI_INPUT_PREFIX, PublisherFormat.NETEX, "kooste.tasks.download.url.peti.rail", "rail", "PETI-NeTEx-rail-{timestamp}.zip"),
        new S3Publisher("PETI", PETI_GTFS_ALL_EXPORT_PATTERN, S3_VACO_INPUT_PREFIX, PublisherFormat.GTFS, false)
    );

    public enum PublisherFormat {
        NETEX("NeTEx"),
        GTFS("GTFS");

        public final String displayName;

        PublisherFormat(String displayName) {
            this.displayName = displayName;
        }
    }

    public interface IPublisher {
        String name();
        Pattern exportPattern();
        PublisherFormat format();
        String inputPrefix();
        boolean mergeContents();

        default String exportPrefix() {
            return deriveExportPrefix(name());
        }
    }

    public record S3Publisher(String name, Pattern exportPattern, String inputPrefix,
                              PublisherFormat format, boolean mergeContents) implements IPublisher {
    }

    public record DownloadPublisher(String name, Pattern exportPattern, String inputPrefix,
                                    PublisherFormat format, String urlProperty, String exportType, String exportTemplate) implements IPublisher {

        @Override
        public boolean mergeContents() {
            return false;
        }

        public URI getURI() {
            Config cfg = ConfigProvider.getConfig();
            return URI.create(cfg.getValue(urlProperty, String.class));
        }
    }

    /**
     * Derives export prefix from publisher name.
     * URA -> no.entur.uttu.export
     * PETI -> fi.digitraffic.ura.kooste.peti.export
     */
    private static String deriveExportPrefix(String name) {
        return switch (name) {
            case "URA" -> "no.entur.uttu.export";
            case "PETI" -> "fi.digitraffic.ura.kooste.peti.export";
            default -> throw new IllegalArgumentException("Unknown publisher: " + name);
        };
    }
}
