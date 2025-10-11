package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Listen to {@link ClaimPermissionCheckEvent} and check if
 * {@link ClaimPermissionCheckEvent#getTriggeringEvent()} {@code instanceof} {@link BlockBreakEvent}.
 */
@Deprecated(forRemoval = true, since = "17.0.0")
public class PreventBlockBreakEvent extends Event implements Cancellable
{

    private final @NotNull BlockBreakEvent innerEvent;

    /**
     * Construct a new {@code PreventBlockBreakEvent}.
     *
     * @param innerEvent the inner {@link BlockBreakEvent}
     */
    public PreventBlockBreakEvent(@NotNull BlockBreakEvent innerEvent)
    {
        this.innerEvent = innerEvent;
    }

    /**
     * Get the {@link BlockBreakEvent} being cancelled by GriefPrevention.
     *
     * @return the inner {@code BlockBreakEvent}
     */
    public @NotNull BlockBreakEvent getInnerEvent()
    {
        return this.innerEvent;
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
