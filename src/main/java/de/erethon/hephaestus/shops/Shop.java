package de.erethon.hephaestus.shops;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a shop with items that can be bought or sold.
 */
public class Shop {

    private final String id;
    private final String displayName;
    private final List<ShopItem> items;

    public Shop(String id, String displayName, List<ShopItem> items) {
        this.id = id;
        this.displayName = displayName;
        this.items = items;
    }

    public static Shop loadFromFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String id = file.getName().replace(".yml", "");
        String displayName = config.getString("name", id);
        List<ShopItem> items = new ArrayList<>();

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection != null) {
                    ShopItem shopItem = ShopItem.fromConfig(key, itemSection);
                    if (shopItem != null) {
                        items.add(shopItem);
                    }
                }
            }
        }

        return new Shop(id, displayName, items);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<ShopItem> getItems() {
        return items;
    }

    /**
     * Represents an item in a shop.
     */
    public static class ShopItem {
        private final String itemId;
        private final HItem hItem;
        private final TransactionType transactionType;
        private final long buyPrice;
        private final long sellPrice;
        private final int restockAmount;
        private final long restockTime; // in milliseconds

        public ShopItem(String itemId, HItem hItem, TransactionType transactionType,
                       long buyPrice, long sellPrice, int restockAmount, long restockTime) {
            this.itemId = itemId;
            this.hItem = hItem;
            this.transactionType = transactionType;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.restockAmount = restockAmount;
            this.restockTime = restockTime;
        }

        public static ShopItem fromConfig(String itemId, ConfigurationSection config) {
            HItem hItem = Hephaestus.INSTANCE.getLibrary().get(itemId);
            if (hItem == null) {
                Hephaestus.INSTANCE.getLogger().warning("Unknown item in shop config: " + itemId);
                return null;
            }

            String typeStr = config.getString("type", "BUY");
            TransactionType type = TransactionType.valueOf(typeStr.toUpperCase());

            long buyPrice = config.getLong("buyPrice", 0);
            long sellPrice = config.getLong("sellPrice", 0);
            int restockAmount = config.getInt("restockAmount", -1); // -1 means infinite
            long restockTimeMinutes = config.getLong("restockTime", 60); // 60 minutes (1 hour) default
            long restockTime = restockTimeMinutes * 60 * 1000; // Convert minutes to milliseconds

            return new ShopItem(itemId, hItem, type, buyPrice, sellPrice, restockAmount, restockTime);
        }

        public String getItemId() {
            return itemId;
        }

        public HItem getHItem() {
            return hItem;
        }

        public TransactionType getTransactionType() {
            return transactionType;
        }

        public long getBuyPrice() {
            return buyPrice;
        }

        public long getSellPrice() {
            return sellPrice;
        }

        public int getRestockAmount() {
            return restockAmount;
        }

        public long getRestockTime() {
            return restockTime;
        }

        public boolean hasInfiniteStock() {
            return restockAmount < 0;
        }
    }

    public enum TransactionType {
        BUY,    // Player can buy from shop
        SELL,   // Player can sell to shop
        BOTH    // Player can buy and sell
    }
}

