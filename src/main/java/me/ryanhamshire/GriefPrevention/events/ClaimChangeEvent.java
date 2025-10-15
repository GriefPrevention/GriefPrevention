package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link Event} called when a {@link Claim} is changed.
 *
 * <p>If cancelled, the resulting changes will not be made.
 *
 * <p>Note that the {@link #getTo() new claim} will not necessarily be added to the datastore! Most implementations
 * apply changes to the {@link #getFrom() existing claim} for better compatibility with add-ons holding instances.
 * Additionally, while the new claim is modifiable, modifications will not necessarily be respected by implementations.
 */
public class ClaimChangeEvent extends Event implements Cancellable
{

    private final @NotNull Claim from;

    private final @NotNull Claim to;

    /**
     * Construct a new {@code ClaimChangeEvent}.
     *
     * @param from the original {@link Claim}
     * @param to the resulting {@code Claim}
     */
    public ClaimChangeEvent(@NotNull Claim from, @NotNull Claim to)
    {
        this.from = from;
        this.to = to;
    }

    /**
     * Get the original unmodified {@link Claim}.
     *
     * @return the initial {@code Claim}
     */
    public @NotNull Claim getFrom()
    {
        return from;
    }

    /**
     * Get the resulting {@link Claim} after any changes have been applied.
     *
     * @return the resulting {@code Claim}
     */
    public @NotNull Claim getTo()
    {
        return to;
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
