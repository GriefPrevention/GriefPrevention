package com.griefprevention.visualization;

import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

/**
 * Supplies fake block data for block-based boundary visualizations.
 */
public interface BlockBoundaryRenderer
{

    /**
     * Create the block data used for corners in a boundary visualization.
     *
     * @param boundary the boundary being visualized
     * @return the block data to display
     */
    @NotNull BlockData createCornerBlockData(@NotNull Boundary boundary);

    /**
     * Create the block data used for sides in a boundary visualization.
     *
     * @param boundary the boundary being visualized
     * @return the block data to display
     */
    @NotNull BlockData createSideBlockData(@NotNull Boundary boundary);

}
