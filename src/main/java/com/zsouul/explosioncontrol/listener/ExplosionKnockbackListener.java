package com.zsouul.explosioncontrol.listener;

import com.zsouul.explosioncontrol.cache.PendingKnockbackCache;
import com.zsouul.explosioncontrol.config.ExplosionSettings;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.util.Vector;

import java.util.Optional;

/**
 * Applies {@code knockback-multiplier} to explosion-caused knockback.
 * <p>
 * {@code EntityKnockbackEvent} does not expose which entity or block caused the knockback, so
 * the actual category/settings are looked up from {@link PendingKnockbackCache}, populated a
 * moment earlier by {@link ExplosionDamageListener} for the same victim. If nothing is found
 * (e.g. the damage event never fired or was cancelled by another plugin first), vanilla
 * knockback is left completely untouched rather than guessed at.
 */
public final class ExplosionKnockbackListener implements Listener {

    private final PendingKnockbackCache knockbackCache;

    public ExplosionKnockbackListener(PendingKnockbackCache knockbackCache) {
        this.knockbackCache = knockbackCache;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityKnockback(EntityKnockbackEvent event) {
        if (event.getCause() != EntityKnockbackEvent.KnockbackCause.EXPLOSION) {
            return;
        }

        Optional<ExplosionSettings> resolved = knockbackCache.consume(event.getEntity().getUniqueId());
        if (resolved.isEmpty()) {
            return;
        }

        double multiplier = resolved.get().knockbackMultiplier();
        if (multiplier == 1.0D) {
            return; // vanilla behaviour, nothing to do
        }

        Vector scaled = event.getFinalKnockback().clone().multiply(multiplier);
        event.setFinalKnockback(scaled);
    }
}
