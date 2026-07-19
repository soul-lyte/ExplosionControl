package com.zsouul.explosioncontrol.listener;

import com.zsouul.explosioncontrol.config.ConfigManager;
import com.zsouul.explosioncontrol.config.ExplosionSettings;
import com.zsouul.explosioncontrol.model.ExplosionCategory;
import com.zsouul.explosioncontrol.resolver.ExplosionSourceResolver;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;

/**
 * Applies {@code block-damage} to Bed and Respawn Anchor explosions, the two vanilla explosion
 * sources that originate from a block rather than an entity.
 * <p>
 * Unlike entity-sourced explosions, {@code enabled} for these two categories is primarily
 * enforced <em>before</em> the explosion ever starts — see {@link BedExplosionGuard} and
 * {@link RespawnAnchorExplosionGuard} — because by the time {@link BlockExplodeEvent} fires,
 * vanilla has already applied entity damage and knockback (see the package-info / README for
 * the full explanation of this ordering). Cancelling here is therefore only a defensive
 * fallback that stops block destruction if an explosion slips through some other path.
 */
public final class BlockExplodeListener implements Listener {

    private final ConfigManager configManager;

    public BlockExplodeListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Material material = event.getExplodedBlockState().getType();
        ExplosionCategory category = ExplosionSourceResolver.resolveBlock(material);
        ExplosionSettings settings = configManager.getSettings(category);

        if (!settings.enabled()) {
            event.setCancelled(true);
            return;
        }

        if (!settings.blockDamage() && !event.blockList().isEmpty()) {
            event.blockList().clear();
        }
    }
}
