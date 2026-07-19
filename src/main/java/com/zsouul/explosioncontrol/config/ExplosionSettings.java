package com.zsouul.explosioncontrol.config;

/**
 * Immutable, resolved configuration for a single {@link com.zsouul.explosioncontrol.model.ExplosionCategory}.
 *
 * @param enabled            whether this explosion source is allowed to explode at all.
 *                           When {@code false}, the explosion is prevented as close to its
 *                           origin as the Paper API allows (see the listener package for the
 *                           exact hook used per source).
 * @param damageMultiplier   scales the damage this explosion type deals to any
 *                           {@link org.bukkit.entity.LivingEntity} it hits. {@code 1.0} is
 *                           vanilla, {@code 0.0} deals no damage at all, {@code 2.0} doubles
 *                           it. Never negative. Applied to the raw base damage before armor,
 *                           so armor/resistance/absorption continue to reduce it exactly as
 *                           they would in unmodified vanilla, just starting from a scaled
 *                           amount — the same approach {@link #radiusMultiplier} and
 *                           {@link #knockbackMultiplier} already use for their own values.
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
        double damageMultiplier,
        double radiusMultiplier,
        double knockbackMultiplier,
        boolean blockDamage
) {

    /**
     * @return a settings instance representing unmodified vanilla behaviour, used whenever a
     * category is missing from {@code config.yml} so the plugin degrades gracefully instead
     * of silently disabling an explosion source.
     */
    public static ExplosionSettings vanillaDefault() {
        return new ExplosionSettings(true, 1.0D, 1.0D, 1.0D, true);
    }
}
