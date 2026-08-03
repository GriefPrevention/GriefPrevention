package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class PlayerInteractEntityEventHandlerTest
{
    @BeforeEach
    void beforeEach()
    {
        Server server = ServerMocks.newServer();
        Bukkit.setServer(server);
    }

    @AfterEach
    void afterEach()
    {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void interactingWithUntamedHorseDoesNotRepairOrClearThirdPartyInventory()
    {
        UUID playerId = UUID.fromString("fc70e25f-f8da-4f33-bb9c-ff0db586cd30");
        DataStore dataStore = mock(DataStore.class);
        when(dataStore.loadBannedWords()).thenReturn(Collections.emptyList());
        when(dataStore.getPlayerData(playerId)).thenReturn(new PlayerData());

        GriefPrevention plugin = mock(GriefPrevention.class);
        plugin.dataStore = dataStore;
        plugin.config_claims_protectHorses = true;
        plugin.config_pvp_blockedCommands = new ArrayList<>();
        plugin.config_claims_commandsRequiringAccessTrust = new ArrayList<>();
        plugin.config_spam_monitorSlashCommands = new ArrayList<>();
        plugin.config_eavesdrop_whisperCommands = new ArrayList<>();
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        GriefPrevention.instance = plugin;

        World world = mock(World.class);
        when(plugin.claimsEnabledForWorld(world)).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
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
