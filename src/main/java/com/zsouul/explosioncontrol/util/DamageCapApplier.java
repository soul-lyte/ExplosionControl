package com.zsouul.explosioncontrol.util;

import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Applies a {@code damage-cap} as a true ceiling on an {@link EntityDamageEvent}'s
 * <b>final</b> damage — i.e. after armor, resistance, absorption, and every other vanilla
 * damage modifier has already been factored in — rather than on the raw, pre-modifier base
 * damage.
 * <p>
 * <b>Why this matters:</b> Bukkit computes an {@code EntityDamageEvent}'s non-base modifiers
 * (armor, resistance, absorption, ...) once, from the original uncapped damage, at the moment
 * the event is fired. They are not automatically rescaled if a listener changes the base
 * afterwards. Naively capping {@link EntityDamageEvent#getDamage()} (the base modifier alone)
 * therefore gets this wrong in <i>both</i> directions:
 * <ul>
 *   <li>If the cap is never applied at all for some reason (e.g. the wrong category was
 *       resolved), the entity simply takes the full, uncapped total.</li>
 *   <li>An armoured entity has its (unchanged, correctly vanilla-computed) armor reduction
 *       subtracted from the new, already-lowered base — taking far more off than intended and
 *       delivering noticeably less damage than the configured cap.</li>
 * </ul>
 * Working from {@link EntityDamageEvent#getFinalDamage()} instead — the one number that
 * already reflects whatever armor/resistance/absorption reduction really applies — and
 * trimming only the excess off the base modifier via the stable, non-deprecated
 * {@link EntityDamageEvent#getDamage()} / {@link EntityDamageEvent#setDamage(double)} pair,
 * keeps every other modifier's real contribution completely intact: an entity that would
 * already take less than the cap is left entirely untouched, and one that would take more is
 * trimmed down to exactly the cap.
 * <p>
 * The adjustment is applied in a small, bounded loop rather than as a single calculation,
 * since re-reading {@link EntityDamageEvent#getFinalDamage()} after each adjustment (instead of
 * assuming a fixed relationship between base and final) makes this correct regardless of
 * exactly how a given Paper version internally recomputes its modifiers.
 */
public final class DamageCapApplier {

    /** Generous bound on convergence iterations; correct results normally need only one. */
    private static final int MAX_ITERATIONS = 4;

    private DamageCapApplier() {
        throw new AssertionError("utility class");
    }

    /**
     * @param event the damage event to cap; left completely untouched if its current final
     *              damage is already at or below {@code cap}
     * @param cap   the maximum final damage this event may result in, in health points
     */
    public static void apply(EntityDamageEvent event, double cap) {
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double finalDamage = event.getFinalDamage();
            if (finalDamage <= cap) {
                return;
            }

            double excess = finalDamage - cap;
            double newBase = event.getDamage() - excess;

            if (newBase <= 0.0D) {
                event.setDamage(0.0D);
                return;
            }
            event.setDamage(newBase);
        }
    }
}
