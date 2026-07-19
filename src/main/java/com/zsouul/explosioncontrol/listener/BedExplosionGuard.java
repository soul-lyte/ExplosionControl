package com.zsouul.explosioncontrol.listener;

import com.zsouul.explosioncontrol.config.ConfigManager;
import com.zsouul.explosioncontrol.config.ExplosionSettings;
import com.zsouul.explosioncontrol.model.ExplosionCategory;
import io.papermc.paper.event.player.PlayerBedFailEnterEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Fully enforces {@code enabled: false} for Bed explosions.
 * <p>
 * A Bed explosion is triggered by a player attempting to sleep in the Nether or the End, which
 * Paper reports through the cancellable {@link PlayerBedFailEnterEvent}
 * ({@code failReason == NOT_POSSIBLE_HERE}, {@code willExplode() == true}). Cancelling this
 * event stops the interaction before vanilla ever calls its explosion code, which is what lets
 * this plugin prevent Bed explosions <em>completely</em> (no damage, no knockback, no block
 * damage) rather than only stopping block destruction after the fact — unlike Respawn Anchor,
 * for which Paper does not expose an equivalent dedicated event (see
 * {@link RespawnAnchorExplosionGuard}).
 */
public final class BedExplosionGuard implements Listener {

    private final ConfigManager configManager;

    public BedExplosionGuard(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedFailEnter(PlayerBedFailEnterEvent event) {
        if (!event.getWillExplode()) {
            return; // a normal "can't sleep right now" failure, not an explosion
        }

        ExplosionSettings settings = configManager.getSettings(ExplosionCategory.BED);
        if (!settings.enabled()) {
            event.setCancelled(true);
        }
    }
}
