package de.erethon.hephaestus.jobs.crafting.commands;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.jobs.crafting.gui.CraftingStationGUI;
import de.erethon.hephaestus.jobs.crafting.gui.RecipeDiscoveryGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class CraftingCommand extends Command implements TabCompleter {

    private final Hephaestus plugin = Hephaestus.INSTANCE;

    public CraftingCommand(@NotNull String name) {
        super(name);
        setDescription("Access the crafting system");
        setUsage("/crafting [station|discovery|reload|help]");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            new CraftingStationGUI(plugin, player).open();
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "station", "craft" -> {
                new CraftingStationGUI(plugin, player).open();
                player.sendMessage(Component.text("Opening crafting station...", NamedTextColor.GREEN));
            }
            case "discovery", "discover" -> {
                new RecipeDiscoveryGUI(plugin, player).open();
                player.sendMessage(Component.text("Opening recipe discovery interface...", NamedTextColor.GOLD));
            }
            case "reload" -> {
                if (!player.hasPermission("hephaestus.crafting.reload")) {
                    player.sendMessage(Component.text("You don't have permission to reload recipes!", NamedTextColor.RED));
                    return true;
                }
                plugin.getRecipeManager().reloadRecipes();
                player.sendMessage(Component.text("Recipes reloaded successfully!", NamedTextColor.GREEN));
            }
            case "help" -> {
                sendHelpMessage(player);
            }
            default -> {
                player.sendMessage(Component.text("Unknown subcommand. Use /crafting help for available commands.", NamedTextColor.RED));
            }
        }

        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(Component.text("=== Crafting System Help ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/crafting station - Open the crafting station", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/crafting discovery - Open recipe discovery interface", NamedTextColor.YELLOW));
        if (player.hasPermission("hephaestus.crafting.reload")) {
            player.sendMessage(Component.text("/crafting reload - Reload all recipes", NamedTextColor.YELLOW));
        }
        player.sendMessage(Component.text("/crafting help - Show this help message", NamedTextColor.YELLOW));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("station", "discovery", "help");
            if (sender.hasPermission("hephaestus.crafting.reload")) {
                subCommands = Arrays.asList("station", "discovery", "reload", "help");
            }
            return subCommands.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
