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
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection jobsSection = config.getConfigurationSection("jobs");

        if (jobsSection == null) {
            Hephaestus.log("No jobs section found in jobs.yml");
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

                    Hephaestus.log("Loaded job: " + job.getId());
                } catch (Exception e) {
                    Hephaestus.log("Failed to load job: " + key + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        Hephaestus.log("Loaded " + jobs.size() + " jobs from configuration");
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
                    Hephaestus.log("Failed to set character job: " + throwable.getMessage());
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
                    Hephaestus.log("Failed to remove character job: " + throwable.getMessage());
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
