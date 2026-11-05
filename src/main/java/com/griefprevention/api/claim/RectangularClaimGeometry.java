package com.griefprevention.api.claim;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Built-in geometry matching GriefPrevention's current rectangular claim behavior.
 */
public final class RectangularClaimGeometry implements ClaimGeometry
{

    /**
     * Built-in key for rectangular claim geometry.
     */
    public static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("griefprevention:rectangular"));

    @Override
    public @NotNull NamespacedKey getKey()
    {
        return KEY;
    }

    @Override
    public @NotNull BoundingBox getLookupBounds(@NotNull Claim claim)
    {
        return new BoundingBox(claim);
    }

    @Override
    public boolean contains(@NotNull Claim claim, @NotNull Location location, boolean ignoreHeight)
    {
        if (!Objects.equals(location.getWorld(), claim.getLesserBoundaryCorner().getWorld()))
        {
            return false;
        }

        BoundingBox boundingBox = getLookupBounds(claim);
        int x = location.getBlockX();
        int z = location.getBlockZ();

        if (ignoreHeight)
        {
            return boundingBox.contains2d(x, z);
        }

        return boundingBox.contains(x, location.getBlockY(), z);
    }

    @Override
    public boolean overlaps(@NotNull Claim claim, @NotNull Claim otherClaim)
    {
        if (!Objects.equals(claim.getLesserBoundaryCorner().getWorld(), otherClaim.getLesserBoundaryCorner().getWorld()))
        {
            return false;
        }

        return getLookupBounds(claim).intersects(getLookupBounds(otherClaim));
    }

}
