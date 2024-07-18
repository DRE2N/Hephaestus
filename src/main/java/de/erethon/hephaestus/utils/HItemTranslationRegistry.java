package de.erethon.hephaestus.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HItemTranslationRegistry {

    // Item Stacks do not support translations, so we have to them ourselves.

    private final static Map<Locale, Map<String, String>> translations = new HashMap<>();

    public static void registerTranslation(Locale locale, String key, String translation) {
        Map<String, String> map = translations.computeIfAbsent(locale, k -> new HashMap<>());
        map.put(key, translation);
    }

    public static String getTranslation(Locale locale, String key) {
        Map<String, String> map = translations.get(locale);
        if (map == null) {
            return "<no translation found>";
        }
        return map.get(key);
    }
}
