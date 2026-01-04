package de.erethon.hephaestus.items.upgrades;

import de.erethon.hephaestus.items.HItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HRolledUpgrade {

    private final HItemUpgrade upgrade;
    private final HItemStack stack;
    private CompoundTag values;
    private int socketIndex = -1; // -1 means unattached/legacy
    private @Nullable String sourceItemId; // The item ID of the item used as the upgrade source (e.g., orb)
    private int sourceItemLevel = 1; // The level of the source item (orb)
    private @Nullable String sourceItemRarity; // The rarity of the source item (orb)

    public HRolledUpgrade(HItemStack stack, HItemUpgrade upgrade, @Nullable CompoundTag values) {
        this.upgrade = upgrade;
        this.stack = stack;
        this.values = values;
    }

    public HRolledUpgrade(HItemStack stack, HItemUpgrade upgrade, @Nullable CompoundTag values, @Nullable String sourceItemId) {
        this.upgrade = upgrade;
        this.stack = stack;
        this.values = values;
        this.sourceItemId = sourceItemId;
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

    public int getSocketIndex() { return socketIndex; }
    public void setSocketIndex(int socketIndex) { this.socketIndex = socketIndex; }

    public @Nullable String getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(@Nullable String sourceItemId) { this.sourceItemId = sourceItemId; }

    public int getSourceItemLevel() { return sourceItemLevel; }
    public void setSourceItemLevel(int sourceItemLevel) { this.sourceItemLevel = sourceItemLevel; }

    public @Nullable String getSourceItemRarity() { return sourceItemRarity; }
    public void setSourceItemRarity(@Nullable String sourceItemRarity) { this.sourceItemRarity = sourceItemRarity; }

    private static String formatNumber(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-6) {
            return Integer.toString((int) Math.rint(d));
        }
        return String.format("%.2f", d);
    }

    private static String humanizeAttributeKey(String key) {
        String path = key;
        int colon = path.indexOf(':');
        if (colon != -1) path = path.substring(colon + 1);
        path = path.replace('.', '_');
        String[] parts = path.split("_");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            b.append(' ');
        }
        if (b.length() == 0) return key;
        b.setLength(b.length() - 1);
        return b.toString();
    }

    public List<Component> getAttributeLoreLines() {
        if (values == null || values.isEmpty()) {
            return Collections.singletonList(Component.text("●", NamedTextColor.GRAY));
        }
        List<String> keys = new ArrayList<>(values.keySet());
        keys.sort(Comparator.naturalOrder());
        List<Component> lines = new ArrayList<>();
        boolean first = true;
        for (String k : keys) {
            if (!values.contains(k)) continue;
            double v = values.getDouble(k).get();
            String display = humanizeAttributeKey(k);
            boolean negative = v < 0;
            String numStr = formatNumber(v);
            String prefix = negative ? "" : "+";
            NamedTextColor numColor = negative ? NamedTextColor.RED : NamedTextColor.GREEN;
            Component valueComp = Component.text(prefix + numStr, numColor)
                    .append(Component.text(" " + display, NamedTextColor.GRAY));
            if (first) {
                lines.add(Component.text("● ", NamedTextColor.GRAY).append(valueComp));
                first = false;
            } else {
                lines.add(Component.text("  ", NamedTextColor.GRAY).append(valueComp));
            }
        }
        if (lines.isEmpty()) {
            lines.add(Component.text("●", NamedTextColor.GRAY));
        }
        return lines;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", upgrade.getId());
        if (values != null) {
            tag.put("values", values);
        } else {
            tag.put("values", new CompoundTag());
        }
        tag.putInt("socketIndex", socketIndex);
        if (sourceItemId != null) {
            tag.putString("sourceItemId", sourceItemId);
            tag.putInt("sourceItemLevel", sourceItemLevel);
            if (sourceItemRarity != null) {
                tag.putString("sourceItemRarity", sourceItemRarity);
            }
        }
        return tag;
    }

    public static @Nullable HRolledUpgrade fromNBT(HItemStack stack, CompoundTag tag) {
        String id = tag.getString("id").get();
        HItemUpgrade upgrade = stack.getItem().getLibrary().getUpgrade(id);
        if (upgrade == null) {
            return null;
        }
        CompoundTag val = tag.getCompoundOrEmpty("values");
        HRolledUpgrade r = new HRolledUpgrade(stack, upgrade, val);
        r.socketIndex = tag.contains("socketIndex") ? tag.getInt("socketIndex").get() : -1;
        if (tag.contains("sourceItemId")) {
            r.sourceItemId = tag.getString("sourceItemId").get();
            r.sourceItemLevel = tag.contains("sourceItemLevel") ? tag.getInt("sourceItemLevel").get() : 1;
            if (tag.contains("sourceItemRarity")) {
                r.sourceItemRarity = tag.getString("sourceItemRarity").get();
            }
        }
        return r;
    }
}
