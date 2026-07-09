package com.griefprevention.protection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class ChestProtectionHandler
{

    private ChestProtectionHandler()
    {
        throw new AssertionError("Utility class must not be instantiated.");
    }

    public static void denyConnectingDoubleChestsAcrossClaimBoundary(DataStore dataStore, Claim claim, Block block, Player player)
    {
        // Only apply this logic to placed chests.
        if (!(block.getBlockData() instanceof Chest chest)) return;

        // Only interfere when Minecraft already connected this chest to a side
        // that should not be allowed by the claim ownership rule.
        BlockFace connectedFace = getConnectedFace(chest);
        if (connectedFace == null) return;

        Block connectedBlock = block.getRelative(connectedFace);
        if (!(connectedBlock.getBlockData() instanceof Chest connectedChest)) return;
        if (block.getType() != connectedBlock.getType()) return;

        Claim connectedClaim = dataStore.getClaimAt(connectedBlock.getLocation(), true, claim);

        if (ProtectionHelper.sameClaimOwner(claim, connectedClaim)) return;

        // Avoid fallback chest connections when sneaking
        if (player.isSneaking())
        {
            splitDoubleChest(block, chest, connectedBlock, connectedChest, player);
            return;
        }

        // Vanilla connected to a side that should not be allowed. Look for another
        // allowed side that Minecraft could naturally connect to instead.
        BlockFace allowedFace = findAllowedSingleChestConnectionFace(dataStore, claim, block, chest, connectedFace);

        // Always undo the invalid vanilla connection first.
        splitDoubleChest(block, chest, connectedBlock, connectedChest, player);

        if (allowedFace == null) return;

        Block allowedBlock = block.getRelative(allowedFace);
        if (!(allowedBlock.getBlockData() instanceof Chest allowedChest)) return;
        if (block.getType() != allowedBlock.getType()) return;

        connectDoubleChest(chest, block, allowedChest, allowedBlock, allowedFace, player);
    }

    // Checks whether the opposite side of the denied connection has an allowed,
    // naturally connectable single chest.
    private static @Nullable BlockFace findAllowedSingleChestConnectionFace(DataStore dataStore, Claim claim, Block block,
                                                                            Chest chest, BlockFace deniedFace)
    {
        // A normal double chest can only connect on the left/right axis.
        // Since vanilla already connected one side and that side was denied,
        // the only remaining possible vanilla-style alternative is the opposite side.
        BlockFace face = deniedFace.getOppositeFace();

        Block relative = block.getRelative(face);
        if (!(relative.getBlockData() instanceof Chest relativeChest)) return null;
        if (block.getType() != relative.getType()) return null;
        if (relativeChest.getType() != Chest.Type.SINGLE) return null;

        // Do not treat claim permission as enough to force a connection.
        // The neighboring chest must also be naturally connectable.
        if (cannotNaturallyConnectChests(chest, relativeChest, face)) return null;

        Claim relativeClaim = dataStore.getClaimAt(relative.getLocation(), true, claim);
        if (!ProtectionHelper.sameClaimOwner(claim, relativeClaim)) return null;

        return face;
    }

    // Splits a double chest back into two single chests.
    private static void splitDoubleChest(Block placedBlock, Chest placedChest, Block connectedBlock, Chest connectedChest,
                                         Player player)
    {
        placedChest.setType(Chest.Type.SINGLE);
        placedBlock.setBlockData(placedChest);

        connectedChest.setType(Chest.Type.SINGLE);
        connectedBlock.setBlockData(connectedChest);

        player.sendBlockChange(placedBlock.getLocation(), placedChest);
        player.sendBlockChange(connectedBlock.getLocation(), connectedChest);
    }

    // Checks whether two chests could naturally form a double chest.
    // This prevents the plugin from rotating an existing neighboring chest.
    private static boolean cannotNaturallyConnectChests(Chest placedChest, Chest relativeChest, BlockFace relativeFace)
    {
        // Minecraft only forms a normal double chest when both chests face the same direction.
        if (placedChest.getFacing() != relativeChest.getFacing()) return true;

        // The placed chest must be able to connect on the relative side,
        // and the relative chest must be able to connect back on the opposite side.
        return isInvalidChestConnectionFace(placedChest, relativeFace) ||
                isInvalidChestConnectionFace(relativeChest, relativeFace.getOppositeFace());
    }

    // Checks whether the neighbor is on a side where this chest cannot form a double chest.
    private static boolean isInvalidChestConnectionFace(Chest chest, BlockFace face)
    {
        BlockFace facing = chest.getFacing();
        return face != rotateClockwise(facing) && face != rotateCounterClockwise(facing);
    }

    // Connects two allowed single chests to become one double chest.
    // This method never changes the facing of the neighboring chest.
    private static void connectDoubleChest(Chest placedChest, Block placedBlock, Chest relativeChest, Block relativeBlock,
                                           BlockFace relativeFace, Player player)
    {
        // Safety check: only connect if this would be a natural double chest.
        if (cannotNaturallyConnectChests(placedChest, relativeChest, relativeFace)) return;

        BlockFace facing = placedChest.getFacing();

        // Calculate the correct LEFT/RIGHT type for both chest blocks.
        Chest.Type placedType = getChestTypeForConnection(facing, relativeFace);
        Chest.Type relativeType = getChestTypeForConnection(facing, relativeFace.getOppositeFace());

        if (placedType == Chest.Type.SINGLE || relativeType == Chest.Type.SINGLE) return;

        placedChest.setType(placedType);
        placedBlock.setBlockData(placedChest);

        relativeChest.setType(relativeType);
        relativeBlock.setBlockData(relativeChest);

        // Resend both blocks to prevent visual desync.
        player.sendBlockChange(placedBlock.getLocation(), placedChest);
        player.sendBlockChange(relativeBlock.getLocation(), relativeChest);
    }

    // Returns the side where the chest is currently connected.
    private static @Nullable BlockFace getConnectedFace(Chest chest)
    {
        if (chest.getType() == Chest.Type.LEFT) return rotateClockwise(chest.getFacing());
        if (chest.getType() == Chest.Type.RIGHT) return rotateCounterClockwise(chest.getFacing());
        return null;
    }

    // Converts a connected side into the correct chest half type.
    private static Chest.Type getChestTypeForConnection(BlockFace facing, BlockFace connectedFace)
    {
        if (connectedFace == rotateClockwise(facing)) return Chest.Type.LEFT;
        if (connectedFace == rotateCounterClockwise(facing)) return Chest.Type.RIGHT;
        return Chest.Type.SINGLE;
    }

    // Rotates a horizontal direction clockwise.
    private static BlockFace rotateClockwise(BlockFace face)
    {
        if (face == BlockFace.NORTH) return BlockFace.EAST;
        if (face == BlockFace.EAST) return BlockFace.SOUTH;
        if (face == BlockFace.SOUTH) return BlockFace.WEST;
        if (face == BlockFace.WEST) return BlockFace.NORTH;
        return face;
    }

    // Rotates a horizontal direction counter-clockwise.
    private static BlockFace rotateCounterClockwise(BlockFace face)
    {
        if (face == BlockFace.NORTH) return BlockFace.WEST;
        if (face == BlockFace.WEST) return BlockFace.SOUTH;
        if (face == BlockFace.SOUTH) return BlockFace.EAST;
        if (face == BlockFace.EAST) return BlockFace.NORTH;
        return face;
    }
}
