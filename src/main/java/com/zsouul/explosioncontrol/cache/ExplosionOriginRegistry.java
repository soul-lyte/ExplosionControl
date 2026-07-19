package com.zsouul.explosioncontrol.cache;

import com.zsouul.explosioncontrol.model.ExplosionCategory;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Correlates a recently-primed explosion's location with the {@link ExplosionCategory}
 * resolved for it, as a fallback for the rare case where {@code DamageSource#getCausingEntity()}
 * and {@code getDirectEntity()} both come back {@code null} for what was, at prime time, a
 * perfectly valid entity-sourced explosion — for example if the source entity removes itself
 * as part of exploding (a TNT Minecart consuming itself) before Bukkit builds the
 * {@code DamageSource} for a given victim.
 * <p>
 * Without this fallback, that case would silently resolve to {@link ExplosionCategory#OTHER}
 * — a real gap, since {@code other} defaults to unrestricted vanilla behaviour, which is the
 * worst possible category to fall back to unnoticed.
 * <p>
 * Populated as early as possible — see {@code ExplosionPrimeListener}, which fires before any
 * damage or removal happens and is therefore guaranteed a valid source entity — and consulted
 * by {@code ExplosionDamageListener} only when the direct entity lookup fails, so the common,
 * fast path (direct entity resolution via {@code DamageSource}) pays no extra cost.
 * <p>
 * Entries are keyed by a rounded block-coordinate bucket rather than by entity, since by the
 * time a lookup is needed there may be no entity reference left to key on, and expire quickly:
 * a busy PvP server can have several unrelated explosions in flight within the same tick, and
 * a stale or distant entry must never bleed into a different explosion's resolution.
 */
public final class ExplosionOriginRegistry {

    private static final long MAX_AGE_MILLIS = 150L; // ~3 ticks at 20 TPS, generous safety margin
    private static final int SWEEP_THRESHOLD = 128;

    private final Map<String, Entry> recent = new ConcurrentHashMap<>();

    /**
     * Records the category resolved for an explosion that was just primed/exploded at
     * {@code location}.
     */
    public void record(Location location, ExplosionCategory category) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (recent.size() >= SWEEP_THRESHOLD) {
            sweep();
        }
        recent.put(bucketKey(location), new Entry(category, System.currentTimeMillis()));
    }

    /**
     * @return the category recorded for an explosion near {@code location}, if one was
     * recorded recently enough; {@link Optional#empty()} otherwise.
     */
    public Optional<ExplosionCategory> lookup(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        Entry entry = recent.get(bucketKey(location));
        if (entry == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() - entry.timestampMillis() > MAX_AGE_MILLIS) {
            return Optional.empty();
        }
        return Optional.of(entry.category());
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        recent.entrySet().removeIf(e -> now - e.getValue().timestampMillis() > MAX_AGE_MILLIS);
    }

    private static String bucketKey(Location location) {
        World world = location.getWorld();
        UUID worldId = (world != null) ? world.getUID() : null;
        return worldId + ":" + Math.round(location.getX())
                + ":" + Math.round(location.getY())
                + ":" + Math.round(location.getZ());
    }

    private record Entry(ExplosionCategory category, long timestampMillis) {
    }
}
