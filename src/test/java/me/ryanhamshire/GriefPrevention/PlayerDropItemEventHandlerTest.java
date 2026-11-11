package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlayerDropItemEventHandlerTest
{
    private static final UUID PLAYER_UUID = UUID.fromString("6a2d3e1b-4c8f-4f2a-9b7e-1c5d8a9e3b21");

    private static GriefPrevention plugin;
    private static DataStore dataStore;
    private static GriefPrevention previousInstance;

    @BeforeAll
    static void beforeAll()
    {
        //PlayerEventHandler's constructor touches Bukkit.getServer() via MonitoredCommands
        Bukkit.setServer(ServerMocks.newServer());

        plugin = mock(GriefPrevention.class);
        plugin.config_pvp_allowCombatItemDrop = false;
        plugin.config_pvp_combatTimeoutSeconds = 15;
        plugin.config_pvp_blockedCommands = new ArrayList<>();
        plugin.config_claims_commandsRequiringAccessTrust = new ArrayList<>();
        plugin.config_spam_monitorSlashCommands = new ArrayList<>();
        plugin.config_eavesdrop_whisperCommands = new ArrayList<>();
        dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        when(dataStore.loadBannedWords()).thenReturn(new ArrayList<>());

        //dummy logger so the MonitoredCommands static block can log without failing
        when(plugin.getLogger()).thenReturn(mock(Logger.class));

        previousInstance = GriefPrevention.instance;
        GriefPrevention.instance = plugin;
    }

    @AfterAll
    static void afterAll()
    {
        GriefPrevention.instance = previousInstance;
        ServerMocks.unsetBukkitServer();
    }

    private static PlayerData combatPlayerData()
    {
        PlayerData playerData = new PlayerData();
        playerData.lastPvpTimestamp = System.currentTimeMillis();
        return playerData;
    }

    private static Player inCombatPlayer()
    {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        when(player.isDead()).thenReturn(false);
        when(dataStore.getPlayerData(PLAYER_UUID)).thenReturn(combatPlayerData());
        return player;
    }

    //during PvP combat, a drop click (Q on a slot, or clicking outside with an item on the
    //cursor) is blocked at the click so the item never reaches a state where cancelling the
    //drop event would silently delete it
    @Test
    void verifyCursorDropClickIsCancelledDuringCombat()
    {
        Player player = inCombatPlayer();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getAction()).thenReturn(InventoryAction.DROP_ONE_CURSOR);
        when(event.getWhoClicked()).thenReturn(player);
        PlayerEventHandler handler = new PlayerEventHandler(dataStore, plugin);

        handler.onInventoryClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void verifySlotDropClickIsCancelledDuringCombat()
    {
        Player player = inCombatPlayer();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getAction()).thenReturn(InventoryAction.DROP_ALL_SLOT);
        when(event.getWhoClicked()).thenReturn(player);
        PlayerEventHandler handler = new PlayerEventHandler(dataStore, plugin);

        handler.onInventoryClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void verifyNonDropClickIsNotCancelledDuringCombat()
    {
        Player player = inCombatPlayer();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ONE);
        when(event.getWhoClicked()).thenReturn(player);
        PlayerEventHandler handler = new PlayerEventHandler(dataStore, plugin);

        handler.onInventoryClick(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void verifyDropClickIsNotCancelledOutsideCombat()
    {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        when(dataStore.getPlayerData(PLAYER_UUID)).thenReturn(new PlayerData());
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getAction()).thenReturn(InventoryAction.DROP_ONE_CURSOR);
        when(event.getWhoClicked()).thenReturn(player);
        PlayerEventHandler handler = new PlayerEventHandler(dataStore, plugin);

        handler.onInventoryClick(event);

        verify(event, never()).setCancelled(true);
    }

    //a cancelled drop is returned to the main inventory, so an item that cannot fit there (a
    //drop from the cursor or an armor or offhand slot with a full main inventory) must not be
    //cancelled, or it would be silently deleted
    @Test
    void verifyUnreturnableDropIsNotCancelled()
    {
        Player player = inCombatPlayer();
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getItem(anyInt())).thenReturn(new ItemStack(Material.DIAMOND, 64));
        when(player.getInventory()).thenReturn(inventory);
        when(plugin.getItemInHand(player, EquipmentSlot.HAND)).thenReturn(new ItemStack(Material.DIAMOND_SWORD));
        Item drop = mock(Item.class);
        when(drop.getItemStack()).thenReturn(new ItemStack(Material.DIAMOND_CHESTPLATE));
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemDrop()).thenReturn(drop);
        PlayerEventHandler handler = new PlayerEventHandler(dataStore, plugin);

        handler.onPlayerDropItem(event);

        verify(event, never()).setCancelled(true);
    }

    //a main-hand drop can always be cancelled: the server splits the stack out of the selected
    //hotbar slot, so cancelling returns it to that now-free slot
    @Test
    void verifyMainHandDropIsCancelled()
    {
        Player player = inCombatPlayer();
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getItem(anyInt())).thenReturn(new ItemStack(Material.DIAMOND, 64));
        when(player.getInventory()).thenReturn(inventory);
        when(plugin.getItemInHand(player, EquipmentSlot.HAND)).thenReturn(new ItemStack(Material.AIR));
        Item drop = mock(Item.class);
        when(drop.getItemStack()).thenReturn(new ItemStack(Material.DIAMOND_SWORD));
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemDrop()).thenReturn(drop);
        PlayerEventHandler handler = new PlayerEventHandler(dataStore, plugin);

        handler.onPlayerDropItem(event);

        verify(event).setCancelled(true);
    }

    //a drop that can be returned to the main inventory is still cancelled as usual
    @Test
    void verifyReturnableDropIsCancelled()
    {
        Player player = inCombatPlayer();
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(plugin.getItemInHand(player, EquipmentSlot.HAND)).thenReturn(new ItemStack(Material.AIR));
        Item drop = mock(Item.class);
        when(drop.getItemStack()).thenReturn(new ItemStack(Material.DIAMOND_SWORD));
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemDrop()).thenReturn(drop);
        PlayerEventHandler handler = new PlayerEventHandler(dataStore, plugin);

        handler.onPlayerDropItem(event);

        verify(event).setCancelled(true);
    }

    //closing an inventory with an item on the cursor drops that item; during combat, put it into
    //the main inventory instead so it cannot be deleted
    @Test
    void verifyCursorItemIsPlacedIntoInventoryOnCloseDuringCombat()
    {
        Player player = inCombatPlayer();
        ItemStack cursor = new ItemStack(Material.DIAMOND, 5);
        when(player.getItemOnCursor()).thenReturn(cursor);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(cursor)).thenReturn(new HashMap<>());
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        PlayerEventHandler handler = new PlayerEventHandler(dataStore, plugin);

        handler.onInventoryClose(event);

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(player).setItemOnCursor(captor.capture());
        assertEquals(Material.AIR, captor.getValue().getType());
    }

    @Test
    void verifyCursorIsNotTouchedOnCloseOutsideCombat()
    {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        when(dataStore.getPlayerData(PLAYER_UUID)).thenReturn(new PlayerData());
        when(player.getItemOnCursor()).thenReturn(new ItemStack(Material.DIAMOND, 5));
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        PlayerEventHandler handler = new PlayerEventHandler(dataStore, plugin);

        handler.onInventoryClose(event);

        verify(player, never()).setItemOnCursor(any());
    }
}
