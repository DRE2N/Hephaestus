package de.erethon.hephaestus.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.erethon.hephaestus.items.HItemLibrary;
import net.minecraft.commands.CommandSourceStack;
import net.strokkur.commands.CustomSuggestion;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

class SuggestionsRepository {
    static @Nullable HItemLibrary ITEM_LIBRARY = null;

    static HItemLibrary itemLibrary() {
        return Objects.requireNonNull(ITEM_LIBRARY);
    }

    @CustomSuggestion
    @Retention(RetentionPolicy.SOURCE)
    @interface BlockDataKeySuggestions {}

    @CustomSuggestion
    @Retention(RetentionPolicy.SOURCE)
    @interface UpgradeSuggestions {}

    @BlockDataKeySuggestions
    public static CompletableFuture<Suggestions> suggestBlockDataKeys(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        itemLibrary().getKeys().forEach(key -> {
            String keyString = key.toString();
            if (keyString.toLowerCase().startsWith(input)) {
                builder.suggest(keyString);
            }
        });
        return builder.buildFuture();
    }

    @UpgradeSuggestions
    public static CompletableFuture<Suggestions> suggestUpgradeKeys(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        itemLibrary().getUpgradeKeys().forEach(upgradeKey -> {
            if (upgradeKey.toLowerCase().startsWith(input)) {
                builder.suggest(upgradeKey);
            }
        });
        return builder.buildFuture();
    }
}
