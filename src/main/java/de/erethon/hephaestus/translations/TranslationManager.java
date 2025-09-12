package de.erethon.hephaestus.translations;

import de.erethon.hephaestus.Hephaestus;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TranslationManager {

    private final Hephaestus plugin;
    private final TranslationRegistry translationRegistry;
    private boolean translationSourceAdded = false;

    public TranslationManager(Hephaestus plugin) {
        this.plugin = plugin;
        this.translationRegistry = TranslationRegistry.create(Key.key("hephaestus"));
    }

    public void initialize() {
        loadTranslations();

        if (!translationSourceAdded) {
            GlobalTranslator.translator().addSource(translationRegistry);
            translationSourceAdded = true;
            plugin.getLogger().info("Hephaestus translation source registered.");
        }
    }

    private void loadTranslations() {
        loadTranslationFile("translations_en.yml", Locale.US);
        loadTranslationFile("translations_de.yml", Locale.GERMANY);

        plugin.getLogger().info("Loaded translations for supported locales");
    }

    private void loadTranslationFile(String fileName, Locale locale) {
        File dataFolder = plugin.getDataFolder();
        File translationFile = new File(dataFolder, fileName);

        YamlConfiguration config;
        if (!translationFile.exists()) {
            plugin.saveResource(fileName, false);
        }

        config = YamlConfiguration.loadConfiguration(translationFile);

        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaultConfig);
        }

        registerTranslationsFromConfig(config, "", locale);

        plugin.getLogger().info("Loaded " + fileName + " for locale " + locale.toString());
    }

    private void registerTranslationsFromConfig(YamlConfiguration config, String prefix, Locale locale) {
        Set<String> keys = config.getKeys(true);

        for (String key : keys) {
            Object value = config.get(key);

            if (value instanceof String) {
                String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                String translationKey = "hephaestus." + fullKey;

                registerTranslation(translationKey, locale, (String) value);
            }
        }
    }

    public void registerTranslation(String key, Locale locale, String translation) {
        if (translation == null) {
            return;
        }
        if (translationRegistry.contains(key, locale)) {
            return;
        }
        translationRegistry.register(key, locale, new MessageFormat(translation));
    }

    public TranslationRegistry getTranslationRegistry() {
        return translationRegistry;
    }

    public static MessageFormat translate(String key, Locale locale) {
        return GlobalTranslator.translator().translate(key, locale);
    }
}
