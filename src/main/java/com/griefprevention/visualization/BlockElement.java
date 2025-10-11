package com.griefprevention.visualization;

import java.util.Objects;

import org.bukkit.World;
import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.griefprevention.util.IntVector;

/**
 * An element of a {@link BlockBoundaryVisualization}.
 */
public abstract class BlockElement
{

    private final @NotNull IntVector coordinate;

    /**
     * Construct a new {@code BlockElement} with the given coordinate.
     *
     * @param coordinate the in-world coordinate of the element
     */
    public BlockElement(@NotNull IntVector coordinate)
    {
        this.coordinate = coordinate;
    }

    /**
     * Get the in-world coordinate of the element.
     *
     * @return the coordinate
     */
    public @NotNull IntVector getCoordinate()
    {
        return coordinate;
    }

    /**
     * Display the element for a {@link Player} in a particular {@link World}.
     *
     * @param player the {@code Player} visualizing the element
     * @param world the {@code World} the element is displayed in
     */
    protected abstract void draw(@NotNull Player player, @NotNull World world);

    /**
     * Stop the display of the element for a {@link Player} in a particular {@link World}.
     *
     * @param player the {@code Player} visualizing the element
     * @param world the {@code World} the element is displayed in
     */
    protected abstract void erase(@NotNull Player player, @NotNull World world);

    @Override
    public boolean equals(@Nullable Object other)
    {
        if (this == other) return true;
        if (other == null || !getClass().isAssignableFrom(other.getClass())) return false;
        BlockElement that = (BlockElement) other;
        return coordinate.equals(that.coordinate);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(coordinate);
    }

}
