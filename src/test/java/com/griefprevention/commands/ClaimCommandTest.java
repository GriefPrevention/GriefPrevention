package com.griefprevention.commands;

import com.griefprevention.test.ServerMocks;
import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.AutoExtendClaimTask;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimCommandTest
{
    private static Server server;
    private GriefPrevention plugin;
    private DataStore dataStore;
    private Player player;
    private PlayerData playerData;
    private World world;
    private ClaimCommand command;
    private MockedStatic<BoundaryVisualization> visualization;
    private MockedStatic<AutoExtendClaimTask> autoExtend;

    @BeforeAll
    static void startServer()
    {
        server = ServerMocks.newServer();
        Bukkit.setServer(server);
    }

    @AfterAll
    static void stopServer()
    {
        ServerMocks.unsetBukkitServer();
    }

    @BeforeEach
    void setUp()
    {
        plugin = mock(GriefPrevention.class, CALLS_REAL_METHODS);
        when(plugin.getServer()).thenReturn(server);
        GriefPrevention.instance = plugin;
        dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        plugin.config_claims_automaticClaimsForNewPlayersRadius = 0;
        plugin.config_claims_worldModes = new ConcurrentHashMap<>();
        world = mock(World.class);
        plugin.config_claims_worldModes.put(world, ClaimsMode.Survival);
        player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 10));
        playerData = mock(PlayerData.class, CALLS_REAL_METHODS);
        doReturn(new Vector<Claim>()).when(playerData).getClaims();
        doReturn(100).when(playerData).getRemainingClaimBlocks();
        when(dataStore.getPlayerData(any())).thenReturn(playerData);
        when(dataStore.getMessage(any(Messages.class), any(String[].class)))
                .thenAnswer(invocation -> ((Messages) invocation.getArgument(0)).name());
        command = mock(ClaimCommand.class, CALLS_REAL_METHODS);
        try
        {
            java.lang.reflect.Field pluginField = CommandHandler.class.getDeclaredField("plugin");
            pluginField.setAccessible(true);
            pluginField.set(command, plugin);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
        visualization = mockStatic(BoundaryVisualization.class);
        autoExtend = mockStatic(AutoExtendClaimTask.class);
    }

    @AfterEach
    void tearDown()
    {
        if (autoExtend != null) autoExtend.close();
        if (visualization != null) visualization.close();
        GriefPrevention.instance = null;
    }

    @Test
    void successfulCreationSendsExpectedMessagesVisualizesClearsStateAndSchedulesOnce()
    {
        Claim claim = mock(Claim.class);
        CreateClaimResult result = new CreateClaimResult();
        result.succeeded = true;
        result.claim = claim;
        when(dataStore.createClaim(any(), any(int.class), any(int.class), any(int.class), any(int.class),
                any(int.class), any(int.class), any(), isNull(), isNull(), any())).thenReturn(result);
        playerData.claimResizing = mock(Claim.class);
        playerData.lastShovelLocation = new Location(world, 1, 2, 3);

        assertTrue(command.onCommand(player, null, "claim", new String[0]));

        assertNull(playerData.claimResizing);
        assertNull(playerData.lastShovelLocation);
        visualization.verify(() -> BoundaryVisualization.visualizeClaim(player, claim, VisualizationType.CLAIM));
        autoExtend.verify(() -> AutoExtendClaimTask.scheduleAsync(claim));
        verify(dataStore).getMessage(eq(Messages.CreateClaimSuccess), any(String[].class));
        verify(dataStore).getMessage(eq(Messages.SurvivalBasicsVideo2), eq(new String[]{DataStore.SURVIVAL_VIDEO_URL}));
        verify(dataStore).createClaim(any(), any(int.class), any(int.class), any(int.class), any(int.class),
                any(int.class), any(int.class), any(), any(), any(), any());
    }

    @Test
    void failedCreationDoesNotClearStateVisualizeCreatedClaimOrSchedule()
    {
        CreateClaimResult result = new CreateClaimResult();
        when(dataStore.createClaim(any(), any(int.class), any(int.class), any(int.class), any(int.class),
                any(int.class), any(int.class), any(), isNull(), isNull(), any())).thenReturn(result);
        Claim resizing = mock(Claim.class);
        Location lastShovelLocation = new Location(world, 1, 2, 3);
        playerData.claimResizing = resizing;
        playerData.lastShovelLocation = lastShovelLocation;

        assertTrue(command.onCommand(player, null, "claim", new String[0]));

        assertSame(resizing, playerData.claimResizing);
        assertSame(lastShovelLocation, playerData.lastShovelLocation);
        visualization.verifyNoInteractions();
        autoExtend.verifyNoInteractions();
    }

    @Test
    void successfulResultWithoutClaimDoesNotRunSuccessEffects()
    {
        CreateClaimResult result = new CreateClaimResult();
        result.succeeded = true;
        when(dataStore.createClaim(any(), any(int.class), any(int.class), any(int.class), any(int.class),
                any(int.class), any(int.class), any(), isNull(), isNull(), any())).thenReturn(result);
        Claim resizing = mock(Claim.class);
        playerData.claimResizing = resizing;

        assertTrue(command.onCommand(player, null, "claim", new String[0]));

        assertSame(resizing, playerData.claimResizing);
        visualization.verifyNoInteractions();
        autoExtend.verifyNoInteractions();
    }
}
