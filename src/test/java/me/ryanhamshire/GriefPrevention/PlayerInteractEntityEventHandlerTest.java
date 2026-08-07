package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
public class PlayerInteractEntityEventHandlerTest
{
    private static final UUID PLAYER_ID = UUID.fromString("fc70e25f-f8da-4f33-bb9c-ff0db586cd30");

    @BeforeAll
    static void beforeAll()
    {
        Server server = ServerMocks.newServer();
        Bukkit.setServer(server);

        // Force initialization of InventoryType before tests run.
        // In 1.21.10+, InventoryType depends on MenuType which requires registry lookups.
        // Initializing here ensures this happens with proper mocks, not mid-stubbing.
        InventoryType.values();

        // Touch class to load material list.
        //noinspection ResultOfMethodCallIgnored
        PlayerEventHandler.class.getName();
    }

    @AfterAll
    static void afterAll()
    {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void interactingWithUntamedHorseDoesNotRepairOrClearThirdPartyInventory()
    {
        DataStore dataStore = mock(DataStore.class);
        when(dataStore.loadBannedWords()).thenReturn(Collections.emptyList());
        when(dataStore.getPlayerData(PLAYER_ID)).thenReturn(new PlayerData());

        GriefPrevention plugin = mock(GriefPrevention.class);
        plugin.dataStore = dataStore;
        plugin.config_claims_protectHorses = true;
        plugin.config_pvp_blockedCommands = new ArrayList<>();
        plugin.config_claims_commandsRequiringAccessTrust = new ArrayList<>();
        plugin.config_spam_monitorSlashCommands = new ArrayList<>();
        plugin.config_eavesdrop_whisperCommands = new ArrayList<>();
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        GriefPrevention.instance = plugin;

        World world = mock(World.class);
        when(plugin.claimsEnabledForWorld(world)).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(plugin.getItemInHand(player, EquipmentSlot.HAND)).thenReturn(new ItemStack(Material.AIR));

        HorseInventory inventory = mock(HorseInventory.class);
        AbstractHorse horse = mock(AbstractHorse.class);
        when(horse.getWorld()).thenReturn(world);
        when(horse.isTamed()).thenReturn(false);
        when(horse.getInventory()).thenReturn(inventory);

        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getRightClicked()).thenReturn(horse);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);

        new PlayerEventHandler(dataStore, plugin).onPlayerInteractEntity(event);

        verify(inventory, never()).clear();
        verify(horse, never()).setOwner(null);
    }
}