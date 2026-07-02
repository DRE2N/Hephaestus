package de.erethon.hephaestus.items;

import de.erethon.hephaestus.Hephaestus;
import net.minecraft.resources.Identifier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class HItemUtil {

    private HItemUtil() {
    }

    public static String getItemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        HItemStack hStack = Hephaestus.getStack(stack);
        if (hStack != null && hStack.getItem() != null && hStack.getItem().getKey() != null) {
            return hStack.getItem().getKey().toString();
        }
        return stack.getType().getKey().toString();
    }

    public static boolean matchesItemId(String itemId, ItemStack stack) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String actualId = getItemId(stack);
        return itemId.equals(actualId);
    }

    public static ItemStack createItemStack(String itemId, int amount) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        HItem hItem = Hephaestus.getItem(itemId);
        if (hItem != null) {
            return hItem.createStack(Math.max(1, amount)).getBukkitStack();
        }
        Material material = materialFromId(itemId);
        return material == null || material.isAir() ? null : new ItemStack(material, Math.max(1, amount));
    }

    public static Material materialFromId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            Identifier id = Identifier.parse(itemId);
            if (!id.getNamespace().equals("minecraft")) {
                return null;
            }
            return Material.getMaterial(id.getPath().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
            return Material.getMaterial(itemId.toUpperCase(java.util.Locale.ROOT));
        }
    }
}
