package com.griefprevention.api.claim;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Resolves lookup, overlap, chunk indexing, and visualization bounds for claims.
 */
public interface ClaimGeometry
{

    /**
     * Metadata key used to store a claim's geometry type.
     */
    NamespacedKey METADATA_KEY = Objects.requireNonNull(NamespacedKey.fromString("griefprevention:geometry"));

    /**
     * Get the namespaced key identifying this geometry.
     *
     * @return the geometry key
     */
    @NotNull NamespacedKey getKey();

    /**
     * Get the bounding box used for chunk indexing and broad-phase lookups.
     *
     * @param claim the claim
     * @return the lookup bounds
     */
    @NotNull BoundingBox getLookupBounds(@NotNull Claim claim);

    /**
     * Get the bounding box used for visualization.
     *
     * @param claim the claim
     * @return the visualization bounds
     */
    default @NotNull BoundingBox getVisualizationBounds(@NotNull Claim claim)
    {
        return getLookupBounds(claim);
    }

    /**
     * Determine whether a location is inside a claim.
     *
     * @param claim the claim
     * @param location the location
     * @param ignoreHeight whether height should be ignored
     * @return true if inside
     */
    boolean contains(@NotNull Claim claim, @NotNull Location location, boolean ignoreHeight);

    /**
     * Determine whether a set of bounds fits inside a claim.
     *
     * @param claim the containing claim
     * @param bounds the bounds to test
     * @param ignoreHeight whether height should be ignored
     * @return true if fully contained
     */
    default boolean contains(@NotNull Claim claim, @NotNull BoundingBox bounds, boolean ignoreHeight)
    {
        BoundingBox lookupBounds = getLookupBounds(claim);
        if (ignoreHeight)
        {
            return lookupBounds.getMinX() <= bounds.getMinX()
                    && lookupBounds.getMaxX() >= bounds.getMaxX()
                    && lookupBounds.getMinZ() <= bounds.getMinZ()
                    && lookupBounds.getMaxZ() >= bounds.getMaxZ();
        }

        return lookupBounds.contains(bounds);
    }

    /**
     * Determine whether two claims overlap.
     *
     * @param claim the first claim
     * @param otherClaim the second claim
     * @return true if they overlap
     */
    boolean overlaps(@NotNull Claim claim, @NotNull Claim otherClaim);

    /**
     * Get chunk hashes that should index a claim for broad-phase lookup.
     *
     * @param claim the claim
     * @return the chunk hashes
     */
    default @NotNull ArrayList<Long> getChunkHashes(@NotNull Claim claim)
    {
        return DataStore.getChunkHashes(getLookupBounds(claim));
    }

}
