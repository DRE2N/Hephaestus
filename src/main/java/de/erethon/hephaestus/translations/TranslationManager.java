package de.erethon.hephaestus.translations;

import de.erethon.hephaestus.Hephaestus;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
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
        loadTranslationFile("english.yml", "translations_en.yml", Locale.US);
        loadTranslationFile("german.yml", "translations_de.yml", Locale.GERMANY);

        plugin.getLogger().info("Loaded translations for supported locales");
    }

    private void loadTranslationFile(String fileName, String legacyFileName, Locale locale) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File translationFile = new File(dataFolder, fileName);
        migrateLegacyTranslationFile(translationFile, new File(dataFolder, legacyFileName));

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

    private void migrateLegacyTranslationFile(File translationFile, File legacyTranslationFile) {
        if (translationFile.exists() || !legacyTranslationFile.exists()) {
            return;
        }
        try {
            Files.copy(
                    legacyTranslationFile.toPath(),
                    translationFile.toPath(),
                    StandardCopyOption.COPY_ATTRIBUTES
            );
            plugin.getLogger().info("Migrated legacy translation file " + legacyTranslationFile.getName() + " to " + translationFile.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to migrate legacy translation file " + legacyTranslationFile.getName() + ": " + e.getMessage());
        }
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
