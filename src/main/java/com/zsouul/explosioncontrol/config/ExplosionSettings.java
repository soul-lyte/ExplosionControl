package com.zsouul.explosioncontrol.config;

/**
 * Immutable, resolved configuration for a single {@link com.zsouul.explosioncontrol.model.ExplosionCategory}.
 *
 * @param enabled            whether this explosion source is allowed to explode at all.
 *                           When {@code false}, the explosion is prevented as close to its
 *                           origin as the Paper API allows (see the listener package for the
 *                           exact hook used per source).
 * @param damageCap          the maximum amount of damage, in raw health points (20.0 = 10
 *                           hearts = vanilla max explosion damage), this explosion type may
 *                           deal to any single {@link org.bukkit.entity.LivingEntity}. Always
 *                           in the inclusive range {@code [0.0, 20.0]}. Applied as a true
 *                           ceiling on final, post-armor damage by
 *                           {@link com.zsouul.explosioncontrol.util.DamageCapApplier}.
 * @param radiusMultiplier   scales the vanilla explosion radius/power. {@code 1.0} is vanilla,
 *                           {@code 0.0} produces a zero-radius explosion, {@code 2.0} doubles it.
 *                           Never negative.
 * @param knockbackMultiplier scales the knockback vector applied to entities caught in the
 *                           blast. {@code 1.0} is vanilla, {@code 0.0} removes knockback
 *                           entirely. Never negative.
 * @param blockDamage        whether this explosion type is allowed to destroy/drop blocks.
 */
public record ExplosionSettings(
        boolean enabled,
        double damageCap,
        double radiusMultiplier,
        double knockbackMultiplier,
        boolean blockDamage
) {

    /** Minimum permitted damage cap, in health points. */
    public static final double MIN_DAMAGE_CAP = 0.0D;

    /** Maximum permitted damage cap, in health points (vanilla max explosion damage). */
    public static final double MAX_DAMAGE_CAP = 20.0D;

    /**
     * @return a settings instance representing unmodified vanilla behaviour, used whenever a
     * category is missing from {@code config.yml} so the plugin degrades gracefully instead
     * of silently disabling an explosion source.
     */
    public static ExplosionSettings vanillaDefault() {
        return new ExplosionSettings(true, MAX_DAMAGE_CAP, 1.0D, 1.0D, true);
    }
}
