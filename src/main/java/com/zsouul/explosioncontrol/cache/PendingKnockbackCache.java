package com.zsouul.explosioncontrol.cache;

import com.zsouul.explosioncontrol.config.ExplosionSettings;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Correlates a resolved {@link ExplosionSettings} with the knockback event Paper fires for the
 * same victim immediately afterwards.
 * <p>
 * <b>Why this exists:</b> {@code org.bukkit.event.entity.EntityKnockbackEvent} tells you
 * <i>that</i> an entity is being knocked back by an explosion ({@code KnockbackCause.EXPLOSION}),
 * but not <i>which</i> explosion source caused it — there is no accessor for the exploding
 * entity or block. To still apply a per-source {@code knockback-multiplier}, this cache stores
 * the category resolved while handling that same victim's {@code EntityDamageEvent} (where the
 * source <i>is</i> available, via {@code DamageSource}), keyed by the victim's {@link UUID}.
 * <p>
 * This works because vanilla's explosion resolution damages and knocks back each affected
 * entity back-to-back before moving on to the next one, so the damage event for a given victim
 * always fires immediately before that victim's knockback event, within the same server tick.
 * Entries are consumed (removed) the moment they're read, and any left unread past
 * {@link #MAX_AGE_MILLIS} (e.g. because another plugin cancelled the damage event first) are
 * swept away opportunistically so this can never grow unbounded.
 */
public final class PendingKnockbackCache {

    private static final long MAX_AGE_MILLIS = 150L; // ~3 ticks at 20 TPS, generous safety margin
    private static final int SWEEP_THRESHOLD = 128;

    private final Map<UUID, Entry> pending = new ConcurrentHashMap<>();

    /**
     * Records the settings resolved for an explosion that just damaged {@code entityId}, to be
     * consumed by the matching knockback event.
     */
    public void put(UUID entityId, ExplosionSettings settings) {
        if (pending.size() >= SWEEP_THRESHOLD) {
            sweep();
        }
        pending.put(entityId, new Entry(settings, System.currentTimeMillis()));
    }

    /**
     * Removes and returns the pending settings for {@code entityId}, if any and not expired.
     */
    public Optional<ExplosionSettings> consume(UUID entityId) {
        Entry entry = pending.remove(entityId);
        if (entry == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() - entry.timestampMillis() > MAX_AGE_MILLIS) {
            return Optional.empty();
        }
        return Optional.of(entry.settings());
    }

    /**
     * Discards any pending entry for {@code entityId} without returning it, used when the
     * originating damage event was itself cancelled.
     */
    public void remove(UUID entityId) {
        pending.remove(entityId);
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue().timestampMillis() > MAX_AGE_MILLIS);
    }

    private record Entry(ExplosionSettings settings, long timestampMillis) {
    }
}
