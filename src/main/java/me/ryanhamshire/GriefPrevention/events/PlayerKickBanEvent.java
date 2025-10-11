package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import org.jetbrains.annotations.NotNull;

/**
 * An {@link org.bukkit.event.Event Event} called when GriefPrevention kicks or bans a {@link Player}.
 *
 * @author BillyGalbreath on 03/10/2017
 */
public class PlayerKickBanEvent extends PlayerEvent implements Cancellable
{

    private final @NotNull String reason;

    private final @NotNull String source;

    private final boolean ban;

    /**
     * Construct a new {@code PlayerKickBanEvent}.
     *
     * @param player the affected {@link Player}
     * @param reason the reason for the kick/ban
     * @param source the source of the kick/ban
     * @param ban whether the {@code Player} will be banned
     */
    public PlayerKickBanEvent(Player player, @NotNull String reason, @NotNull String source, boolean ban)
    {
        super(player);
        this.reason = reason;
        this.source = source;
        this.ban = ban;
    }

    /**
     * Get the reason why the {@link Player} is being kicked.
     *
     * @return the reason why the target is being kicked
     */
    public @NotNull String getReason()
    {
        return this.reason;
    }

    /**
     * Get the source of the kick.
     *
     * @return the source of the kick
     */
    public @NotNull String getSource()
    {
        return this.source;
    }

    /**
     * Get whether the {@link Player} will also be banned.
     *
     * @return whether the target will be banned
     */
    public boolean isBan()
    {
        return ban;
    }

    /**
     * Get whether the {@link Player} will also be banned.
     *
     * @deprecated use {@link #isBan()}
     * @return whether the target will be banned
     */
    @Deprecated
    public boolean getBan()
    {
        return this.isBan();
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
