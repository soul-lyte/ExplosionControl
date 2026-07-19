package com.zsouul.explosioncontrol;

import com.zsouul.explosioncontrol.cache.ExplosionOriginRegistry;
import com.zsouul.explosioncontrol.cache.PendingKnockbackCache;
import com.zsouul.explosioncontrol.command.ExplosionControlCommand;
import com.zsouul.explosioncontrol.config.ConfigManager;
import com.zsouul.explosioncontrol.listener.BedExplosionGuard;
import com.zsouul.explosioncontrol.listener.BlockExplodeListener;
import com.zsouul.explosioncontrol.listener.DragonFireballListener;
import com.zsouul.explosioncontrol.listener.EntityExplodeListener;
import com.zsouul.explosioncontrol.listener.ExplosionDamageListener;
import com.zsouul.explosioncontrol.listener.ExplosionKnockbackListener;
import com.zsouul.explosioncontrol.listener.ExplosionPrimeListener;
import com.zsouul.explosioncontrol.listener.RespawnAnchorExplosionGuard;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for ExplosionControl.
 * <p>
 * Gives server administrators independent, hot-reloadable control over every explosion source
 * in Minecraft 1.21.11 — {@code enabled}, {@code damage-multiplier}, {@code radius-multiplier},
 * {@code knockback-multiplier}, and {@code block-damage} — via {@code config.yml}.
 * <p>
 * See the {@code listener} package for how each explosion source is hooked, and
 * {@link com.zsouul.explosioncontrol.resolver.ExplosionSourceResolver} for the single place
 * that maps a Bukkit entity/block to a configurable category.
 *
 * @author zSouul
 */
public final class ExplosionControl extends JavaPlugin {

    private ConfigManager configManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        configManager.load();

        PendingKnockbackCache knockbackCache = new PendingKnockbackCache();
        ExplosionOriginRegistry originRegistry = new ExplosionOriginRegistry();

        registerListeners(knockbackCache, originRegistry);
        registerCommand();

        getLogger().info(() -> "ExplosionControl enabled: "
                + configManager.getAllSettings().size() + " explosion categories loaded.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        getLogger().info("ExplosionControl disabled.");
    }

    /**
     * @return the live configuration manager, exposed for tests/tools; listeners receive it
     * directly via constructor injection instead of reaching through the plugin singleton.
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    private void registerListeners(PendingKnockbackCache knockbackCache, ExplosionOriginRegistry originRegistry) {
        PluginManager pluginManager = getServer().getPluginManager();

        // Entity-sourced explosions: TNT, TNT Minecart, Creeper, Charged Creeper, Wither spawn,
        // Wither Skull, Ghast Fireball, Fireball, Blaze Fireball, End Crystal, Wind Charge.
        pluginManager.registerEvents(new ExplosionPrimeListener(configManager, originRegistry), this);
        pluginManager.registerEvents(new EntityExplodeListener(configManager, originRegistry), this);

        // Block-sourced explosions: Bed, Respawn Anchor.
        pluginManager.registerEvents(new BlockExplodeListener(configManager), this);
        pluginManager.registerEvents(new BedExplosionGuard(configManager), this);
        pluginManager.registerEvents(new RespawnAnchorExplosionGuard(configManager), this);

        // Shared damage-multiplier / knockback-multiplier enforcement for every source above.
        pluginManager.registerEvents(new ExplosionDamageListener(configManager, knockbackCache, originRegistry), this);
        pluginManager.registerEvents(new ExplosionKnockbackListener(knockbackCache), this);

        // Dragon Fireball: Paper API special case, handled entirely separately (see class docs).
        pluginManager.registerEvents(new DragonFireballListener(configManager), this);
    }

    private void registerCommand() {
        ExplosionControlCommand handler = new ExplosionControlCommand(this, configManager);
        PluginCommand command = getCommand("explosioncontrol");
        if (command == null) {
            getLogger().severe("Command 'explosioncontrol' is missing from plugin.yml — "
                    + "reloading will not be available.");
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }
}
