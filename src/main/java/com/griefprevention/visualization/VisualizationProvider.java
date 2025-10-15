package com.griefprevention.visualization;

import org.bukkit.World;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import com.griefprevention.util.IntVector;

/**
 * A provider for {@link BoundaryVisualization BoundaryVisualizations}.
 */
public interface VisualizationProvider
{

    /**
     * Construct a new {@link BoundaryVisualization} with the given parameters.
     *
     * @param world the {@link World} being visualized in
     * @param visualizeFrom the {@link IntVector} representing the world coordinate being visualized from
     * @param height the height of the visualization
     * @return the resulting visualization
     */
    @Contract(pure = true, value = "_, _, _ -> new")
    @NotNull
    BoundaryVisualization create(@NotNull World world, @NotNull IntVector visualizeFrom, int height);

}
