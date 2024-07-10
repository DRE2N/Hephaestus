package de.erethon.hephaestus;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.erethon.hephaestus.items.ItemLibrary;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.set.RegistrySet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.*;

import java.io.File;
import java.util.List;

public class HephaestusBootstrap implements PluginBootstrap {

    private ItemLibrary itemLibrary;

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        itemLibrary = new ItemLibrary(new File(context.getDataDirectory().toFile(), "items"));
        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();
        manager.registerEventHandler(RegistryEvents.ENCHANTMENT.entryAdd().newHandler(e -> e.builder().supportedItems(RegistrySet.keySet(ItemTypeKeys.AIR.registryKey()))));

        manager.registerEventHandler(LifecycleEvents.COMMANDS, e -> {
            final Commands commands = e.registrar();
            commands.register(Commands.literal("hephaestus").executes(ctx -> Command.SINGLE_SUCCESS).then(Commands.literal("reload").executes(ctx -> {
                ctx.getSource().getSender().sendRichMessage("<green>Reloading...");
                itemLibrary.reload();
                ctx.getSource().getSender().sendRichMessage("<green>Reloaded Item Library.");
                return Command.SINGLE_SUCCESS;
            })).requires(s -> s.getSender().hasPermission("hephaestus.reload")).then(Commands.literal("register").then(RequiredArgumentBuilder.argument("key", ArgumentTypes.namespacedKey())).executes(ctx -> {
                ctx.getSource().getSender().sendRichMessage("Parsing Item in hand...");
                Player player = (Player) ctx.getSource().getSender();
                NamespacedKey key = ctx.getArgument("key", NamespacedKey.class);
                itemLibrary.register(player.getInventory().getItemInMainHand(), key);
                ctx.getSource().getSender().sendRichMessage("Registered Item with key " + key.toString() + ".");
                return Command.SINGLE_SUCCESS;
            })).requires(s -> s.getSender().hasPermission("hephaestus.register")).build(), "Main Hephaestus command.", List.of("he", "hp", "h", "hh"));
        });
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new Hephaestus(itemLibrary);
    }
}
