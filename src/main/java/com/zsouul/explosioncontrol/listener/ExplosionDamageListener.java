package com.zsouul.explosioncontrol.listener;

import com.zsouul.explosioncontrol.cache.ExplosionOriginRegistry;
import com.zsouul.explosioncontrol.cache.PendingKnockbackCache;
import com.zsouul.explosioncontrol.config.ConfigManager;
import com.zsouul.explosioncontrol.config.ExplosionSettings;
import com.zsouul.explosioncontrol.model.ExplosionCategory;
import com.zsouul.explosioncontrol.resolver.ExplosionSourceResolver;
import com.zsouul.explosioncontrol.util.DamageCapApplier;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Optional;

/**
 * Enforces {@code damage-cap} for every {@link org.bukkit.entity.LivingEntity} hit by an
 * explosion, and resolves the responsible {@link ExplosionCategory} so
 * {@link ExplosionKnockbackListener} can apply the matching {@code knockback-multiplier}
 * afterwards (see {@link PendingKnockbackCache} for why that correlation is necessary).
 * <p>
 * The actual capping is delegated to {@link DamageCapApplier}, which works from the event's
 * <em>final</em> damage (after armor/resistance/absorption) rather than the raw base damage —
 * see that class's docs for why that distinction matters.
 */
public final class ExplosionDamageListener implements Listener {

    private final ConfigManager configManager;
    private final PendingKnockbackCache knockbackCache;
    private final ExplosionOriginRegistry originRegistry;

    public ExplosionDamageListener(ConfigManager configManager, PendingKnockbackCache knockbackCache,
                                    ExplosionOriginRegistry originRegistry) {
        this.configManager = configManager;
        this.knockbackCache = knockbackCache;
        this.originRegistry = originRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }

        ExplosionCategory category = resolveCategory(event);
        ExplosionSettings settings = configManager.getSettings(category);

        if (!settings.enabled()) {
            // Should already have been prevented upstream (ExplosionPrimeListener /
            // BedExplosionGuard / RespawnAnchorExplosionGuard); cancelling here is a safety net.
            event.setCancelled(true);
            knockbackCache.remove(event.getEntity().getUniqueId());
            return;
        }

        DamageCapApplier.apply(event, settings.damageCap());

        // Hand the resolved settings off to the knockback listener for this same victim.
        knockbackCache.put(event.getEntity().getUniqueId(), settings);
    }

    /**
     * Resolves which explosion category caused this damage event, in three tiers:
     * <ol>
     *   <li>{@link DamageSource#getCausingEntity()} (falling back to {@code getDirectEntity()})
     *       — precise and cheap, covers the overwhelming majority of explosions.</li>
     *   <li>{@link ExplosionOriginRegistry} — covers the rare case where the exploding entity
     *       is already gone by the time the {@code DamageSource} is built (e.g. a TNT Minecart
     *       consuming itself), using the category recorded for that location a moment ago.</li>
     *   <li>A direct block-type check at {@link DamageSource#getDamageLocation()} — the
     *       expected path for block-sourced explosions (Bed, Respawn Anchor), for which there
     *       never is a causing entity in the first place.</li>
     * </ol>
     * Only falls through to {@link ExplosionCategory#OTHER} if all three come up empty.
     */
    private ExplosionCategory resolveCategory(EntityDamageEvent event) {
        DamageSource source = event.getDamageSource();

        Entity causingEntity = source.getCausingEntity();
        if (causingEntity == null) {
            causingEntity = source.getDirectEntity();
        }
        if (causingEntity != null) {
            return ExplosionSourceResolver.resolve(causingEntity);
        }

        Location damageLocation = source.getDamageLocation();

        Optional<ExplosionCategory> fromOrigin = originRegistry.lookup(damageLocation);
        if (fromOrigin.isPresent()) {
            return fromOrigin.get();
        }

        if (damageLocation != null && damageLocation.getWorld() != null) {
            return ExplosionSourceResolver.resolveBlock(damageLocation.getBlock().getType());
        }

        return ExplosionCategory.OTHER;
    }
}
