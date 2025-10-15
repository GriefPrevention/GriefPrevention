package me.ryanhamshire.GriefPrevention;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.loot.Lootable;

import org.jetbrains.annotations.NotNull;

//automatically extends a claim downward based on block types detected
public class AutoExtendClaimTask implements Runnable
{

    /**
     * Assemble information and schedule a task to update claim depth to include existing structures.
     *
     * @param claim the claim to extend the depth of
     */
    public static void scheduleAsync(@NotNull Claim claim)
    {
        Location lesserCorner = claim.getLesserBoundaryCorner();
        Location greaterCorner = claim.getGreaterBoundaryCorner();
        World world = lesserCorner.getWorld();

        if (world == null) return;

        int lowestLootableTile = lesserCorner.getBlockY();
        ArrayList<ChunkSnapshot> snapshots = new ArrayList<>();
        for (int chunkX = lesserCorner.getBlockX() / 16; chunkX <= greaterCorner.getBlockX() / 16; chunkX++)
        {
            for (int chunkZ = lesserCorner.getBlockZ() / 16; chunkZ <= greaterCorner.getBlockZ() / 16; chunkZ++)
            {
                if (world.isChunkLoaded(chunkX, chunkZ))
                {
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);

                    // If we're on the main thread, access to tile entities will speed up the process.
                    if (Bukkit.isPrimaryThread())
                    {
                        // Find the lowest non-natural storage block in the chunk.
                        // This way chests, barrels, etc. are always protected even if player block definitions are lacking.
                        lowestLootableTile = Math.min(
                                lowestLootableTile,
                                Arrays.stream(chunk.getTileEntities())
                                        // Accept only Lootable tiles that do not have loot tables.
                                        // Naturally generated Lootables only have a loot table reference until the container is
                                        // accessed. On access the loot table is used to calculate the contents and removed.
                                        // This prevents claims from always extending over unexplored structures, spawners, etc.
                                        .filter(
                                                tile -> tile instanceof Lootable lootable
                                                        && lootable.getLootTable() == null)
                                        // Return smallest value or default to existing min Y if no eligible tiles are present.
                                        .mapToInt(BlockState::getY).min().orElse(lowestLootableTile));
                    }

                    // Save a snapshot of the chunk for more detailed async block searching.
                    snapshots.add(chunk.getChunkSnapshot(false, true, false));
                }
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(
                GriefPrevention.instance,
                new AutoExtendClaimTask(claim, snapshots, world.getEnvironment(), lowestLootableTile));
    }

    private final Claim claim;

    private final ArrayList<ChunkSnapshot> chunks;

    private final Environment worldType;

    private final Map<Biome, Set<Material>> biomePlayerMaterials = new HashMap<>();

    private final int minY;

    private final int lowestExistingY;

    // Definitions of biomes where sand covers surfaces instead of grass.
    static final Set<NamespacedKey> SAND_SOIL_BIOMES = Set.of(
            NamespacedKey.minecraft("snowy_beach"),
            NamespacedKey.minecraft("beach"),
            NamespacedKey.minecraft("desert"));

    private AutoExtendClaimTask(@NotNull Claim claim, @NotNull ArrayList<@NotNull ChunkSnapshot> chunks,
            @NotNull Environment worldType, int lowestExistingY)
    {
        this.claim = claim;
        this.chunks = chunks;
        this.worldType = worldType;
        this.lowestExistingY = Math.min(lowestExistingY, claim.getLesserBoundaryCorner().getBlockY());
        this.minY = Math.max(
                Objects.requireNonNull(claim.getLesserBoundaryCorner().getWorld()).getMinHeight(),
                GriefPrevention.instance.config_claims_maxDepth);
    }

    @Override
    public void run()
    {
        int newY = this.getLowestBuiltY();
        if (newY < this.claim.getLesserBoundaryCorner().getBlockY())
        {
            Bukkit.getScheduler().runTask(GriefPrevention.instance, new ExecuteExtendClaimTask(claim, newY));
        }
    }

    private int getLowestBuiltY()
    {
        int y = this.lowestExistingY;

        if (yTooSmall(y)) return this.minY;

        for (ChunkSnapshot chunk : this.chunks)
        {
            y = findLowerBuiltY(chunk, y);

            // If already at minimum Y, stop searching.
            if (yTooSmall(y)) return this.minY;
        }

        return y;
    }

    private int findLowerBuiltY(ChunkSnapshot chunkSnapshot, int y)
    {
        // Specifically not using yTooSmall here to allow protecting bottom layer.
        nextY : for (int newY = y - 1; newY >= this.minY; newY--)
        {
            for (int x = 0; x < 16; x++)
            {
                for (int z = 0; z < 16; z++)
                {
                    // If the block is natural, ignore it and continue searching the same Y level.
                    if (!isPlayerBlock(chunkSnapshot, x, newY, z)) continue;

                    // If the block is player-placed and we're at the minimum Y allowed, we're done searching.
                    if (yTooSmall(y)) return this.minY;

                    // Because we found a player block, repeatedly check the next block in the column.
                    while (isPlayerBlock(chunkSnapshot, x, --newY, z))
                    {
                        // If we've hit minimum Y we're done searching.
                        if (yTooSmall(y)) return this.minY;
                    }

                    // Undo increment for unsuccessful player block check.
                    newY++;

                    // Move built level down to current level.
                    y = newY;

                    // Because the level is now protected, continue downwards.
                    continue nextY;
                }
            }
        }

        // Return provided value or last located player block level.
        return y;
    }

    private boolean yTooSmall(int y)
    {
        return y <= this.minY;
    }

    private boolean isPlayerBlock(ChunkSnapshot chunkSnapshot, int x, int y, int z)
    {
        Material blockType = chunkSnapshot.getBlockType(x, y, z);
        Biome biome = chunkSnapshot.getBiome(x, y, z);

        return this.getBiomePlayerBlocks(biome).contains(blockType);
    }

    private Set<Material> getBiomePlayerBlocks(Biome biome)
    {
        return biomePlayerMaterials.computeIfAbsent(biome, newBiome -> {
            Set<Material> playerBlocks = AutoExtendClaimTask.getPlayerBlocks(this.worldType, newBiome);
            playerBlocks.removeAll(BlockEventHandler.TRASH_BLOCKS);
            return playerBlocks;
        });
    }

    static Set<Material> getPlayerBlocks(Environment environment, Biome biome)
    {
        Set<Material> playerBlocks = new HashSet<>();
        playerBlocks.addAll(Tag.ANVIL.getValues());
        playerBlocks.addAll(Tag.BANNERS.getValues());
        playerBlocks.addAll(Tag.BEACON_BASE_BLOCKS.getValues());
        playerBlocks.addAll(Tag.BEDS.getValues());
        playerBlocks.addAll(Tag.BUTTONS.getValues());
        playerBlocks.addAll(Tag.CAMPFIRES.getValues());
        playerBlocks.addAll(Tag.CANDLE_CAKES.getValues());
        playerBlocks.addAll(Tag.CANDLES.getValues());
        playerBlocks.addAll(Tag.WOOL_CARPETS.getValues());
        playerBlocks.addAll(Tag.CAULDRONS.getValues());
        playerBlocks.addAll(Tag.DOORS.getValues());
        playerBlocks.addAll(Tag.FENCE_GATES.getValues());
        playerBlocks.addAll(Tag.FENCES.getValues());
        playerBlocks.addAll(Tag.FIRE.getValues());
        playerBlocks.addAll(Tag.FLOWER_POTS.getValues());
        playerBlocks.addAll(Tag.IMPERMEABLE.getValues()); // Glass block variants
        playerBlocks.addAll(Tag.LOGS.getValues());
        playerBlocks.addAll(Tag.PLANKS.getValues());
        playerBlocks.addAll(Tag.PRESSURE_PLATES.getValues());
        playerBlocks.addAll(Tag.RAILS.getValues());
        playerBlocks.addAll(Tag.SHULKER_BOXES.getValues());
        playerBlocks.addAll(Tag.SIGNS.getValues());
        playerBlocks.addAll(Tag.SLABS.getValues());
        playerBlocks.addAll(Tag.STAIRS.getValues());
        playerBlocks.addAll(Tag.STONE_BRICKS.getValues());
        playerBlocks.addAll(Tag.TRAPDOORS.getValues());
        playerBlocks.addAll(Tag.WALLS.getValues());
        playerBlocks.addAll(Tag.WOOL.getValues());
        playerBlocks.add(Material.BOOKSHELF);
        playerBlocks.add(Material.BREWING_STAND);
        playerBlocks.add(Material.BRICK);
        playerBlocks.add(Material.COBBLESTONE);
        playerBlocks.add(Material.LAPIS_BLOCK);
        playerBlocks.add(Material.DISPENSER);
        playerBlocks.add(Material.NOTE_BLOCK);
        playerBlocks.add(Material.STICKY_PISTON);
        playerBlocks.add(Material.PISTON);
        playerBlocks.add(Material.PISTON_HEAD);
        playerBlocks.add(Material.MOVING_PISTON);
        playerBlocks.add(Material.WHEAT);
        playerBlocks.add(Material.TNT);
        playerBlocks.add(Material.MOSSY_COBBLESTONE);
        playerBlocks.add(Material.TORCH);
        playerBlocks.add(Material.CHEST);
        playerBlocks.add(Material.REDSTONE_WIRE);
        playerBlocks.add(Material.CRAFTING_TABLE);
        playerBlocks.add(Material.FURNACE);
        playerBlocks.add(Material.LADDER);
        playerBlocks.add(Material.SCAFFOLDING);
        playerBlocks.add(Material.LEVER);
        playerBlocks.add(Material.REDSTONE_TORCH);
        playerBlocks.add(Material.SNOW_BLOCK);
        playerBlocks.add(Material.JUKEBOX);
        playerBlocks.add(Material.NETHER_PORTAL);
        playerBlocks.add(Material.JACK_O_LANTERN);
        playerBlocks.add(Material.CAKE);
        playerBlocks.add(Material.REPEATER);
        playerBlocks.add(Material.MUSHROOM_STEM);
        playerBlocks.add(Material.RED_MUSHROOM_BLOCK);
        playerBlocks.add(Material.BROWN_MUSHROOM_BLOCK);
        playerBlocks.add(Material.IRON_BARS);
        playerBlocks.add(Material.GLASS_PANE);
        playerBlocks.add(Material.MELON_STEM);
        playerBlocks.add(Material.ENCHANTING_TABLE);
        playerBlocks.add(Material.COBWEB);
        playerBlocks.add(Material.GRAVEL);
        playerBlocks.add(Material.SANDSTONE);
        playerBlocks.add(Material.ENDER_CHEST);
        playerBlocks.add(Material.COMMAND_BLOCK);
        playerBlocks.add(Material.REPEATING_COMMAND_BLOCK);
        playerBlocks.add(Material.CHAIN_COMMAND_BLOCK);
        playerBlocks.add(Material.BEACON);
        playerBlocks.add(Material.CARROT);
        playerBlocks.add(Material.POTATO);
        playerBlocks.add(Material.SKELETON_SKULL);
        playerBlocks.add(Material.WITHER_SKELETON_SKULL);
        playerBlocks.add(Material.CREEPER_HEAD);
        playerBlocks.add(Material.ZOMBIE_HEAD);
        playerBlocks.add(Material.PLAYER_HEAD);
        playerBlocks.add(Material.DRAGON_HEAD);
        playerBlocks.add(Material.SPONGE);
        playerBlocks.add(Material.WHITE_STAINED_GLASS_PANE);
        playerBlocks.add(Material.ORANGE_STAINED_GLASS_PANE);
        playerBlocks.add(Material.MAGENTA_STAINED_GLASS_PANE);
        playerBlocks.add(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        playerBlocks.add(Material.YELLOW_STAINED_GLASS_PANE);
        playerBlocks.add(Material.LIME_STAINED_GLASS_PANE);
        playerBlocks.add(Material.PINK_STAINED_GLASS_PANE);
        playerBlocks.add(Material.GRAY_STAINED_GLASS_PANE);
        playerBlocks.add(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        playerBlocks.add(Material.CYAN_STAINED_GLASS_PANE);
        playerBlocks.add(Material.PURPLE_STAINED_GLASS_PANE);
        playerBlocks.add(Material.BLUE_STAINED_GLASS_PANE);
        playerBlocks.add(Material.BROWN_STAINED_GLASS_PANE);
        playerBlocks.add(Material.GREEN_STAINED_GLASS_PANE);
        playerBlocks.add(Material.RED_STAINED_GLASS_PANE);
        playerBlocks.add(Material.BLACK_STAINED_GLASS_PANE);
        playerBlocks.add(Material.TRAPPED_CHEST);
        playerBlocks.add(Material.COMPARATOR);
        playerBlocks.add(Material.DAYLIGHT_DETECTOR);
        playerBlocks.add(Material.REDSTONE_BLOCK);
        playerBlocks.add(Material.HOPPER);
        playerBlocks.add(Material.QUARTZ_BLOCK);
        playerBlocks.add(Material.DROPPER);
        playerBlocks.add(Material.SLIME_BLOCK);
        playerBlocks.add(Material.PRISMARINE);
        playerBlocks.add(Material.HAY_BLOCK);
        playerBlocks.add(Material.SEA_LANTERN);
        playerBlocks.add(Material.COAL_BLOCK);
        playerBlocks.add(Material.REDSTONE_LAMP);
        playerBlocks.add(Material.RED_NETHER_BRICKS);
        playerBlocks.add(Material.POLISHED_ANDESITE);
        playerBlocks.add(Material.POLISHED_DIORITE);
        playerBlocks.add(Material.POLISHED_GRANITE);
        playerBlocks.add(Material.POLISHED_BASALT);
        playerBlocks.add(Material.POLISHED_DEEPSLATE);
        playerBlocks.add(Material.DEEPSLATE_BRICKS);
        playerBlocks.add(Material.CRACKED_DEEPSLATE_BRICKS);
        playerBlocks.add(Material.DEEPSLATE_TILES);
        playerBlocks.add(Material.CRACKED_DEEPSLATE_TILES);
        playerBlocks.add(Material.CHISELED_DEEPSLATE);
        playerBlocks.add(Material.RAW_COPPER_BLOCK);
        playerBlocks.add(Material.RAW_IRON_BLOCK);
        playerBlocks.add(Material.RAW_GOLD_BLOCK);
        playerBlocks.add(Material.LIGHTNING_ROD);
        playerBlocks.add(Material.DECORATED_POT);

        //these are unnatural in the nether and end
        if (environment != Environment.NORMAL && environment != Environment.CUSTOM)
        {
            playerBlocks.addAll(Tag.BASE_STONE_OVERWORLD.getValues());
            playerBlocks.addAll(Tag.DIRT.getValues());
            playerBlocks.addAll(Tag.SAND.getValues());
        }

        //these are unnatural in the standard world, but not in the nether
        if (environment != Environment.NETHER)
        {
            playerBlocks.addAll(Tag.NYLIUM.getValues());
            playerBlocks.addAll(Tag.WART_BLOCKS.getValues());
            playerBlocks.addAll(Tag.BASE_STONE_NETHER.getValues());
            playerBlocks.add(Material.POLISHED_BLACKSTONE);
            playerBlocks.add(Material.CHISELED_POLISHED_BLACKSTONE);
            playerBlocks.add(Material.CRACKED_POLISHED_BLACKSTONE_BRICKS);
            playerBlocks.add(Material.GILDED_BLACKSTONE);
            playerBlocks.add(Material.BONE_BLOCK);
            playerBlocks.add(Material.SOUL_SAND);
            playerBlocks.add(Material.SOUL_SOIL);
            playerBlocks.add(Material.GLOWSTONE);
            playerBlocks.add(Material.NETHER_BRICK);
            playerBlocks.add(Material.MAGMA_BLOCK);
            playerBlocks.add(Material.ANCIENT_DEBRIS);
            playerBlocks.add(Material.CHAIN);
            playerBlocks.add(Material.SHROOMLIGHT);
            playerBlocks.add(Material.NETHER_GOLD_ORE);
            playerBlocks.add(Material.NETHER_SPROUTS);
            playerBlocks.add(Material.CRIMSON_FUNGUS);
            playerBlocks.add(Material.CRIMSON_ROOTS);
            playerBlocks.add(Material.NETHER_WART_BLOCK);
            playerBlocks.add(Material.WEEPING_VINES);
            playerBlocks.add(Material.WEEPING_VINES_PLANT);
            playerBlocks.add(Material.WARPED_FUNGUS);
            playerBlocks.add(Material.WARPED_ROOTS);
            playerBlocks.add(Material.WARPED_WART_BLOCK);
            playerBlocks.add(Material.TWISTING_VINES);
            playerBlocks.add(Material.TWISTING_VINES_PLANT);
        }
        //blocks from tags that are natural in the nether
        else
        {
            playerBlocks.remove(Material.CRIMSON_STEM);
            playerBlocks.remove(Material.CRIMSON_HYPHAE);
            playerBlocks.remove(Material.NETHER_BRICK_FENCE);
            playerBlocks.remove(Material.NETHER_BRICK_STAIRS);
            playerBlocks.remove(Material.SOUL_FIRE);
            playerBlocks.remove(Material.WARPED_STEM);
            playerBlocks.remove(Material.WARPED_HYPHAE);
        }

        //these are unnatural in the standard and nether worlds, but not in the end
        if (environment != Environment.THE_END)
        {
            playerBlocks.add(Material.CHORUS_PLANT);
            playerBlocks.add(Material.CHORUS_FLOWER);
            playerBlocks.add(Material.END_ROD);
            playerBlocks.add(Material.END_STONE);
            playerBlocks.add(Material.END_STONE_BRICKS);
            playerBlocks.add(Material.OBSIDIAN);
            playerBlocks.add(Material.PURPUR_BLOCK);
            playerBlocks.add(Material.PURPUR_PILLAR);
        }
        //blocks from tags that are natural in the end
        else
        {
            playerBlocks.remove(Material.PURPUR_SLAB);
            playerBlocks.remove(Material.PURPUR_STAIRS);
        }

        //these are unnatural in sandy biomes, but not elsewhere
        if (SAND_SOIL_BIOMES.contains(biome.getKey()) || environment != Environment.NORMAL)
        {
            playerBlocks.addAll(Tag.LEAVES.getValues());
        }
        //blocks from tags that are natural in non-sandy normal biomes
        else
        {
            playerBlocks.remove(Material.OAK_LOG);
            playerBlocks.remove(Material.SPRUCE_LOG);
            playerBlocks.remove(Material.BIRCH_LOG);
            playerBlocks.remove(Material.JUNGLE_LOG);
            playerBlocks.remove(Material.ACACIA_LOG);
            playerBlocks.remove(Material.DARK_OAK_LOG);
        }

        return playerBlocks;
    }

    //runs in the main execution thread, where it can safely change claims and save those changes
    private record ExecuteExtendClaimTask(Claim claim, int newY) implements Runnable {

        @Override
        public void run()
        {
            GriefPrevention.instance.dataStore.extendClaim(claim, newY);
        }
    }

}
