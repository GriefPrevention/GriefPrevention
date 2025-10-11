package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import org.jetbrains.annotations.NotNull;

/**
 * An {@link org.bukkit.event.Event Event} called when a {@link Player} is about to receive claim blocks.
 * GriefPrevention calls this event 6 times hourly, once every 10 minutes.
 *
 * @author RoboMWM on 11/15/2016
 */
public class AccrueClaimBlocksEvent extends PlayerEvent implements Cancellable
{

    private int blocksToAccrue;

    private boolean isIdle;

    /**
     * Construct a new {@code AccrueClaimBlocksEvent}.
     *
     * <p>Note that this event was designed for simple internal usage. Because of that,
     * it is assumed GriefPrevention is handling block delivery at its standard rate of
     * 6 times per hour.
     * <br>To achieve a specific number of blocks to accrue, either multiply in advance or set
     * using {@link #setBlocksToAccrue(int)} after construction.
     *
     * @param player the {@link Player} receiving accruals
     * @param blocksToAccruePerHour the number of claim blocks to accrue multiplied by 6
     * @see #setBlocksToAccruePerHour(int)
     * @deprecated Use {@link #AccrueClaimBlocksEvent(Player, int, boolean)} instead
     */
    @Deprecated
    public AccrueClaimBlocksEvent(@NotNull Player player, int blocksToAccruePerHour)
    {
        this(player, blocksToAccruePerHour, false);
    }

    /**
     * Construct a new {@code AccrueClaimBlocksEvent}.
     *
     * <p>Note that this event was designed for simple internal usage. Because of that,
     * it is assumed GriefPrevention is handling block delivery at its standard rate of
     * 6 times per hour.
     * <br>To achieve a specific number of blocks to accrue, either multiply in advance or set
     * using {@link #setBlocksToAccrue(int)} after construction.
     * <br>This event is initialized as canceled if @param isIdle is set to true.
     *
     * @param player the {@link Player} receiving accruals
     * @param blocksToAccruePerHour the number of claim blocks to accrue multiplied by 6
     * @param isIdle whether player is detected as idle
     * @see #setBlocksToAccruePerHour(int)
     */
    public AccrueClaimBlocksEvent(@NotNull Player player, int blocksToAccruePerHour, boolean isIdle)
    {
        super(player);
        this.blocksToAccrue = blocksToAccruePerHour / 6;
        this.isIdle = isIdle;
        if (isIdle) this.setCancelled(true);
    }

    /**
     * Get the number of claim blocks that will be delivered to the {@link Player}.
     *
     * @return the number of new claim blocks
     */
    public int getBlocksToAccrue()
    {
        return this.blocksToAccrue;
    }

    /**
     * Set the number of claim blocks to be delivered to the {@link Player}.
     *
     * @param blocksToAccrue blocks to deliver
     */
    public void setBlocksToAccrue(int blocksToAccrue)
    {
        this.blocksToAccrue = blocksToAccrue;
    }

    /**
     * Set the number of blocks to accrue per hour. This assumes GriefPrevention is
     * handling block delivery at its standard rate of 6 times per hour.
     *
     * @param blocksToAccruePerHour the per-hour rate of blocks to deliver
     * @see #setBlocksToAccrue(int)
     */
    public void setBlocksToAccruePerHour(int blocksToAccruePerHour)
    {
        this.blocksToAccrue = blocksToAccruePerHour / 6;
    }

    /**
     * Get whether the {@link Player} is idle. This can be used to modify accrual rate
     * for players who are inactive.
     *
     * @return whether the {@code Player} is idle
     */
    public boolean isIdle()
    {
        return this.isIdle;
    }

    /**
     * Set whether the {@link Player} is idle.
     *
     * @param idle whether the {@code Player} is idle
     */
    public void setIdle(boolean idle)
    {
        isIdle = idle;
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
