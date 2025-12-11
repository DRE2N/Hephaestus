package de.erethon.hephaestus;

import de.erethon.bedrock.database.BedrockDBConnection;
import de.erethon.hecate.Hecate;
import de.erethon.hephaestus.auctionhouse.AuctionHouseDatabaseManager;
import de.erethon.hephaestus.auctionhouse.AuctionHouseManager;
import de.erethon.hephaestus.auctionhouse.commands.AuctionHouseCommand;
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
import de.erethon.hephaestus.shops.ShopDatabaseManager;
import de.erethon.hephaestus.shops.ShopManager;
import de.erethon.hephaestus.shops.commands.ShopCommand;
import de.erethon.hephaestus.translations.TranslationManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Locale;

public final class Hephaestus extends JavaPlugin {

    public static Hephaestus INSTANCE;

    private final HItemLibrary itemLibrary;
    private final HBlockLibrary blockLibrary = new HBlockLibrary();
    private HEquipmentManager equipmentManager;
    private JobManager jobManager;
    private JobDatabaseManager jobDatabaseManager;
    private AuctionHouseDatabaseManager auctionHouseDatabaseManager;
    private AuctionHouseManager auctionHouseManager;
    private ShopDatabaseManager shopDatabaseManager;
    private ShopManager shopManager;
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
        return INSTANCE.getLibrary().register(ItemStack.fromBukkitCopy(stack), Identifier.parse(key));
    }

    @Override
    public void onEnable() {
        // Initialize translation system first
        translationManager = new TranslationManager(this);
        translationManager.initialize();

        // Initialize database and job system
        initializeJobSystem();

        // Initialize auction house system
        initializeAuctionHouse();

        HListener itemListener = new HListener(this);
        EquipmentListener equipmentListener = new EquipmentListener();
        Bukkit.getPluginManager().registerEvents(itemListener, this);
        Bukkit.getPluginManager().registerEvents(blockLibrary, this);
        Bukkit.getPluginManager().registerEvents(equipmentListener, this);
        itemLibrary.load();

        // Always check for and register missing vanilla items
        generateDefaultItems();

        // Initialize shop system AFTER items are loaded
        initializeShops();

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
        if (auctionHouseDatabaseManager != null) {
            auctionHouseDatabaseManager.close();
        }
        if (shopDatabaseManager != null) {
            shopDatabaseManager.close();
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
                Hephaestus.log("Failed to connect to database. Hecate will not work.");
                e.printStackTrace();
                return;
            }

            File jobsFile = new File(getDataFolder(), "jobs.yml");
            jobManager = new JobManager(jobDatabaseManager, jobsFile);

            // Initialize crafting system
            File recipesDirectory = new File(getDataFolder(), "recipes");
            recipeManager = new RecipeManager(recipesDirectory);
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

    public HEquipmentManager getEquipmentManager() {
        return equipmentManager;
    }

    public void registerTranslation(String key, Locale locale, String translation) {
        if (translation == null) {
            return;
        }
        translationManager.registerTranslation(key, locale, translation);
    }

    private void generateDefaultItems() {
        getLogger().info("Checking for missing vanilla items...");
        int newCount = 0;
        int totalCount = 0;
        for (Item item : BuiltInRegistries.ITEM.stream().toList()) {
            totalCount++;
            Identifier itemKey = BuiltInRegistries.ITEM.getKey(item);
            // Only register items that don't already exist
            if (itemLibrary.get(itemKey) == null) {
                itemLibrary.register(new ItemStack(item), itemKey);
                getLogger().info("Registered new vanilla item: " + itemKey);
                newCount++;
            }
        }
        if (newCount > 0) {
            getLogger().info("Registered " + newCount + " new vanilla items out of " + totalCount + " total.");
            itemLibrary.save();
        } else {
            getLogger().info("All " + totalCount + " vanilla items are already registered.");
        }
    }

    private void initializeAuctionHouse() {
        try {
            YamlConfiguration env = YamlConfiguration.loadConfiguration(new File(Bukkit.getWorldContainer(), "environment.yml"));
            try {
                BedrockDBConnection connection = new BedrockDBConnection(env.getString("dbUrl"),
                        env.getString("dbUser"),
                        env.getString("dbPassword"),
                        "org.postgresql.ds.PGSimpleDataSource");
                auctionHouseDatabaseManager = new AuctionHouseDatabaseManager(connection);
            } catch (Exception e) {
                getLogger().severe("Failed to connect to database for auction house: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            auctionHouseManager = new AuctionHouseManager(this, auctionHouseDatabaseManager);

            // Register auction house command
            AuctionHouseCommand ahCommand = new AuctionHouseCommand(this, auctionHouseManager);
            Bukkit.getCommandMap().register("hephaestus", ahCommand);

            getLogger().info("Auction house system initialized successfully");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize auction house: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeShops() {
        try {
            YamlConfiguration env = YamlConfiguration.loadConfiguration(new File(Bukkit.getWorldContainer(), "environment.yml"));
            try {
                BedrockDBConnection connection = new BedrockDBConnection(env.getString("dbUrl"),
                        env.getString("dbUser"),
                        env.getString("dbPassword"),
                        "org.postgresql.ds.PGSimpleDataSource");
                shopDatabaseManager = new ShopDatabaseManager(connection);
            } catch (Exception e) {
                getLogger().severe("Failed to connect to database for shops: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            File shopsDirectory = new File(getDataFolder(), "shops");
            shopManager = new ShopManager(this, shopDatabaseManager, shopsDirectory);

            // Register shop command
            ShopCommand shopCommand = new ShopCommand("shop");
            Bukkit.getCommandMap().register("hephaestus", shopCommand);

            getLogger().info("Shop system initialized successfully");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize shop system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public JobDatabaseManager getJobDatabaseManager() {
        return jobDatabaseManager;
    }

    public AuctionHouseDatabaseManager getAuctionHouseDatabaseManager() {
        return auctionHouseDatabaseManager;
    }

    public AuctionHouseManager getAuctionHouseManager() {
        return auctionHouseManager;
    }

    public ShopDatabaseManager getShopDatabaseManager() {
        return shopDatabaseManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
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

    public static void log(String message) {
        INSTANCE.getLogger().info(message);
    }
}
