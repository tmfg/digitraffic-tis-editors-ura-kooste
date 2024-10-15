package fi.digitraffic.ura.kooste.i18n;

import io.quarkus.qute.TemplateExtension;

import java.text.MessageFormat;

/**
 * Use in Qute templates with
 * <blockquote>
 * {mf:format(i18n:['my.unformatted.message'], 'values', 'to', inject)}
 * </blockquote>
 */
@TemplateExtension(namespace = "mf")
public class MessageFormatTemplateExtensions {

    static String format(String pattern, Object... params) {
        return MessageFormat.format(pattern, params);
    }
}
