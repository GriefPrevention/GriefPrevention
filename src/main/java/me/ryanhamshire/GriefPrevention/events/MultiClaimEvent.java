package me.ryanhamshire.GriefPrevention.events;

import java.util.Collection;
import java.util.HashSet;

import org.bukkit.event.Event;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link Event} involving multiple {@link Claim Claims}.
 */
public abstract class MultiClaimEvent extends Event
{

    private final @NotNull Collection<Claim> claims;

    public MultiClaimEvent(@Nullable Collection<Claim> claims)
    {
        if (claims == null)
        {
            this.claims = new HashSet<>();
        }
        else
        {
            this.claims = new HashSet<>(claims);
        }
    }

    /**
     * Get all affected claims.
     *
     * @return the affected claims
     */
    public @NotNull Collection<Claim> getClaims()
    {
        return claims;
    }

}
