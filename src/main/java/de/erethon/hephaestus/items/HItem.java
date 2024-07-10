package de.erethon.hephaestus.items;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class HItem {

    private final File file;
    private NamespacedKey key;
    private Item baseItem;
    private DataComponentPatch patch;

    public HItem(File file) {
        this.file = file;
        load();
    }

    public HItem(NamespacedKey key, File file) {
        this.file = file;
    }

    public NamespacedKey getKey() {
        return key;
    }

    public ItemStack update(ItemStack stack) {
        stack.applyComponents(patch);
        if (stack.getItem() != baseItem) {
            stack.setItem(baseItem); // Is there a better way?
        }
        return stack;
    }

    private void load() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        key = NamespacedKey.fromString(config.getString("key", "hephaestus:default"));
        baseItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(config.getString("baseItem", "minecraft:stone")));
        ItemParser parser = new ItemParser(MinecraftServer.getDefaultRegistryAccess());
        ItemParser.ItemResult result;
        try {
            result = parser.parse(new StringReader(config.getString("patch", "{}")));
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(e);
        }
        patch = result.components();
    }

}
