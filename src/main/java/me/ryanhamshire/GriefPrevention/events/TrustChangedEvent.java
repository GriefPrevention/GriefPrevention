package me.ryanhamshire.GriefPrevention.events;

import java.util.Collections;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;

/**
 * An {@link org.bukkit.event.Event Event} called when a {@link Player} modifies
 * {@link ClaimPermission permissions} in one or more {@link Claim claims}.
 *
 * @author roinujnosde
 */
public class TrustChangedEvent extends MultiClaimEvent implements Cancellable
{

    private final @NotNull Player changer;

    private final @Nullable ClaimPermission claimPermission;

    private final boolean given;

    private final @NotNull String identifier;

    /**
     * Construct a new {@code TrustChangedEvent} for several {@link Claim claims}.
     *
     * @param changer the {@link Player} causing the trust changes
     * @param claims the affected {@code Claims}
     * @param claimPermission the new {@link ClaimPermission} to assign or {@code null} if trust is removed
     * @param given whether trust is being given or taken
     * @param identifier the identifier whose trust is being affected
     */
    public TrustChangedEvent(@NotNull Player changer, @Nullable List<Claim> claims,
            @Nullable ClaimPermission claimPermission, boolean given, @NotNull String identifier)
    {
        super(claims);
        this.changer = changer;
        this.claimPermission = claimPermission;
        this.given = given;
        this.identifier = identifier;
    }

    /**
     * Construct a new {@code TrustChangedEvent} for a single {@link Claim}.
     *
     * @param changer the {@link Player} causing the trust changes
     * @param claim the affected {@code Claim}
     * @param claimPermission the new {@link ClaimPermission} to assign or {@code null} if trust is removed
     * @param given whether trust is being given or taken
     * @param identifier the identifier whose trust is being affected
     */
    public TrustChangedEvent(@NotNull Player changer, @NotNull Claim claim, @Nullable ClaimPermission claimPermission,
            boolean given, @NotNull String identifier)
    {
        super(Collections.singleton(claim));
        this.changer = changer;
        this.claimPermission = claimPermission;
        this.given = given;
        this.identifier = identifier;
    }

    /**
     * Get the {@link Player} who made the change.
     *
     * @return the changer
     */
    public @NotNull Player getChanger()
    {
        return changer;
    }

    /**
     * Get the {@link ClaimPermission} assigned or {@code null} if permission is being removed.
     *
     * @return the claim permission
     */
    public @Nullable ClaimPermission getClaimPermission()
    {
        return claimPermission;
    }

    /**
     * Check if trust is being given or taken.
     *
     * @return true if given, false if taken
     */
    public boolean isGiven()
    {
        return given;
    }

    /**
     * Get the identifier of the receiver of this action.
     * Possible values: "public", "all", a {@link java.util.UUID UUID} in string form, a permission
     *
     * @return the identifier
     */
    public @NotNull String getIdentifier()
    {
        return identifier;
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
