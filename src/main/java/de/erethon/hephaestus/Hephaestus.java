package de.erethon.hephaestus;

import de.erethon.bedrock.database.BedrockDBConnection;
import de.erethon.hecate.Hecate;
import de.erethon.hephaestus.blocks.HBlockLibrary;
import de.erethon.hephaestus.crafting.VanillaRecipeManager;
import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemLibrary;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.items.sets.HEquipmentManager;
import de.erethon.hephaestus.jobs.JobDatabaseManager;
import de.erethon.hephaestus.jobs.JobManager;
import de.erethon.hephaestus.jobs.commands.JobCommand;
import de.erethon.hephaestus.jobs.crafting.RecipeManager;
import de.erethon.hephaestus.jobs.crafting.PlayerCraftingProgress;
import de.erethon.hephaestus.jobs.crafting.commands.CraftingCommand;
import de.erethon.hephaestus.listeners.CraftingListener;
import de.erethon.hephaestus.listeners.EquipmentListener;
import de.erethon.hephaestus.listeners.HListener;
import de.erethon.hephaestus.translations.TranslationManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.MessageFormat;
import java.util.Locale;

public final class Hephaestus extends JavaPlugin {

    public static Hephaestus INSTANCE;

    private final HItemLibrary itemLibrary;
    private final HBlockLibrary blockLibrary = new HBlockLibrary();
    private HEquipmentManager equipmentManager;
    private JobManager jobManager;
    private JobDatabaseManager jobDatabaseManager;
    private RecipeManager recipeManager;
    private PlayerCraftingProgress playerCraftingProgress;
    private TranslationManager translationManager;
    private VanillaRecipeManager vanillaRecipeManager;

    public Hephaestus(HItemLibrary itemLibrary) {
        super();
        this.itemLibrary = itemLibrary;
        INSTANCE = this;
    }

    // Utility methods for quick access to the item library
    public static HItemStack getStack(ItemStack stack) {
        return INSTANCE.getLibrary().get(stack);
    }

    public static HItemStack getStack(org.bukkit.inventory.ItemStack stack) {
        return INSTANCE.getLibrary().get(stack);
    }

    public static HItem getItem(NamespacedKey key) {
        return INSTANCE.getLibrary().get(key);
    }

    public static HItem getItem(String key) {
        return INSTANCE.getLibrary().get(key);
    }

    public static HItem registerNewFromBukkit(String key, Material material) {
        return registerNewFromBukkit(key, new org.bukkit.inventory.ItemStack(material));
    }

    public static HItem registerNewFromBukkit(String key, org.bukkit.inventory.ItemStack stack) {
        return INSTANCE.getLibrary().register(ItemStack.fromBukkitCopy(stack), ResourceLocation.parse(key));
    }

    @Override
    public void onEnable() {
        // Initialize translation system first
        translationManager = new TranslationManager(this);
        translationManager.initialize();

        // Initialize database and job system
        initializeJobSystem();

        HListener itemListener = new HListener(this);
        EquipmentListener equipmentListener = new EquipmentListener();
        Bukkit.getPluginManager().registerEvents(itemListener, this);
        Bukkit.getPluginManager().registerEvents(blockLibrary, this);
        Bukkit.getPluginManager().registerEvents(equipmentListener, this);
        itemLibrary.load();
        if (itemLibrary.get(BuiltInRegistries.ITEM.getKey(Items.DIAMOND)) == null) {
            getLogger().warning("No vanilla items found. Generating default items...");
            generateDefaultItems();
        }

        // Initialize vanilla crafting system
        File vanillaRecipesFile = new File(getDataFolder(), "vanilla_recipes.yml");
        vanillaRecipeManager = new VanillaRecipeManager(this, vanillaRecipesFile);
        Bukkit.getPluginManager().registerEvents(new CraftingListener(this, vanillaRecipeManager), this);

        // let's hope Spellbook is ready here. We have to test this.
        File equipmentFile = new File(getDataFolder(), "equipment.yml");
        equipmentManager = new HEquipmentManager(equipmentFile);
        JobCommand jobCommand = new JobCommand("job");
        Bukkit.getCommandMap().register("jobsxl", jobCommand);

        // Register crafting command
        CraftingCommand craftingCommand = new CraftingCommand("craft");
        Bukkit.getCommandMap().register("jcrafting", craftingCommand);
    }

    @Override
    public void onDisable() {
        itemLibrary.save();
        if (jobDatabaseManager != null) {
            jobDatabaseManager.close();
        }
    }

    private void initializeJobSystem() {
        try {

            YamlConfiguration env = YamlConfiguration.loadConfiguration(new File(Bukkit.getWorldContainer(), "environment.yml"));
            try {
                BedrockDBConnection connection = new BedrockDBConnection(env.getString("dbUrl"),
                        env.getString("dbUser"),
                        env.getString("dbPassword"),
                        "org.postgresql.ds.PGSimpleDataSource");
                jobDatabaseManager = new JobDatabaseManager(connection);
            }
            catch (Exception e) {
                Hecate.log("Failed to connect to database. Hecate will not work.");
                e.printStackTrace();
                return;
            }

            File jobsFile = new File(getDataFolder(), "jobs.yml");
            jobManager = new JobManager(jobDatabaseManager, jobsFile);

            // Initialize crafting system
            File recipesFile = new File(getDataFolder(), "recipes.yml");
            recipeManager = new RecipeManager(recipesFile);
            playerCraftingProgress = new PlayerCraftingProgress(jobDatabaseManager);

            getLogger().info("Job system initialized successfully");
            getLogger().info("Crafting system initialized successfully");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize job system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public HItemLibrary getLibrary() {
        return itemLibrary;
    }

    public HBlockLibrary getBlockLibrary() {
        return blockLibrary;
    }

    public void registerTranslation(String key, Locale locale, String translation) {
        if (translation == null) {
            return;
        }
        translationManager.registerTranslation(key, locale, translation);
    }

    private void generateDefaultItems() {
        getLogger().info("Generating default items... This may take a while.");
        int count = 0;
        for (Item item : BuiltInRegistries.ITEM.stream().toList()) {
            itemLibrary.register(new ItemStack(item), BuiltInRegistries.ITEM.getKey(item));
            getLogger().info("Registered " + BuiltInRegistries.ITEM.getKey(item));
            count++;
        }
        getLogger().info("Generated " + count + " default items.");
        itemLibrary.save();
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public JobDatabaseManager getJobDatabaseManager() {
        return jobDatabaseManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public PlayerCraftingProgress getPlayerCraftingProgress() {
        return playerCraftingProgress;
    }

    public TranslationManager getTranslationManager() {
        return translationManager;
    }

    public VanillaRecipeManager getVanillaRecipeManager() {
        return vanillaRecipeManager;
    }
}
