package de.erethon.hephaestus;

import de.erethon.hephaestus.commands.GiveCommandBrigadier;
import de.erethon.hephaestus.commands.GrindstoneCommandBrigadier;
import de.erethon.hephaestus.commands.HephaestusCommandBrigadier;
import de.erethon.hephaestus.items.HItemLibrary;
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

    private HItemLibrary itemLibrary;

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        itemLibrary = new HItemLibrary(new File(context.getDataDirectory().toFile(), "items"), new File(context.getDataDirectory().toFile(), "upgrades"));
        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();
        manager.registerEventHandler(RegistryEvents.ENCHANTMENT.entryAdd().newHandler(e -> e.builder().supportedItems(RegistrySet.keySet(ItemTypeKeys.AIR.registryKey()))));

        manager.registerEventHandler(LifecycleEvents.COMMANDS, e -> {
            final Commands commands = e.registrar();
            HephaestusCommandBrigadier.register(commands, itemLibrary);
            GiveCommandBrigadier.register(commands, itemLibrary);
            GrindstoneCommandBrigadier.register(commands);
        });
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new Hephaestus(itemLibrary);
    }
}
