package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link org.bukkit.event.Event Event} called when a {@link Claim} is deleted.
 *
 * <p>This event is not called when a claim is resized.
 *
 * @author Tux2
 */
public class ClaimDeletedEvent extends ClaimEvent
{

    /**
     * Construct a new {@code ClaimDeletedEvent}.
     *
     * @param claim the {@link Claim} being deleted
     */
    public ClaimDeletedEvent(@NotNull Claim claim)
    {
        super(claim);
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

}
