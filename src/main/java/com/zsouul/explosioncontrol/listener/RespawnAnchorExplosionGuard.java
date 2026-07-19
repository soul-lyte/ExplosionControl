package com.zsouul.explosioncontrol.listener;

import com.zsouul.explosioncontrol.config.ConfigManager;
import com.zsouul.explosioncontrol.config.ExplosionSettings;
import com.zsouul.explosioncontrol.model.ExplosionCategory;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Best-effort enforcement of {@code enabled: false} for Respawn Anchor explosions.
 * <p>
 * <b>Paper API limitation:</b> unlike Beds (see {@link BedExplosionGuard}, backed by the
 * dedicated cancellable {@code PlayerBedFailEnterEvent}), Paper/Bukkit does not expose any
 * pre-explosion event for Respawn Anchors. The closest available hook is
 * {@link PlayerInteractEvent}, so this listener replicates vanilla's own trigger condition for
 * "using this anchor will make it explode": a right-click on a <em>charged</em> Respawn Anchor
 * outside the Nether, without a charging item (glowstone) in hand.
 * <p>
 * This covers the vanilla trigger (a player interacting with the anchor) but, unlike the Bed
 * guard, cannot catch every conceivable way an anchor might be forced to explode (e.g. a
 * different plugin invoking the explosion directly). {@link BlockExplodeListener} still runs
 * as a second line of defence in those cases, though by that point in vanilla's explosion
 * sequence any entity damage/knockback has already been applied — see that class's docs.
 */
public final class RespawnAnchorExplosionGuard implements Listener {

    private final ConfigManager configManager;

    public RespawnAnchorExplosionGuard(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.RESPAWN_ANCHOR) {
            return;
        }

        World.Environment environment = block.getWorld().getEnvironment();
        if (environment == World.Environment.NETHER) {
            return; // stable here — a Respawn Anchor never explodes in the Nether
        }

        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.GLOWSTONE) {
            return; // charging attempt, not a spawn-point trigger
        }

        if (!(block.getBlockData() instanceof RespawnAnchor anchor) || anchor.getCharges() <= 0) {
            return; // an anchor with no charge cannot explode
        }

        ExplosionSettings settings = configManager.getSettings(ExplosionCategory.RESPAWN_ANCHOR);
        if (!settings.enabled()) {
            event.setCancelled(true);
        }
    }
}
