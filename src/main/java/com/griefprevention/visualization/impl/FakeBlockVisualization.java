package com.griefprevention.visualization.impl;

import com.griefprevention.util.IntVector;
import com.griefprevention.visualization.BlockBoundaryVisualization;
import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A {@link BoundaryVisualization} implementation that displays clientside blocks along
 * {@link com.griefprevention.visualization.Boundary Boundaries}.
 */
public class FakeBlockVisualization extends BlockBoundaryVisualization
{

    protected final boolean waterTransparent;

    /**
     * Construct a new {@code FakeBlockVisualization}.
     *
     * @param world the {@link World} being visualized in
     * @param visualizeFrom the {@link IntVector} representing the world coordinate being visualized from
     * @param height the height of the visualization
     */
    public FakeBlockVisualization(@NotNull World world, @NotNull IntVector visualizeFrom, int height) {
        super(world, visualizeFrom, height);

        // Water is considered transparent based on whether the visualization is initiated in water.
        waterTransparent = visualizeFrom.toBlock(world).getType() == Material.WATER;
    }

    @Override
    protected @NotNull Consumer<@NotNull IntVector> addCornerElements(@NotNull Boundary boundary)
    {
        return createElementAdder(switch (boundary.type())
        {
            case SUBDIVISION_3D -> Material.IRON_BLOCK.createBlockData();
            case SUBDIVISION -> Material.IRON_BLOCK.createBlockData();
            case INITIALIZE_ZONE -> Material.DIAMOND_BLOCK.createBlockData();
            case CONFLICT_ZONE -> {
                BlockData fakeData = Material.REDSTONE_ORE.createBlockData();
                ((Lightable) fakeData).setLit(true);
                yield fakeData;
            }
            default -> Material.GLOWSTONE.createBlockData();
        }, boundary.type(), boundary.type() == VisualizationType.SUBDIVISION_3D);
    }


    @Override
    protected @NotNull Consumer<@NotNull IntVector> addSideElements(@NotNull Boundary boundary)
    {
        // Determine BlockData from boundary type to cache for reuse in function.
        return createElementAdder(switch (boundary.type())
        {
            case ADMIN_CLAIM -> Material.PUMPKIN.createBlockData();
            case SUBDIVISION -> Material.WHITE_WOOL.createBlockData();
            case SUBDIVISION_3D -> Material.WHITE_WOOL.createBlockData();
            case INITIALIZE_ZONE -> Material.DIAMOND_BLOCK.createBlockData();
            case CONFLICT_ZONE -> Material.NETHERRACK.createBlockData();
            default -> Material.GOLD_BLOCK.createBlockData();
        }, boundary.type(), boundary.type() == VisualizationType.SUBDIVISION_3D);
    }

    @Override
    protected void draw(@NotNull Player player, @NotNull Boundary boundary)
    {
        // Use 3D-specific drawing for 3D subdivisions, otherwise use standard 2D drawing
        if (boundary.type() == VisualizationType.SUBDIVISION_3D) {
            drawRespectingYBoundaries(player, boundary);
        } else {
            super.draw(player, boundary);
        }
    }

    /**
     * Draw a 3D subdivision boundary while respecting Y boundaries.
     */
    private void drawRespectingYBoundaries(@NotNull Player player, @NotNull Boundary boundary)
    {
        BoundingBox area = boundary.bounds();
        Claim claim = boundary.claim();
        
        if (claim == null || !claim.is3D()) 
        {
            // Fallback to default behavior if claim is null or not 3D
            super.draw(player, boundary);
            return;
        }

        // Get the Y boundaries of the 3D subclaim
        int claimMinY = area.getMinY();
        int claimMaxY = area.getMaxY();

        // Replicate display zone logic
        final int displayZoneRadius = 75;
        int baseX = this.visualizeFrom.x();
        int baseZ = this.visualizeFrom.z();
        int worldMinY = world.getMinHeight();
        int worldMaxY = world.getMaxHeight();
        int minShowY = Math.max(worldMinY, claimMinY - 1);
        int maxShowY = Math.min(worldMaxY, claimMaxY + 1);
        BoundingBox displayZoneArea = new BoundingBox(
                new IntVector(baseX - displayZoneRadius, minShowY, baseZ - displayZoneRadius),
                new IntVector(baseX + displayZoneRadius, maxShowY, baseZ + displayZoneRadius));
        
        // Trim to area
        BoundingBox displayZone = displayZoneArea.intersection(area);
        if (displayZone == null) return;

        Consumer<@NotNull IntVector> addCorner = addCornerElements(boundary);
        Consumer<@NotNull IntVector> addSide = addSideElements(boundary);

        // Render at top and bottom Y boundaries for 3D subdivisions
        int[] yLevels = new int[] { claimMinY, claimMaxY };
        for (int y : yLevels)
        {
            if (y < world.getMinHeight() || y > world.getMaxHeight()) continue;

            // Short directional side markers next to corners
            if (area.getLength() > 2)
            {
                addDisplayed3D(displayZone, new IntVector(area.getMinX() + 1, y, area.getMaxZ()), addSide);
                addDisplayed3D(displayZone, new IntVector(area.getMinX() + 1, y, area.getMinZ()), addSide);
                addDisplayed3D(displayZone, new IntVector(area.getMaxX() - 1, y, area.getMaxZ()), addSide);
                addDisplayed3D(displayZone, new IntVector(area.getMaxX() - 1, y, area.getMinZ()), addSide);
            }
            if (area.getWidth() > 2)
            {
                addDisplayed3D(displayZone, new IntVector(area.getMinX(), y, area.getMinZ() + 1), addSide);
                addDisplayed3D(displayZone, new IntVector(area.getMaxX(), y, area.getMinZ() + 1), addSide);
                addDisplayed3D(displayZone, new IntVector(area.getMinX(), y, area.getMaxZ() - 1), addSide);
                addDisplayed3D(displayZone, new IntVector(area.getMaxX(), y, area.getMaxZ() - 1), addSide);
            }

            // Corners at this Y level
            addDisplayed3D(displayZone, new IntVector(area.getMinX(), y, area.getMaxZ()), addCorner);
            addDisplayed3D(displayZone, new IntVector(area.getMaxX(), y, area.getMaxZ()), addCorner);
            addDisplayed3D(displayZone, new IntVector(area.getMinX(), y, area.getMinZ()), addCorner);
            addDisplayed3D(displayZone, new IntVector(area.getMaxX(), y, area.getMinZ()), addCorner);

            // Vertical indicator blocks
            int verticalY = (y == claimMinY) ? y + 1 : y - 1;
            if (verticalY >= world.getMinHeight() && verticalY <= world.getMaxHeight()) {
                addDisplayed3D(displayZone, new IntVector(area.getMinX(), verticalY, area.getMaxZ()), addSide);
                addDisplayed3D(displayZone, new IntVector(area.getMaxX(), verticalY, area.getMaxZ()), addSide);
                addDisplayed3D(displayZone, new IntVector(area.getMinX(), verticalY, area.getMinZ()), addSide);
                addDisplayed3D(displayZone, new IntVector(area.getMaxX(), verticalY, area.getMinZ()), addSide);
            }
        }
    }

    /**
     * Add a display element if within display zone (3D version).
     */
    private void addDisplayed3D(
            @NotNull BoundingBox displayZone,
            @NotNull IntVector coordinate,
            @NotNull Consumer<@NotNull IntVector> addElement)
    {
        if (coordinate.x() >= displayZone.getMinX() && coordinate.x() <= displayZone.getMaxX() &&
            coordinate.y() >= displayZone.getMinY() && coordinate.y() <= displayZone.getMaxY() &&
            coordinate.z() >= displayZone.getMinZ() && coordinate.z() <= displayZone.getMaxZ())
        {
            addElement.accept(coordinate);
        }
    }

    /**
     * Create an element adder that either snaps to terrain or places exactly based on exact3D flag.
     */
    private @NotNull Consumer<@NotNull IntVector> createElementAdder(
            @NotNull BlockData fakeData,
            @NotNull VisualizationType type,
            boolean exact3D)
    {
        if (exact3D) {
            return addExactBlockElement(fakeData);
        } else {
            return addBlockElement(fakeData);
        }
    }

    /**
     * Create a {@link Consumer} that adds an appropriate {@link FakeBlockElement} for the given {@link IntVector}.
     *
     * @param fakeData the fake {@link BlockData}
     * @return the function for determining a visible fake block location
     */
    private @NotNull Consumer<@NotNull IntVector> addBlockElement(@NotNull BlockData fakeData)
    {
        return vector -> {
            // Obtain visible location from starting point.
            Block visibleLocation = getVisibleLocation(vector);
            // Create an element using our fake data and the determined block's real data.
            elements.add(new FakeBlockElement(new IntVector(visibleLocation), visibleLocation.getBlockData(), fakeData));
        };
    }

    /**
     * Find a location that should be visible to players. This causes the visualization to "cling" to the ground.
     *
     * @param vector the {@link IntVector} of the display location
     * @return the located {@link Block}
     */
    private Block getVisibleLocation(@NotNull IntVector vector)
    {
        Block block = vector.toBlock(world);
        BlockFace direction = (isTransparent(block)) ? BlockFace.DOWN : BlockFace.UP;

        while (block.getY() >= world.getMinHeight() &&
                block.getY() < world.getMaxHeight() - 1 &&
                (!isTransparent(block.getRelative(BlockFace.UP)) || isTransparent(block)))
        {
            block = block.getRelative(direction);
        }

        return block;
    }

    /**
     * Helper method for determining if a {@link Block} is transparent from the top down.
     *
     * @param block the {@code Block}
     * @return true if transparent
     */
    protected boolean isTransparent(@NotNull Block block)
    {
        Material blockMaterial = block.getType();

        // Custom per-material definitions.
        switch (blockMaterial)
        {
            case WATER:
                return waterTransparent;
            case SNOW:
                return false;
        }

        if (blockMaterial.isAir()
                || Tag.FENCES.isTagged(blockMaterial)
                || Tag.FENCE_GATES.isTagged(blockMaterial)
                || Tag.SIGNS.isTagged(blockMaterial)
                || Tag.WALLS.isTagged(blockMaterial)
                || Tag.WALL_SIGNS.isTagged(blockMaterial))
            return true;

        return block.getType().isTransparent();
    }

    /**
     * Create a {@link Consumer} that adds a {@link FakeBlockElement} exactly at the given {@link IntVector}
     * coordinate without searching for a nearby visible ground block. This is used for 3D subdivision corners
     * so they are highlighted even when floating in air.
     *
     * @param fakeData the fake {@link BlockData}
     * @return the function for placing a fake block at the exact location
     */
    private @NotNull Consumer<@NotNull IntVector> addExactBlockElement(@NotNull BlockData fakeData)
    {
        return vector -> {
            Block block = vector.toBlock(world);
            elements.add(new FakeBlockElement(vector, block.getBlockData(), fakeData));
        };
    }

}
