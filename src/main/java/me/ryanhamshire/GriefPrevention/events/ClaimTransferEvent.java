package me.ryanhamshire.GriefPrevention.events;

import java.util.UUID;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link org.bukkit.event.Event Event} called when a {@link Claim} is transferred.
 * If cancelled, the resulting changes will not be made.
 *
 * @author bertek41 on 10/30/2021
 */

public class ClaimTransferEvent extends ClaimEvent implements Cancellable
{

    private @Nullable UUID newOwner;

    /**
     * Construct a new {@code ClaimTransferEvent}.
     *
     * @param claim the {@link Claim} being transferred
     * @param newOwner the {@link UUID} of the new owner
     */
    public ClaimTransferEvent(@NotNull Claim claim, @Nullable UUID newOwner)
    {
        super(claim);
        this.newOwner = newOwner;
    }

    /**
     * Get the {@link UUID} of the new owner of the claim.
     * This may be {@code null} to indicate an administrative claim.
     *
     * @return the {@code UUID} of the new owner
     */
    public @Nullable UUID getNewOwner()
    {
        return newOwner;
    }

    /**
     * Set the {@link UUID} of the new owner of the claim.
     * This may be {@code null} to indicate an administrative claim.
     *
     * @param newOwner the {@code UUID} of the new owner
     */
    public void setNewOwner(@Nullable UUID newOwner)
    {
        this.newOwner = newOwner;
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
