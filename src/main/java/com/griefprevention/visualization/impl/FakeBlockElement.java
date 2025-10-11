package com.griefprevention.visualization.impl;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;

import com.griefprevention.util.IntVector;
import com.griefprevention.visualization.BlockElement;

/**
 * A {@link BlockElement} that displays itself as a clientside block.
 */
final class FakeBlockElement extends BlockElement
{

    private final @NotNull BlockData realBlock;

    private final @NotNull BlockData visualizedBlock;

    FakeBlockElement(@NotNull IntVector intVector, @NotNull BlockData realBlock, @NotNull BlockData visualizedBlock)
    {
        super(intVector);
        this.realBlock = realBlock;
        this.visualizedBlock = visualizedBlock;
    }

    @Override
    protected void draw(@NotNull Player player, @NotNull World world)
    {
        // Send the player a fake block change event only if the chunk is loaded.
        if (!getCoordinate().isChunkLoaded(world)) return;

        player.sendBlockChange(getCoordinate().toLocation(world), visualizedBlock);
    }

    @Override
    protected void erase(@NotNull Player player, @NotNull World world)
    {
        player.sendBlockChange(getCoordinate().toLocation(world), realBlock);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!super.equals(other)) return false;
        if (getClass() != other.getClass()) return false;
        FakeBlockElement that = (FakeBlockElement) other;
        return realBlock.equals(that.realBlock) && visualizedBlock.equals(that.visualizedBlock);
    }

    @Override
    public int hashCode()
    {
        /*
         * We specifically use the BlockElement hashcode because we
         * want elements at conflicting locations to be overwritten
         * by the last element added - elements are stored in a Set
         * and corners are added last.
         */
        return super.hashCode();
    }

}
