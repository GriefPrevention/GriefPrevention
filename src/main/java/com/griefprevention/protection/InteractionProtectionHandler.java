package com.griefprevention.protection;

import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Event handler for special interaction protection cases.
 */
public class InteractionProtectionHandler implements Listener
{

    /**
     * Special case to handle End portal frame interactions before the portal is created,
     * ensuring build permission checks happen prior to vanilla portal creation logic.
     *
     * @param event the player interaction event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEndPortalFrameInteract(@NotNull PlayerInteractEvent event)
    {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.END_PORTAL_FRAME) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_EYE) return;

        // Use instanceof check instead of direct cast to safely handle potential modded block data implementations
        if (!(block.getBlockData() instanceof org.bukkit.block.data.type.EndPortalFrame frameData)) return;
        if (frameData.hasEye()) return;

        Player player = event.getPlayer();
        Supplier<String> noBuildReason = ProtectionHelper.checkPermission(player, block.getLocation(), ClaimPermission.Build, event);
        if (noBuildReason != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason.get());
        }
    }

}
