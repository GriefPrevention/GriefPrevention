package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link Event} called when GriefPrevention protects items after a death.
 * If cancelled, GriefPrevention will allow the event to complete normally.
 */
public class ProtectDeathDropsEvent extends Event implements Cancellable
{

    private final @Nullable Claim claim;

    /**
     * Construct a new {@code ProtectDeathDropsEvent}.
     *
     * @param claim the claim in which the death occurred
     */
    public ProtectDeathDropsEvent(@Nullable Claim claim)
    {
        this.claim = claim;
    }

    /**
     * Get the claim in which the death occurred. May be {@code null}.
     *
     * @return the claim in which the death occurred
     */
    public @Nullable Claim getClaim()
    {
        return this.claim;
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
