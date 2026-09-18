package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.BlockProjectileSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BlockEventHandlerTest
{
    private static final UUID PLAYER_UUID = UUID.fromString("fa8d60a7-9645-4a9f-b74d-173966174739");

    @BeforeAll
    static void beforeAll()
    {
        Server server = ServerMocks.newServer();
        doAnswer(invocation ->
        {
            Tag<?> tag = mock();
            doReturn(Set.of()).when(tag).getValues();
            return tag;
        }).when(server).getTag(notNull(), notNull(), notNull());
        Bukkit.setServer(server);

        // Force initialization of InventoryType before tests run.
        // In 1.21.10+, InventoryType depends on MenuType which requires registry lookups.
        // Initializing here ensures this happens with proper mocks, not mid-stubbing.
        InventoryType.values();

        // Touch class to load material list.
        //noinspection ResultOfMethodCallIgnored
        BlockEventHandler.class.getName();
    }

    @AfterAll
    static void afterAll()
    {
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void verifyNormalHopperPassthrough()
    {
        // Verify that we don't cancel events for unprotected items.

        Item item = mock(Item.class);
        Inventory inventory = mock(Inventory.class);
        InventoryPickupItemEvent event = mock(InventoryPickupItemEvent.class);
        when(item.getMetadata("GP_ITEMOWNER")).thenReturn(List.of());
        when(inventory.getType()).thenReturn(InventoryType.HOPPER);
        when(event.getItem()).thenReturn(item);
        when(event.getInventory()).thenReturn(inventory);
        BlockEventHandler handler = new BlockEventHandler(null);

        handler.onInventoryPickupItem(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void verifyNoHopperPassthroughWhenItemIsProtected()
    {
        // Verify that we DO cancel events for items that are protected.

        Item item = mock(Item.class);
        when(item.getMetadata("GP_ITEMOWNER"))
                .thenReturn(List.of(new FixedMetadataValue(mock(Plugin.class), PLAYER_UUID)));
        Inventory inventory = mock(Inventory.class);
        when(inventory.getType()).thenReturn(InventoryType.HOPPER);
        DataStore dataStore = mock(DataStore.class);
        when(dataStore.getPlayerData(PLAYER_UUID)).thenReturn(new PlayerData());
        BlockEventHandler handler = new BlockEventHandler(dataStore);
        InventoryPickupItemEvent event = mock(InventoryPickupItemEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getItem()).thenReturn(item);
        Server server = mock(Server.class);
        when(server.getPlayer(PLAYER_UUID)).thenReturn(mock(Player.class));

        try (var bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getServer).thenReturn(server);

            handler.onInventoryPickupItem(event);
        }

        verify(event).setCancelled(true);
    }

    @Test
    void verifyHopperPassthroughWhenItemIsProtectedButOwnerIsOffline()
    {
        // Verify that we don't cancel events for items that are protected, but where
        // the owner of those items is not logged in.
        // This behaviour matches older versions of GriefPrevention.

        Item item = mock(Item.class);
        when(item.getMetadata("GP_ITEMOWNER"))
                .thenReturn(List.of(new FixedMetadataValue(mock(Plugin.class), PLAYER_UUID)));
        Inventory inventory = mock(Inventory.class);
        when(inventory.getType()).thenReturn(InventoryType.HOPPER);
        BlockEventHandler handler = new BlockEventHandler(null);
        InventoryPickupItemEvent event = mock(InventoryPickupItemEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getItem()).thenReturn(item);
        Server server = mock(Server.class);
        when(server.getPlayer(PLAYER_UUID)).thenReturn(null);

        try (var bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getServer).thenReturn(server);

            handler.onInventoryPickupItem(event);
        }

        verify(event, never()).setCancelled(true);
    }

    @AfterEach
    void clearPlugin()
    {
        GriefPrevention.instance = null;
    }

    @Test
    void dispenserInSameClaimMayBreakChorusFlower()
    {
        ProjectileHitEvent event = chorusFlowerHitByDispenser(true);

        new BlockEventHandler(GriefPrevention.instance.dataStore).chorusFlower(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void dispenserOutsideClaimMayNotBreakChorusFlower()
    {
        ProjectileHitEvent event = chorusFlowerHitByDispenser(false);

        new BlockEventHandler(GriefPrevention.instance.dataStore).chorusFlower(event);

        verify(event).setCancelled(true);
    }

    private static ProjectileHitEvent chorusFlowerHitByDispenser(boolean dispenserInClaim)
    {
        GriefPrevention plugin = mock(GriefPrevention.class);
        DataStore dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        GriefPrevention.instance = plugin;

        World world = mock(World.class);
        when(plugin.claimsEnabledForWorld(world)).thenReturn(true);

        Claim claim = mock(Claim.class);
        Location flowerLocation = mock(Location.class);
        Block flower = mock(Block.class);
        when(flower.getType()).thenReturn(Material.CHORUS_FLOWER);
        when(flower.getLocation()).thenReturn(flowerLocation);
        when(dataStore.getClaimAt(flowerLocation, false, null)).thenReturn(claim);

        Location dispenserLocation = mock(Location.class);
        Block dispenserBlock = mock(Block.class);
        when(dispenserBlock.getLocation()).thenReturn(dispenserLocation);
        BlockProjectileSource dispenser = mock(BlockProjectileSource.class);
        when(dispenser.getBlock()).thenReturn(dispenserBlock);
        when(dataStore.getClaimAt(dispenserLocation, false, claim)).thenReturn(dispenserInClaim ? claim : null);

        Arrow arrow = mock(Arrow.class);
        when(arrow.getWorld()).thenReturn(world);
        when(arrow.getShooter()).thenReturn(dispenser);

        ProjectileHitEvent event = mock(ProjectileHitEvent.class);
        when(event.getEntity()).thenReturn(arrow);
        when(event.getHitBlock()).thenReturn(flower);
        return event;
    }
}
