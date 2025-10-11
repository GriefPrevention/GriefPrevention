package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link org.bukkit.event.Event Event} called when a user is rescued from a {@link Claim}.
 */
public class SaveTrappedPlayerEvent extends ClaimEvent implements Cancellable
{

    private @Nullable Location destination = null;

    /**
     * Construct a new {@code ClaimChangeEvent}.
     *
     * @param claim {@link Claim} the user is to be rescued from
     */
    public SaveTrappedPlayerEvent(@NotNull Claim claim)
    {
        super(claim);
    }

    /**
     * Get the destination that the user will be sent to. This is {@code null} by default,
     * indicating that GriefPrevention will search for a safe location.
     *
     * @return the destination to send the {@code Player} to
     */
    public @Nullable Location getDestination()
    {
        return destination;
    }

    /**
     * Set the destination that the user will be sent to. If {@code null},
     * GriefPrevention will search for a location.
     *
     * @param destination the destination to send the {@code Player} to
     */
    public void setDestination(@Nullable Location destination)
    {
        this.destination = destination;
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
