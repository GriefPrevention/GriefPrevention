package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.Location;

import org.jetbrains.annotations.NotNull;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link org.bukkit.event.Event Event} for when a {@link Claim Claim's} depth (lower Y bound) is to be extended.
 *
 * <p>Note that changes to the {@link #getTo() new claim} other than {@link #setNewDepth(int) setting new depth} will
 * not be respected.
 *
 * @author FrankHeijden
 */
public class ClaimExtendEvent extends ClaimChangeEvent
{

    private int newDepth;

    /**
     * Construct a new {@code ClaimExtendEvent}.
     *
     * @param claim the {@link Claim} extending downwards
     * @param newDepth the new depth of the {@code Claim}
     */
    public ClaimExtendEvent(@NotNull Claim claim, int newDepth)
    {
        super(claim, new Claim(claim) {

            @Override
            public Location getLesserBoundaryCorner()
            {
                Location lesserBoundaryCorner = super.getLesserBoundaryCorner();
                lesserBoundaryCorner.setY(newDepth);
                return lesserBoundaryCorner;
            }
        });
        this.newDepth = newDepth;
    }

    /**
     * Get the resulting {@link Claim} after modification.
     *
     * @return the resulting {@code Claim}
     * @deprecated Use {@link #getTo() getTo} instead.
     */
    @Deprecated(forRemoval = true, since = "16.18")
    public @NotNull Claim getClaim()
    {
        return getTo();
    }

    /**
     * Get the new lowest depth that the {@link Claim} will encompass in the Y axis.
     *
     * @return the new depth
     */
    public int getNewDepth()
    {
        return newDepth;
    }

    /**
     * Set the new lowest depth that the {@link Claim} will encompass in the Y axis.
     *
     * <p>Note that this value is not necessarily final - it will be modified to respect configuration and world limits.
     *
     * @param newDepth the new depth
     */
    public void setNewDepth(int newDepth)
    {
        this.newDepth = newDepth;
    }

}
