package de.erethon.hephaestus.translations;

import de.erethon.hephaestus.Hephaestus;
import net.kyori.adventure.key.Key;
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

        // Register our translation registry as a source
        if (!translationSourceAdded) {
            GlobalTranslator.translator().addSource(translationRegistry);
            translationSourceAdded = true;
            plugin.getLogger().info("Hephaestus translation source registered.");
        }
    }

    private void loadTranslations() {
        // Load English translations
        loadTranslationFile("translations_en.yml", Locale.US);

        // Load German translations
        loadTranslationFile("translations_de.yml", Locale.GERMANY);

        plugin.getLogger().info("Loaded translations for supported locales");
    }

    private void loadTranslationFile(String fileName, Locale locale) {
        File dataFolder = plugin.getDataFolder();
        File translationFile = new File(dataFolder, fileName);

        YamlConfiguration config;

        // If file doesn't exist in data folder, copy from resources and load it
        if (!translationFile.exists()) {
            plugin.saveResource(fileName, false);
        }

        // Load the file
        config = YamlConfiguration.loadConfiguration(translationFile);

        // Also load defaults from resources as fallback
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaultConfig);
        }

        // Register all translations
        registerTranslationsFromConfig(config, "", locale);

        plugin.getLogger().info("Loaded " + fileName + " for locale " + locale.toString());
    }

    private void registerTranslationsFromConfig(YamlConfiguration config, String prefix, Locale locale) {
        Set<String> keys = config.getKeys(true);

        for (String key : keys) {
            Object value = config.get(key);

            // Only process leaf nodes (actual values, not sections)
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
        translationRegistry.register(key, locale, new MessageFormat(translation));
    }

    public TranslationRegistry getTranslationRegistry() {
        return translationRegistry;
    }
}
