package fi.digitraffic.ura.kooste.i18n;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle(locale = "en")
public interface Messages {

    @Message("Publications")
    String page_name();

    @Message("Publications")
    String page_header();

    @Message("Kooste / Publications")
    String page_title(String name);

    @Message("Codespace")
    String table_header_codespace();

    @Message("Label")
    String table_header_label();

    @Message("Last updated")
    String table_header_timestamp();

    @Message("Format")
    String table_header_format();

    @Message("Download")
    String table_header_downloadLink();

    @Message("MMMM d, yyyy 'at' h:mm:ss a")
    String publication_timestamp_format();
}

