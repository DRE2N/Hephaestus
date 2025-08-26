package de.erethon.hephaestus.jobs;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HJob {

    private final String id;
    private final String description;
    private final int maxLevel;
    private final Map<String, Object> properties;
    private final Map<String, String> nameTranslations;
    private final Map<String, String> descriptionTranslations;

    public HJob(String id, String description, int maxLevel,
                Map<String, Object> properties, Map<String, String> nameTranslations, Map<String, String> descriptionTranslations) {
        this.id = id;
        this.description = description;
        this.maxLevel = maxLevel;
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        this.nameTranslations = nameTranslations != null ? new HashMap<>(nameTranslations) : new HashMap<>();
        this.descriptionTranslations = descriptionTranslations != null ? new HashMap<>(descriptionTranslations) : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Component getTranslatableName() {
        return Component.translatable("hephaestus.job." + id + ".name");
    }

    public Component getTranslatableDescription() {
        return Component.translatable("hephaestus.job." + id + ".description");
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public Map<String, String> getNameTranslations() {
        return new HashMap<>(nameTranslations);
    }

    public Map<String, String> getDescriptionTranslations() {
        return new HashMap<>(descriptionTranslations);
    }

    public void serialize(ConfigurationSection section) {
        section.set("id", id);
        section.set("description", description);
        section.set("maxLevel", maxLevel);

        if (!properties.isEmpty()) {
            ConfigurationSection propertiesSection = section.createSection("properties");
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                propertiesSection.set(entry.getKey(), entry.getValue());
            }
        }

        if (!nameTranslations.isEmpty()) {
            ConfigurationSection translationsSection = section.createSection("translations");
            for (Map.Entry<String, String> entry : nameTranslations.entrySet()) {
                translationsSection.set("name." + entry.getKey(), entry.getValue());
            }
        }

        if (!descriptionTranslations.isEmpty()) {
            ConfigurationSection translationsSection = section.createSection("translations");
            for (Map.Entry<String, String> entry : descriptionTranslations.entrySet()) {
                translationsSection.set("description." + entry.getKey(), entry.getValue());
            }
        }
    }

    public static HJob deserialize(ConfigurationSection section) {
        String id = section.getString("id");
        String description = section.getString("description");
        List<String> allowedItems = section.getStringList("allowedItems");
        int maxLevel = section.getInt("maxLevel", 100);

        Map<String, Object> properties = new HashMap<>();
        ConfigurationSection propertiesSection = section.getConfigurationSection("properties");
        if (propertiesSection != null) {
            for (String key : propertiesSection.getKeys(false)) {
                properties.put(key, propertiesSection.get(key));
            }
        }

        Map<String, String> nameTranslations = new HashMap<>();
        Map<String, String> descriptionTranslations = new HashMap<>();
        ConfigurationSection translationsSection = section.getConfigurationSection("translations");
        if (translationsSection != null) {
            for (String key : translationsSection.getKeys(false)) {
                if (key.startsWith("name.")) {
                    nameTranslations.put(key.substring(5), translationsSection.getString(key));
                } else if (key.startsWith("description.")) {
                    descriptionTranslations.put(key.substring(12), translationsSection.getString(key));
                }
            }
        }

        return new HJob(id, description, maxLevel, properties, nameTranslations, descriptionTranslations);
    }

    @Override
    public String toString() {
        return "HJob{id='" + id + "'}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        HJob hJob = (HJob) obj;
        return id.equals(hJob.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
