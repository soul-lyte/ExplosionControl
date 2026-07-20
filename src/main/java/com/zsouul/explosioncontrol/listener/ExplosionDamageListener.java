package com.zsouul.explosioncontrol.listener;

import com.zsouul.explosioncontrol.cache.ExplosionOriginRegistry;
import com.zsouul.explosioncontrol.cache.PendingKnockbackCache;
import com.zsouul.explosioncontrol.config.ConfigManager;
import com.zsouul.explosioncontrol.config.ExplosionSettings;
import com.zsouul.explosioncontrol.model.ExplosionCategory;
import com.zsouul.explosioncontrol.resolver.ExplosionSourceResolver;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Enforces {@code damage-multiplier} for every {@link org.bukkit.entity.LivingEntity} hit by
 * an explosion, and resolves the responsible {@link ExplosionCategory} so
 * {@link ExplosionKnockbackListener} can apply the matching {@code knockback-multiplier}
 * afterwards (see {@link PendingKnockbackCache} for why that correlation is necessary).
 * <p>
 * The multiplier is applied to the raw base damage, exactly the same way
 * {@code radius-multiplier} scales {@code ExplosionPrimeEvent}'s radius and
 * {@code knockback-multiplier} scales {@code EntityKnockbackEvent}'s vector — armor,
 * resistance, and absorption then reduce the scaled amount exactly as they would in
 * unmodified vanilla.
 */
public final class ExplosionDamageListener implements Listener {

    private final ConfigManager configManager;
    private final PendingKnockbackCache knockbackCache;
    private final ExplosionOriginRegistry originRegistry;
    private final Logger logger;

    public ExplosionDamageListener(ConfigManager configManager, PendingKnockbackCache knockbackCache,
                                    ExplosionOriginRegistry originRegistry, Logger logger) {
        this.configManager = configManager;
        this.knockbackCache = knockbackCache;
        this.originRegistry = originRegistry;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }

        boolean debug = configManager.isDebugEnabled();
        ExplosionCategory category = resolveCategory(event, debug);
        ExplosionSettings settings = configManager.getSettings(category);

        if (!settings.enabled()) {
            // Should already have been prevented upstream (ExplosionPrimeListener /
            // BedExplosionGuard / RespawnAnchorExplosionGuard); cancelling here is a safety net.
            event.setCancelled(true);
            knockbackCache.remove(event.getEntity().getUniqueId());
            return;
        }

        double rawDamage = event.getDamage();
        double multiplier = settings.damageMultiplier();
        if (multiplier != 1.0D) {
            double scaled = Math.max(0.0D, rawDamage * multiplier);
            event.setDamage(scaled);
        }

        if (debug) {
            logger.info(() -> String.format(
                    "[debug] cause=%s category=%s multiplier=%.4f rawDamage=%.4f finalDamage=%.4f victim=%s",
                    cause, category.key(), multiplier, rawDamage, event.getDamage(),
                    event.getEntity().getUniqueId()));
        }

        // Hand the resolved settings off to the knockback listener for this same victim.
        knockbackCache.put(event.getEntity().getUniqueId(), settings);
    }

    /**
     * Resolves which explosion category caused this damage event, in three tiers:
     * <ol>
     *   <li>{@link DamageSource#getDirectEntity()} (falling back to {@code getCausingEntity()})
     *       — precise and cheap, covers the overwhelming majority of explosions. Direct is
     *       checked first deliberately: {@code getCausingEntity()} reflects <em>attribution</em>
     *       (e.g. the player who lit a TNT fuse gets credited for the kill) and can therefore be
     *       a {@code Player} rather than the entity that actually exploded, which would never
     *       match any category and silently fall through to {@link ExplosionCategory#OTHER}.
     *       {@code getDirectEntity()} is the literal thing that caused the damage — the TNT,
     *       the Creeper, the Wither Skull — which is what determines the category here.</li>
     *   <li>{@link ExplosionOriginRegistry} — covers the rare case where the exploding entity
     *       is already gone by the time the {@code DamageSource} is built (e.g. a TNT Minecart
     *       consuming itself), using the category recorded for that location a moment ago.</li>
     *   <li>A direct block-type check at {@link DamageSource#getDamageLocation()} — the
     *       expected path for block-sourced explosions (Bed, Respawn Anchor), for which there
     *       never is a causing entity in the first place.</li>
     * </ol>
     * Only falls through to {@link ExplosionCategory#OTHER} if all three come up empty.
     */
    private ExplosionCategory resolveCategory(EntityDamageEvent event, boolean debug) {
        DamageSource source = event.getDamageSource();

        Entity directEntity = source.getDirectEntity();
        Entity resolutionEntity = (directEntity != null) ? directEntity : source.getCausingEntity();

        if (resolutionEntity != null) {
            ExplosionCategory category = ExplosionSourceResolver.resolve(resolutionEntity);
            if (debug) {
                logger.info(() -> String.format(
                        "[debug] resolved via entity: directEntity=%s causingEntity=%s -> used=%s -> category=%s",
                        describe(source.getDirectEntity()), describe(source.getCausingEntity()),
                        describe(resolutionEntity), category.key()));
            }
            return category;
        }

        Location damageLocation = source.getDamageLocation();

        Optional<ExplosionCategory> fromOrigin = originRegistry.lookup(damageLocation);
        if (fromOrigin.isPresent()) {
            if (debug) {
                logger.info(() -> "[debug] resolved via origin registry -> category=" + fromOrigin.get().key());
            }
            return fromOrigin.get();
        }

        if (damageLocation != null && damageLocation.getWorld() != null) {
            ExplosionCategory category = ExplosionSourceResolver.resolveBlock(damageLocation.getBlock().getType());
            if (debug) {
                logger.info(() -> "[debug] resolved via block-at-location -> category=" + category.key());
            }
            return category;
        }

        if (debug) {
            logger.info("[debug] no entity, no origin-registry match, no usable location -> category=other");
        }
        return ExplosionCategory.OTHER;
    }

    private static String describe(Entity entity) {
        return (entity == null) ? "null" : entity.getType() + "(" + entity.getUniqueId() + ")";
    }
}
