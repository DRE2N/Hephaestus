package de.erethon.hephaestus;

import com.mojang.brigadier.Command;
import de.erethon.hephaestus.items.ItemLibrary;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.set.RegistrySet;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class HephaestusBootstrap implements PluginBootstrap {

    private ItemLibrary itemLibrary;

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        itemLibrary = new ItemLibrary(new File(context.getDataDirectory().toFile(), "items"));
        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();
        manager.registerEventHandler(RegistryEvents.ENCHANTMENT.entryAdd().newHandler(e -> e.builder().supportedItems(RegistrySet.keySet(ItemTypeKeys.AIR.registryKey()))));

        manager.registerEventHandler(LifecycleEvents.COMMANDS, e -> {
            final Commands commands = e.registrar();
            commands.register(Commands.literal("hephaestus").executes(ctx -> {
                if (!ctx.getSource().getSender().hasPermission("hephaestus.reload")) {
                    return -1;
                }
                ctx.getSource().getSender().sendRichMessage("<green>Reloading...");
                itemLibrary.reload();
                ctx.getSource().getSender().sendRichMessage("<green>Reloaded Item Library.");
                return Command.SINGLE_SUCCESS;
            }).build());
            });
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new Hephaestus(itemLibrary);
    }
}
