package me.ryanhamshire.GriefPrevention.events;

import java.util.Collection;
import java.util.Collections;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link org.bukkit.event.Event Event} called when a {@link Player} uses the claim inspection tool.
 *
 * @author FrankHeijden
 */
public class ClaimInspectionEvent extends PlayerEvent implements Cancellable
{

    private final @NotNull Collection<Claim> claims;

    private final @Nullable Block inspectedBlock;

    private final boolean inspectingNearbyClaims;

    /**
     * Construct a new {@code ClaimInspectionEvent} for a {@link Player} inspecting a {@link Block}.
     *
     * @param player the inspecting {@code Player}
     * @param inspectedBlock the inspected {@code Block}
     * @param claim the {@link Claim} present or {@code null} if not claimed
     */
    public ClaimInspectionEvent(@NotNull Player player, @NotNull Block inspectedBlock, @Nullable Claim claim)
    {
        super(player);

        this.inspectedBlock = inspectedBlock;
        if (claim != null)
        {
            this.claims = Collections.singleton(claim);
        }
        else
        {
            this.claims = Collections.emptyList();
        }
        this.inspectingNearbyClaims = false;
    }

    /**
     * Construct a new {@code ClaimInspectionEvent}.
     *
     * @param player the inspecting {@link Player}
     * @param inspectedBlock the inspected {@link Block} or {@code null} if no block was clicked
     * @param claims a {@link Collection} of all claims inspected
     * @param inspectingNearbyClaims whether the user is inspecting nearby claims ("shift-clicking")
     */
    public ClaimInspectionEvent(@NotNull Player player, @Nullable Block inspectedBlock,
            @NotNull Collection<Claim> claims, boolean inspectingNearbyClaims)
    {
        super(player);
        this.inspectedBlock = inspectedBlock;
        this.claims = claims;
        this.inspectingNearbyClaims = inspectingNearbyClaims;
    }

    /**
     * Get the inspected {@link Block}. May be {@code null} if inspecting nearby claims.
     *
     * @return the inspected {@code Block} or {@code null} if no block was clicked
     */
    public @Nullable Block getInspectedBlock()
    {
        return inspectedBlock;
    }

    /**
     * Get a {@link Collection} of the claims being inspected.
     *
     * @return the inspected claims
     */
    public @NotNull Collection<Claim> getClaims()
    {
        return claims;
    }

    /**
     * Check if nearby claims are being inspected.
     *
     * @return whether the user is inspecting nearby claims
     */
    public boolean isInspectingNearbyClaims()
    {
        return inspectingNearbyClaims;
    }

    // Listenable event requirements
    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList()
    {
        return HANDLERS;
    }

    @Override
    public @NotNull HandlerList getHandlers()
    {
        return HANDLERS;
    }

    // Cancellable requirements
    private boolean cancelled = false;

    @Override
    public boolean isCancelled()
    {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled)
    {
        this.cancelled = cancelled;
    }

}
