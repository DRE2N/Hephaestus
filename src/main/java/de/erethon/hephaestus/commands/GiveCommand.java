package de.erethon.hephaestus.commands;

import de.erethon.hephaestus.items.HItemLibrary;
import de.erethon.hephaestus.items.HRarity;
import net.strokkur.commands.Aliases;
import net.strokkur.commands.Command;
import net.strokkur.commands.Executes;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.paper.DefaultToExecutor;
import net.strokkur.commands.paper.Description;
import net.strokkur.commands.permission.Permission;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Command("give")
@Description("Give an item to a player.")
@Aliases({"i", "g"})
class GiveCommand {
    private final HItemLibrary itemLibrary;

    public GiveCommand(HItemLibrary itemLibrary) {
        this.itemLibrary = itemLibrary;
    }

    @Executes
    @Permission("hephaestus.give")
    void execute(
        CommandSender sender,
        NamespacedKey key,
        @DefaultToExecutor Player player,
        OptionalInt count,
        @IntArg(min = 0) OptionalInt level,
        Optional<HRarity> rarity,
        Optional<String> socketPattern
    ) {
        itemLibrary.runIfPresent(key, item -> {
            final ItemStack stack;
            if (level.isPresent()) {
                stack = item.createStack(1, level.getAsInt(), socketPattern.orElse(null), rarity.orElse(HRarity.COMMON)).getBukkitStack();
            } else {
                stack = item.rollRandomStack().getBukkitStack();
            }

            if (count.isPresent()) {
                stack.setAmount(Math.min(stack.getMaxStackSize(), count.getAsInt()));
            }
            player.getInventory().addItem();
        });

        final StringBuilder messageBuilder = new StringBuilder("<green>Gave ");
        if (count.isPresent()) {
            messageBuilder.append(count.getAsInt()).append(" of ");
        }
        messageBuilder.append("item <gray>").append(key).append("</gray> ");
        if (level.isPresent()) {
            messageBuilder.append("(level ").append(level.getAsInt());
            rarity.ifPresent(hRarity -> messageBuilder.append(", rarity ").append(hRarity.name().toLowerCase(Locale.ROOT)));
            socketPattern.ifPresent(sockets -> messageBuilder.append(", sockets ").append(sockets));
            messageBuilder.append(") ");
        }
        if (sender != player) {
            messageBuilder.append(" to ").append(player.getName());
        }
        messageBuilder.append(".");

        sender.sendRichMessage(messageBuilder.toString());
    }
}
