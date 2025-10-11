package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link org.bukkit.event.Event Event} called when GriefPrevention prevents PvP combat.
 * If cancelled, GriefPrevention will allow the event to complete normally.
 */
public class PreventPvPEvent extends ClaimEvent implements Cancellable
{

    private final @Nullable Player attacker;

    private final @NotNull Entity defender;

    /**
     * Construct a new {@code PreventPvPEvent}.
     *
     * @param claim the {@link Claim} in which the attack is occurring
     * @param attacker the attacking {@link Player}
     * @param defender the {@link Entity} being attacked
     */
    public PreventPvPEvent(@NotNull Claim claim, @Nullable Player attacker, @NotNull Entity defender)
    {
        super(claim);
        this.attacker = attacker;
        this.defender = defender;
    }

    /**
     * Get the attacking {@link Player}. May be {@code null} for damage from area of effect clouds and similar.
     *
     * @return the attacker
     */
    public @Nullable Player getAttacker()
    {
        return attacker;
    }

    /**
     * Get the {@link Entity} being attacked. This may be a {@link Player}
     * or {@link org.bukkit.entity.Tameable Tameable}.
     *
     * @return the {@code Entity} being attacked
     */
    public @NotNull Entity getDefender()
    {
        return defender;
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
