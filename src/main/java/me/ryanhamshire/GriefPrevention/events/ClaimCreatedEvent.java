package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link org.bukkit.event.Event Event} called when a {@link Claim} is created.
 *
 * <p>If cancelled, the resulting {@code Claim} will not be saved.
 * The creator will not be notified why creation was cancelled, if they are notified at all.
 *
 * @author Narimm on 5/08/2018.
 */
public class ClaimCreatedEvent extends ClaimEvent implements Cancellable
{

    private final @Nullable CommandSender creator;

    /**
     * Construct a new {@code ClaimCreatedEvent}.
     *
     * @param claim the {@link Claim} being created
     * @param creator the {@link CommandSender} causing creation
     */
    public ClaimCreatedEvent(@NotNull Claim claim, @Nullable CommandSender creator)
    {
        super(claim);
        this.creator = creator;
    }

    /**
     * Get the {@link CommandSender} creating the {@link Claim}.
     *
     * @return the actor causing creation
     */
    public @Nullable CommandSender getCreator()
    {
        return creator;
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
