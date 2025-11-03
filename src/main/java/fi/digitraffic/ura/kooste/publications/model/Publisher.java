package fi.digitraffic.ura.kooste.publications.model;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

public class Publisher {
    private static final Pattern KOOSTE_EXPORT_PATTERN = Pattern.compile("^(rb_)?(?<codespace>.{3}).+(?<timestamp>\\d{14})\\.(?<fileExtension>.{3})$");
    private static final Pattern PETI_NETEX_EXPORT_PATTERN = Pattern.compile("^(?<codespace>(PETI))-NeTEx-(?<timestamp>\\d{14})\\.zip$");
    public static final Pattern PETI_GTFS_EXPORT_PATTERN = Pattern.compile("^(?<codespace>(PETI))-GTFS-(?<timestamp>\\d{14})\\.zip$");
    public static final String S3_VACO_INPUT_PREFIX = "inbound/vaco/";


    public final static List<IPublisher> PUBLISHERS = List.of(
        new S3Publisher("URA", KOOSTE_EXPORT_PATTERN, "inbound/uttu/", "no.entur.uttu.export", PublisherFormat.NETEX, true),
        new DownloadPublisher("PETI", PETI_NETEX_EXPORT_PATTERN, "inbound/peti/", "fi.digitraffic.ura.kooste.peti.export", PublisherFormat.NETEX, false, "stops.xml", "kooste.tasks.download.url.peti"),
        new S3Publisher("PETI", PETI_GTFS_EXPORT_PATTERN, S3_VACO_INPUT_PREFIX, "fi.digitraffic.ura.kooste.peti.export", PublisherFormat.GTFS, false)
    );

    public enum PublisherType {
        S3,
        DOWNLOAD;
    }

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
        String exportPrefix();
        PublisherFormat format();
        String inputPrefix();
        boolean mergeContents();
    }

    public record S3Publisher(String name, Pattern exportPattern, String inputPrefix, String exportPrefix,
                              PublisherFormat format, boolean mergeContents) implements IPublisher {
    }

    public record DownloadPublisher(String name, Pattern exportPattern, String inputPrefix, String exportPrefix,
                                    PublisherFormat format, boolean mergeContents, String fileName, String urlProperty) implements IPublisher {


        public URI getURI() {
            Config cfg = ConfigProvider.getConfig();
            String env = cfg.getValue("kooste.environment", String.class);
            return URI.create(cfg.getValue(urlProperty + "." + env, String.class));
        }
    }
}


