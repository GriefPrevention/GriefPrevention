package com.griefprevention.visualization.impl;

import java.util.Collection;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import org.jetbrains.annotations.NotNull;

import com.griefprevention.util.IntVector;

/**
 * A {@link FakeBlockVisualization} with maximum anti-cheat compatibility.
 */
public class AntiCheatCompatVisualization extends FakeBlockVisualization
{

    /**
     * Construct a new {@code AntiCheatCompatVisualization}.
     *
     * @param world the {@link World} being visualized in
     * @param visualizeFrom the {@link IntVector} representing the world coordinate being visualized from
     * @param height the height of the visualization
     */
    public AntiCheatCompatVisualization(@NotNull World world, @NotNull IntVector visualizeFrom, int height)
    {
        super(world, visualizeFrom, height);
    }

    @Override
    protected boolean isTransparent(@NotNull Block block)
    {
        Collection<BoundingBox> boundingBoxes = block.getCollisionShape().getBoundingBoxes();
        // Decide transparency based on whether block physical bounding box occupies the entire block volume.
        return boundingBoxes.isEmpty() || !boundingBoxes.stream().allMatch(box -> box.getVolume() == 1.0);
    }

}
