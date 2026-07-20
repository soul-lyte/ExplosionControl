package com.zsouul.explosioncontrol.resolver;

import com.zsouul.explosioncontrol.model.ExplosionCategory;
import org.bukkit.Material;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.minecart.ExplosiveMinecart;

/**
 * Central, single-source-of-truth logic for turning a Bukkit {@link Entity} or exploded
 * {@link Material} into the {@link ExplosionCategory} the rest of the plugin should apply.
 * <p>
 * Every listener in the {@code listener} package delegates here instead of re-implementing
 * {@code instanceof} chains, so adding support for a new explosion source in a future
 * Minecraft version only ever requires touching this one class (plus a matching
 * {@link ExplosionCategory} constant and {@code config.yml} section).
 */
public final class ExplosionSourceResolver {

    private ExplosionSourceResolver() {
        throw new AssertionError("utility class");
    }

    /**
     * Resolves the category of an entity-sourced explosion (used by {@code ExplosionPrimeEvent}
     * and {@code EntityExplodeEvent}, and indirectly by damage-source lookups).
     *
     * @param entity the exploding entity, or the entity a projectile/knockback is attributed
     *               to; may be {@code null}
     * @return the matching category, or {@link ExplosionCategory#OTHER} if unknown/{@code null}
     */
    public static ExplosionCategory resolve(Entity entity) {
        if (entity == null) {
            return ExplosionCategory.OTHER;
        }

        // Order matters: check the most specific interfaces/classes before their supertypes.
        if (entity instanceof ExplosiveMinecart) {
            return ExplosionCategory.TNT_MINECART;
        }
        if (entity instanceof TNTPrimed) {
            return ExplosionCategory.TNT;
        }
        if (entity instanceof EnderCrystal) {
            return ExplosionCategory.END_CRYSTAL;
        }
        if (entity instanceof Creeper creeper) {
            return creeper.isPowered() ? ExplosionCategory.CHARGED_CREEPER : ExplosionCategory.CREEPER;
        }
        if (entity instanceof WitherSkull) {
            return ExplosionCategory.WITHER_SKULL;
        }
        if (entity instanceof Wither) {
            return ExplosionCategory.WITHER_SPAWN;
        }
        if (entity instanceof DragonFireball) {
            // In practice a DragonFireball never reaches this method: it does not fire
            // ExplosionPrimeEvent/EntityExplodeEvent at all (see DragonFireballListener for
            // why, and how "dragon-fireball" is actually handled). Kept here for completeness
            // and as a safety net in case a future Minecraft version changes this.
            return ExplosionCategory.DRAGON_FIREBALL;
        }
        if (entity instanceof AbstractWindCharge) {
            // Covers both the thrown/dispensed WindCharge and the BreezeWindCharge fired by
            // a Breeze — both share this common supertype and are treated as one category.
            return ExplosionCategory.WIND_CHARGE;
        }
        if (entity instanceof SmallFireball) {
            // The projectile type shot by a Blaze (and by dispensers loaded with fire charges
            // held by no other entity). See config.yml for the vanilla-behaviour caveat here.
            return ExplosionCategory.BLAZE_FIREBALL;
        }
        if (entity instanceof LargeFireball fireball) {
            return (fireball.getShooter() instanceof Ghast) ? ExplosionCategory.GHAST_FIREBALL : ExplosionCategory.FIREBALL;
        }

        return ExplosionCategory.OTHER;
    }

    /**
     * Resolves the category of a block-sourced explosion (Bed, Respawn Anchor), used by
     * {@code BlockExplodeEvent} and by block-based damage-source correlation.
     *
     * @param material the material of the block that exploded; may be {@code null}
     * @return the matching category, or {@link ExplosionCategory#OTHER} if unknown/{@code null}
     */
    public static ExplosionCategory resolveBlock(Material material) {
        if (material == null) {
            return ExplosionCategory.OTHER;
        }
        if (material == Material.RESPAWN_ANCHOR) {
            return ExplosionCategory.RESPAWN_ANCHOR;
        }
        // Covers all 16 colours (WHITE_BED, RED_BED, ...) without depending on a Material tag.
        if (material.name().endsWith("_BED")) {
            return ExplosionCategory.BED;
        }
        return ExplosionCategory.OTHER;
    }
}
