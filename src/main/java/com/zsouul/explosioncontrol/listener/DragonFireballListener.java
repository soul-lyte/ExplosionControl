package com.zsouul.explosioncontrol.listener;

import com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent;
import com.zsouul.explosioncontrol.config.ConfigManager;
import com.zsouul.explosioncontrol.config.ExplosionSettings;
import com.zsouul.explosioncontrol.model.ExplosionCategory;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Handles the Dragon Fireball, which is a genuine Paper API special case among every other
 * explosion source this plugin configures.
 * <p>
 * Every other source in this plugin funnels through {@code ExplosionPrimeEvent} /
 * {@code EntityExplodeEvent}, because vanilla implements them as a real, block-destroying
 * explosion. A Dragon Fireball does not: on impact it spawns a lingering
 * {@link AreaEffectCloud} ("dragon's breath") that damages entities over time with
 * {@code EntityDamageEvent.DamageCause.DRAGON_BREATH}. It never fires
 * {@code ExplosionPrimeEvent}, never fires {@code EntityExplodeEvent}, never destroys blocks,
 * and has no knockback component at all. This is a Minecraft/Paper API limitation, not an
 * oversight — there is no "real" explosion here to hook.
 * <p>
 * The closest available, honest mapping of this plugin's five options is:
 * <ul>
 *   <li>{@code enabled} — cancels {@link EnderDragonFireballHitEvent}, preventing the cloud
 *       (and therefore all resulting damage) from ever appearing.</li>
 *   <li>{@code damage-multiplier} — scales every tick of {@code DRAGON_BREATH} damage the
 *       cloud deals, exactly like any other explosion source's damage multiplier.</li>
 *   <li>{@code radius-multiplier} — scales the resulting {@link AreaEffectCloud}'s radius,
 *       the closest available analogue to an explosion's blast radius.</li>
 *   <li>{@code block-damage} and {@code knockback-multiplier} — intentionally not applicable
 *       and silently ignored for this category: vanilla Dragon Fireballs never break blocks
 *       and never apply knockback, so there is nothing for either option to control.</li>
 * </ul>
 */
public final class DragonFireballListener implements Listener {

    private final ConfigManager configManager;

    public DragonFireballListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonFireballHit(EnderDragonFireballHitEvent event) {
        ExplosionSettings settings = configManager.getSettings(ExplosionCategory.DRAGON_FIREBALL);

        if (!settings.enabled()) {
            event.setCancelled(true);
            return;
        }

        double multiplier = settings.radiusMultiplier();
        if (multiplier != 1.0D) {
            AreaEffectCloud cloud = event.getAreaEffectCloud();
            float scaledRadius = (float) Math.max(0.0D, cloud.getRadius() * multiplier);
            cloud.setRadius(scaledRadius);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonBreathDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.DRAGON_BREATH) {
            return;
        }

        ExplosionSettings settings = configManager.getSettings(ExplosionCategory.DRAGON_FIREBALL);
        if (!settings.enabled()) {
            event.setCancelled(true);
            return;
        }

        double multiplier = settings.damageMultiplier();
        if (multiplier != 1.0D) {
            double scaled = Math.max(0.0D, event.getDamage() * multiplier);
            event.setDamage(scaled);
        }
    }
}
