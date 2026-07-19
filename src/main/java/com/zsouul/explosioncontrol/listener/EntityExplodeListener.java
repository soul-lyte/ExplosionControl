package com.zsouul.explosioncontrol.listener;

import com.zsouul.explosioncontrol.cache.ExplosionOriginRegistry;
import com.zsouul.explosioncontrol.config.ConfigManager;
import com.zsouul.explosioncontrol.config.ExplosionSettings;
import com.zsouul.explosioncontrol.model.ExplosionCategory;
import com.zsouul.explosioncontrol.resolver.ExplosionSourceResolver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Applies {@code block-damage} to entity-sourced explosions by clearing the affected block
 * list, and re-checks {@code enabled} as a defensive fallback (the primary enforcement point
 * is {@link ExplosionPrimeListener}, which runs earlier and also prevents entity damage).
 * <p>
 * Clearing {@link EntityExplodeEvent#blockList()} rather than cancelling the whole event is
 * deliberate: it preserves entity damage/knockback (already resolved separately) while simply
 * leaving the terrain untouched, which is exactly what {@code block-damage: false} promises.
 * <p>
 * Also records into {@link ExplosionOriginRegistry} as a defensive backup, in case
 * {@link ExplosionPrimeListener} didn't get a chance to (e.g. a future explosion source this
 * plugin doesn't yet special-case).
 */
public final class EntityExplodeListener implements Listener {

    private final ConfigManager configManager;
    private final ExplosionOriginRegistry originRegistry;

    public EntityExplodeListener(ConfigManager configManager, ExplosionOriginRegistry originRegistry) {
        this.configManager = configManager;
        this.originRegistry = originRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        ExplosionCategory category = ExplosionSourceResolver.resolve(event.getEntity());
        ExplosionSettings settings = configManager.getSettings(category);

        if (!settings.enabled()) {
            event.setCancelled(true);
            return;
        }

        originRegistry.record(event.getLocation(), category);

        if (!settings.blockDamage() && !event.blockList().isEmpty()) {
            event.blockList().clear();
        }
    }
}
