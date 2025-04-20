package de.erethon.hephaestus.items.upgrades;

import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.utils.HLoreEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.List;

public class HRolledUpgrade implements HLoreEntry {

    private final HItemUpgrade upgrade;
    private final HItemStack stack;
    private CompoundTag values;

    public HRolledUpgrade(HItemStack stack, HItemUpgrade upgrade, @Nullable CompoundTag values) {
        this.upgrade = upgrade;
        this.stack = stack;
        this.values = values;
    }

    public String getId() {
        return upgrade.getId();
    }

    public HItemUpgrade getUpgrade() {
        return upgrade;
    }

    public HItemStack getStack() {
        return stack;
    }

    public void setValues(CompoundTag values) {
        this.values = values;
    }

    public CompoundTag getValues() {
        return values;
    }

    @Override
    public Component getLoreLine() {
        Component c = Component.text("+", NamedTextColor.GREEN);
        return c.append(Component.translatable("hephaestus.upgrade." + upgrade.getId() + ".name"));
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", upgrade.getId());
        if (values != null) {
            tag.put("values", values);
        } else {
            tag.put("values", new CompoundTag());
        }
        return tag;
    }

    public static @Nullable HRolledUpgrade fromNBT(HItemStack stack, CompoundTag tag) {
        HItemUpgrade upgrade = stack.getItem().getLibrary().getUpgrade(tag.getString("id").get());
        if (upgrade == null) {
            return null;
        }
        HRolledUpgrade rolledUpgrade = new HRolledUpgrade(stack, upgrade, tag.getCompound("values").get());
        return rolledUpgrade;
    }
}
