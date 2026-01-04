package de.erethon.hephaestus.items;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.orbs.OrbColor;
import de.erethon.hephaestus.items.upgrades.HRolledUpgrade;
import de.erethon.tyche.EconomyService;
import de.erethon.tyche.TychePlugin;
import de.erethon.tyche.models.OwnerType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Smoker;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.awt.Color.gray;

public class Grindstone {

    private static final NamespacedKey SUCCESS_CHANCE_KEY = new NamespacedKey(Hephaestus.INSTANCE, "grindstone_success_chance");
    private static final NamespacedKey PRICE_PER_ORB_KEY = new NamespacedKey(Hephaestus.INSTANCE, "grindstone_price_per_orb");
    private static final NamespacedKey FULL_REFUND_CHANCE_KEY = new NamespacedKey(Hephaestus.INSTANCE, "grindstone_full_refund_chance");

    private final Hephaestus plugin = Hephaestus.INSTANCE;
    private final EconomyService eco = TychePlugin.getEconomyService();
    private static final String RED_SHARDS_ID = "erethon:red_orb_shard";
    private static final String BLUE_SHARDS_ID = "erethon:blue_orb_shard";
    private static final String GREEN_SHARDS_ID = "erethon:green_orb_shard";

    private final Smoker block;
    private final PersistentDataContainer data;

    private static final Set<UUID> waitingForConfirmation = new HashSet<>();


    public Grindstone(final Smoker block) {
        this.block = block;
        this.data = block.getPersistentDataContainer();
    }

    public float getSuccessChance() {
        return data.getOrDefault(SUCCESS_CHANCE_KEY, PersistentDataType.FLOAT, 0.1f);
    }

    public void setSuccessChance(float chance) {
        data.set(SUCCESS_CHANCE_KEY, PersistentDataType.FLOAT, chance);
        block.update();
    }

    public int getPricePerOrb() {
        return data.getOrDefault(PRICE_PER_ORB_KEY, PersistentDataType.INTEGER, 10);
    }

    public void setPricePerOrb(int price) {
        data.set(PRICE_PER_ORB_KEY, PersistentDataType.INTEGER, price);
        block.update();
    }

    public float getFullRefundChance() {
        return data.getOrDefault(FULL_REFUND_CHANCE_KEY, PersistentDataType.FLOAT, 0.0f);
    }

    public void setFullRefundChance(float chance) {
        data.set(FULL_REFUND_CHANCE_KEY, PersistentDataType.FLOAT, chance);
        block.update();
    }

    public void onRightClick(Player player, ItemStack item) {
        UUID playerId = player.getUniqueId();
        if (waitingForConfirmation.contains(playerId)) {
            // Confirmed
            waitingForConfirmation.remove(playerId);
            removeOrbs(item, player);
        } else {
            // First click, ask for confirmation
            waitingForConfirmation.add(playerId);
            player.sendRichMessage("<red>Are you sure you want to remove all orbs from this item? Right-click again to confirm.");
            player.sendRichMessage("<gray>Cost: " + getPricePerOrb() + " per orb. Full refund: " + (int)(getFullRefundChance() * 100) + "%, Shard drop: " + (int)(getSuccessChance() * 100) + "%");
        }
    }

    private void removeOrbs(ItemStack item, Player player) {
        HItemStack stack = Hephaestus.getStack(item);
        if (stack == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        List<HRolledUpgrade> upgrades = stack.getUpgrades(); // This returns a copy
        float successChance = getSuccessChance();
        float fullRefundChance = getFullRefundChance();
        int pricePerOrb = getPricePerOrb();
        long money = eco.getBalance(playerId, OwnerType.PLAYER, "herone").join();
        Location dropLocation = block.getLocation().add(0.5, 1, 0.5);

        int orbsRemoved = 0;
        int shardsDropped = 0;
        int orbsRefunded = 0;

        for (HRolledUpgrade upgrade : upgrades) {
            // Check if this upgrade has a source item (is from an orb)
            String sourceItemId = upgrade.getSourceItemId();
            if (sourceItemId == null) {
                continue; // Skip upgrades not from orbs
            }

            // Check payment
            if (money >= pricePerOrb) {
                money -= pricePerOrb;
                eco.withdraw(playerId, OwnerType.PLAYER, "herone", pricePerOrb, "Grindstone orb removal", playerId).join();
            } else {
                player.sendRichMessage("<red>You don't have enough money to remove more orbs");
                break;
            }

            // Remove the upgrade
            stack.removeUpgrade(upgrade);
            orbsRemoved++;

            // First check for full refund (gives the actual orb back)
            if (Math.random() < fullRefundChance) {
                // Give back the actual orb item with its original level and rarity
                HItem orbItem = plugin.getLibrary().get(sourceItemId);
                if (orbItem != null) {
                    int orbLevel = upgrade.getSourceItemLevel();
                    String rarityStr = upgrade.getSourceItemRarity();
                    HRarity orbRarity = HRarity.COMMON;
                    if (rarityStr != null) {
                        try {
                            orbRarity = HRarity.valueOf(rarityStr);
                        } catch (IllegalArgumentException ignored) {}
                    }
                    ItemStack orb = orbItem.createStack(1, orbLevel, null, orbRarity).getBukkitStack();
                    Item droppedItem = dropLocation.getWorld().dropItemNaturally(dropLocation, orb, i -> {
                        i.setVelocity(i.getVelocity().multiply(0.2));
                        i.setCanMobPickup(false);
                        i.setVisibleByDefault(false);
                        i.setOwner(playerId);
                    });
                    player.showEntity(Hephaestus.INSTANCE, droppedItem);
                    orbsRefunded++;
                }
            }
            // If no full refund, check for shard drop
            else if (Math.random() < successChance) {
                // Determine shard color based on source orb
                String shardId = getShardIdFromOrbId(sourceItemId);
                if (shardId != null) {
                    HItem shardItem = plugin.getLibrary().get(shardId);
                    if (shardItem != null) {
                        ItemStack shard = shardItem.createStack(1, stack.getItemLevel()).getBukkitStack();
                        Item droppedItem = dropLocation.getWorld().dropItemNaturally(dropLocation, shard, i -> {
                            i.setVelocity(i.getVelocity().multiply(0.2));
                            i.setCanMobPickup(false);
                            i.setVisibleByDefault(false);
                            i.setOwner(playerId);
                        });
                        player.showEntity(Hephaestus.INSTANCE, droppedItem);
                        shardsDropped++;
                    }
                }
            }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 0.5f);

        // Update the player's item in hand with the modified stack
        player.getInventory().setItemInMainHand(stack.getBukkitStack());

        if (orbsRemoved > 0) {
            player.sendRichMessage("<green>Removed " + orbsRemoved + " orb(s). Refunded: " + orbsRefunded + ", Shards: " + shardsDropped + ". Paid " + (orbsRemoved * pricePerOrb) + ".");
        } else {
            player.sendRichMessage("<yellow>No orb upgrades found on this item.");
        }
    }

    private String getShardIdFromOrbId(String orbId) {
        HItem orbItem = plugin.getLibrary().get(orbId);
        if (orbItem == null || !orbItem.isOrbItem()) {
            return null;
        }

        OrbColor color = orbItem.getOrbColor();
        if (color == null) {
            return null;
        }

        return switch (color) {
            case RED -> RED_SHARDS_ID;
            case BLUE -> BLUE_SHARDS_ID;
            case GREEN -> GREEN_SHARDS_ID;
            case PRISMATIC -> {
                // Randomly select one of the three shard colors
                OrbColor[] shardColors = {OrbColor.RED, OrbColor.BLUE, OrbColor.GREEN};
                OrbColor randomColor = shardColors[(int) (Math.random() * shardColors.length)];
                yield switch (randomColor) {
                    case RED -> RED_SHARDS_ID;
                    case BLUE -> BLUE_SHARDS_ID;
                    case GREEN -> GREEN_SHARDS_ID;
                    default -> null;
                };
            }
        };
    }

    public static boolean isGrindstone(org.bukkit.block.Block block) {
        if (block.getType() != Material.SMOKER) {
            return false;
        }
        Smoker smoker = (Smoker) block.getState();
        return smoker.getPersistentDataContainer().has(SUCCESS_CHANCE_KEY);
    }

    public static Grindstone fromBlock(org.bukkit.block.Block block) {
        if (!isGrindstone(block)) {
            return null;
        }
        return new Grindstone((Smoker) block.getState());
    }

}

