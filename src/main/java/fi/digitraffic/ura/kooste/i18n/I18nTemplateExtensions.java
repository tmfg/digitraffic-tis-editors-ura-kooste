package fi.digitraffic.ura.kooste.i18n;

import io.quarkus.qute.TemplateExtension;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static io.quarkus.qute.TemplateExtension.ANY;

@TemplateExtension(namespace = "i18n")
public class I18nTemplateExtensions {

    @TemplateExtension(namespace = "i18n", matchName = ANY, matchRegex = ".*")
    static String message(
        String key,
        @TemplateExtension.TemplateAttribute("locale") Object localeAttr,
        @TemplateExtension.TemplateAttribute("bundle") Object bundleAttr) {
        Locale locale = localeAttr instanceof Locale ? (Locale) localeAttr : Locale.getDefault();
        // TODO: Qute uses lowercase/dashed as convention, but ResourceBundles work better with dot-delimited.
        //       Translating between the two here would be useful.
        ResourceBundle defaultBundle = ResourceBundle.getBundle("i18n/kooste", locale);
        ResourceBundle pageBundle = ResourceBundle.getBundle("i18n/" + bundleAttr.toString(), locale);
        try {
            return pageBundle.getString(key);
        } catch (MissingResourceException e) {
            return defaultBundle.getString(key);
        }
    }
}
