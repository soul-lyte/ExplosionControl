package com.zsouul.explosioncontrol.listener;

import com.zsouul.explosioncontrol.cache.ExplosionOriginRegistry;
import com.zsouul.explosioncontrol.config.ConfigManager;
import com.zsouul.explosioncontrol.config.ExplosionSettings;
import com.zsouul.explosioncontrol.model.ExplosionCategory;
import com.zsouul.explosioncontrol.resolver.ExplosionSourceResolver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExplosionPrimeEvent;

/**
 * Handles every entity-sourced explosion (TNT, TNT Minecart, Creeper, Charged Creeper,
 * Wither spawn, Wither Skull, Ghast Fireball, generic Fireball, Blaze Fireball, End Crystal,
 * Wind Charge) at the earliest point Paper exposes: {@link ExplosionPrimeEvent}, which fires
 * before any block or entity damage calculation happens.
 * <p>
 * This is the single point of control for the {@code enabled} flag on all of the above
 * sources — cancelling here prevents the explosion outright, so no blocks are touched and no
 * entity takes any damage or knockback at all. It is also the only place {@code
 * radius-multiplier} can be applied, since {@code EntityExplodeEvent} only exposes a block
 * list and item-drop yield, not the underlying blast radius/power.
 * <p>
 * It also records the resolved category into {@link ExplosionOriginRegistry}, keyed by
 * location, while the source entity is still guaranteed to exist — see that class for why.
 */
public final class ExplosionPrimeListener implements Listener {

    private final ConfigManager configManager;
    private final ExplosionOriginRegistry originRegistry;

    public ExplosionPrimeListener(ConfigManager configManager, ExplosionOriginRegistry originRegistry) {
        this.configManager = configManager;
        this.originRegistry = originRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        ExplosionCategory category = ExplosionSourceResolver.resolve(event.getEntity());
        ExplosionSettings settings = configManager.getSettings(category);

        if (!settings.enabled()) {
            event.setCancelled(true);
            return;
        }

        originRegistry.record(event.getEntity().getLocation(), category);

        double multiplier = settings.radiusMultiplier();
        if (multiplier != 1.0D) {
            float scaledRadius = (float) Math.max(0.0D, event.getRadius() * multiplier);
            event.setRadius(scaledRadius);
        }
    }
}
