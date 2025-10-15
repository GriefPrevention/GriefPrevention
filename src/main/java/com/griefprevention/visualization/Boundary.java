package com.griefprevention.visualization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;

/**
 * A data holder defining an area to be visualized.
 */
public record Boundary(@NotNull BoundingBox bounds, @NotNull VisualizationType type, @Nullable Claim claim) {

    /**
     * Construct a new {@code Boundary} for a {@link BoundingBox} with the given visualization style.
     *
     * @param bounds the {@code BoundingBox}
     * @param type the {@link VisualizationType}
     */
    public Boundary(@NotNull BoundingBox bounds, @NotNull VisualizationType type)
    {
        this(bounds, type, null);
    }

    /**
     * Construct a new {@code Boundary} for a {@link Claim} with the given visualization style.
     *
     * @param claim the {@code Claim}
     * @param type the {@link VisualizationType}
     */
    public Boundary(@NotNull Claim claim, @NotNull VisualizationType type)
    {
        this(new BoundingBox(claim), type, claim);
    }

    /**
     * Get the {@link Claim} represented by the {@code Boundary}, if any.
     *
     * @return the {@code Claim} or {@code null} if not present
     */
    @Override
    public @Nullable Claim claim()
    {
        return claim;
    }

}
