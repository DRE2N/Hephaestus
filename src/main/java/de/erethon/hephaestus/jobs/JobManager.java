package de.erethon.hephaestus.jobs;

import de.erethon.bedrock.chat.MessageUtil;
import de.erethon.hephaestus.Hephaestus;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class JobManager {

    private final Map<String, HJob> jobs = new HashMap<>();
    private final JobDatabaseManager databaseManager;
    private final File configFile;

    public JobManager(JobDatabaseManager databaseManager, File configFile) {
        this.databaseManager = databaseManager;
        this.configFile = configFile;
        loadJobsFromConfig();
    }

    private void loadJobsFromConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection jobsSection = config.getConfigurationSection("jobs");

        if (jobsSection == null) {
            MessageUtil.log("No jobs section found in jobs.yml");
            return;
        }

        jobs.clear();
        for (String key : jobsSection.getKeys(false)) {
            ConfigurationSection jobSection = jobsSection.getConfigurationSection(key);
            if (jobSection != null) {
                try {
                    HJob job = HJob.deserialize(jobSection);
                    jobs.put(job.getId(), job);

                    registerJobTranslations(job);

                    MessageUtil.log("Loaded job: " + job.getId());
                } catch (Exception e) {
                    MessageUtil.log("Failed to load job: " + key + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        MessageUtil.log("Loaded " + jobs.size() + " jobs from configuration");
    }

    private void registerJobTranslations(HJob job) {
        if (Hephaestus.INSTANCE != null && Hephaestus.INSTANCE.getTranslationManager() != null) {
            for (Map.Entry<String, String> entry : job.getNameTranslations().entrySet()) {
                String locale = entry.getKey();
                String translation = entry.getValue();
                java.util.Locale javaLocale = getLocaleFromString(locale);
                String translationKey = "hephaestus.job." + job.getId() + ".name";
                Hephaestus.INSTANCE.getTranslationManager().registerTranslation(translationKey, javaLocale, translation);
            }

            for (Map.Entry<String, String> entry : job.getDescriptionTranslations().entrySet()) {
                String locale = entry.getKey();
                String translation = entry.getValue();
                java.util.Locale javaLocale = getLocaleFromString(locale);
                String translationKey = "hephaestus.job." + job.getId() + ".description";
                Hephaestus.INSTANCE.getTranslationManager().registerTranslation(translationKey, javaLocale, translation);
            }
        }
    }

    private java.util.Locale getLocaleFromString(String locale) {
        switch (locale.toLowerCase()) {
            case "en", "english" -> {
                return java.util.Locale.US;
            }
            case "de", "german", "deutsch" -> {
                return java.util.Locale.GERMANY;
            }
            default -> {
                return java.util.Locale.US;
            }
        }
    }

    // Some defaults so we aren't as clueless as with JXL
    private void createDefaultConfig() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection jobsSection = config.createSection("jobs");

        ConfigurationSection minerSection = jobsSection.createSection("miner");
        minerSection.set("id", "miner");
        minerSection.set("description", "Extract valuable resources from the earth");
        minerSection.set("maxLevel", 100);

        ConfigurationSection minerTranslations = minerSection.createSection("translations");
        minerTranslations.set("name.en", "Miner");
        minerTranslations.set("name.de", "Bergarbeiter");
        minerTranslations.set("description.en", "Extract valuable resources from the earth");
        minerTranslations.set("description.de", "Wertvolle Ressourcen aus der Erde abbauen");

        ConfigurationSection smithSection = jobsSection.createSection("smith");
        smithSection.set("id", "smith");
        smithSection.set("description", "Forge weapons and tools from raw materials");
        smithSection.set("maxLevel", 100);

        ConfigurationSection smithTranslations = smithSection.createSection("translations");
        smithTranslations.set("name.en", "Smith");
        smithTranslations.set("name.de", "Schmied");
        smithTranslations.set("description.en", "Forge weapons and tools from raw materials");
        smithTranslations.set("description.de", "Waffen und Werkzeuge aus Rohstoffen schmieden");

        ConfigurationSection alchemistSection = jobsSection.createSection("alchemist");
        alchemistSection.set("id", "alchemist");
        alchemistSection.set("description", "Brew potions and create magical items");
        alchemistSection.set("maxLevel", 100);

        ConfigurationSection alchemistTranslations = alchemistSection.createSection("translations");
        alchemistTranslations.set("name.en", "Alchemist");
        alchemistTranslations.set("name.de", "Alchemist");
        alchemistTranslations.set("description.en", "Brew potions and create magical items");
        alchemistTranslations.set("description.de", "Tränke brauen und magische Gegenstände herstellen");

        try {
            config.save(configFile);
            MessageUtil.log("Created default jobs configuration at: " + configFile.getPath());
        } catch (IOException e) {
            MessageUtil.log("Failed to create default jobs configuration: " + e.getMessage());
        }
    }

    public Collection<HJob> getAllJobs() {
        return new ArrayList<>(jobs.values());
    }

    public HJob getJob(String id) {
        return jobs.get(id);
    }

    public CompletableFuture<Boolean> setCharacterJob(UUID characterUuid, String jobId) {
        if (!jobs.containsKey(jobId)) {
            return CompletableFuture.completedFuture(false);
        }

        return databaseManager.setCharacterJob(characterUuid, jobId)
                .thenApply(v -> true)
                .exceptionally(throwable -> {
                    MessageUtil.log("Failed to set character job: " + throwable.getMessage());
                    return false;
                });
    }

    public CompletableFuture<HJob> getCharacterJob(UUID characterUuid) {
        return databaseManager.getCharacterJobId(characterUuid)
                .thenApply(optionalJobId -> optionalJobId.map(jobs::get).orElse(null));
    }

    public CompletableFuture<Boolean> removeCharacterJob(UUID characterUuid) {
        return databaseManager.removeCharacterJob(characterUuid)
                .thenApply(v -> true)
                .exceptionally(throwable -> {
                    MessageUtil.log("Failed to remove character job: " + throwable.getMessage());
                    return false;
                });
    }

    public CompletableFuture<Integer> getJobPlayerCount(String jobId) {
        return databaseManager.getJobPlayerCount(jobId);
    }

    public void reloadJobs() {
        loadJobsFromConfig();
    }

    public boolean hasJob(String jobId) {
        return jobs.containsKey(jobId);
    }
}
