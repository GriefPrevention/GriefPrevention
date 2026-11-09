package me.ryanhamshire.GriefPrevention;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.mockito.Mockito.*;

class WelcomeTaskTest
{
    private static final UUID PLAYER_ID =
            UUID.fromString("fc70e25f-f8da-4f33-bb9c-ff0db586cd30");

    @AfterEach
    void tearDown()
    {
        GriefPrevention.instance = null;
    }

    @Test
    void shouldStopWhenPlayerIsOffline()
    {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);

        WelcomeTask task = new WelcomeTask(player);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<GriefPrevention> griefPrevention =
                     mockStatic(GriefPrevention.class))
        {
            bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID))
                    .thenReturn(null);

            task.run();

            bukkit.verify(() -> Bukkit.getPlayer(PLAYER_ID));
            griefPrevention.verifyNoInteractions();
        }
    }

    @Test
    void shouldContinueWhenPlayerIsOnline()
    {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.isOnline()).thenReturn(true);

        GriefPrevention plugin = mock(GriefPrevention.class);
        plugin.config_claims_supplyPlayerManual = false;
        GriefPrevention.instance = plugin;

        WelcomeTask task = new WelcomeTask(player);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<GriefPrevention> griefPrevention =
                     mockStatic(GriefPrevention.class))
        {
            bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID))
                    .thenReturn(player);

            task.run();

            bukkit.verify(() -> Bukkit.getPlayer(PLAYER_ID));
            verify(player).isOnline();

            griefPrevention.verify(() ->
                    GriefPrevention.sendMessage(
                            player,
                            TextMode.Instr,
                            Messages.AvoidGriefClaimLand
                    )
            );

            griefPrevention.verify(() ->
                    GriefPrevention.sendMessage(
                            player,
                            TextMode.Instr,
                            Messages.SurvivalBasicsVideo2,
                            DataStore.SURVIVAL_VIDEO_URL
                    )
            );
        }
    }

    @Test
    void shouldUseCurrentPlayerAfterReconnect()
    {
        Player oldPlayer = mock(Player.class);
        when(oldPlayer.getUniqueId()).thenReturn(PLAYER_ID);

        Player currentPlayer = mock(Player.class);
        when(currentPlayer.isOnline()).thenReturn(true);

        GriefPrevention plugin = mock(GriefPrevention.class);
        plugin.config_claims_supplyPlayerManual = false;
        GriefPrevention.instance = plugin;

        WelcomeTask task = new WelcomeTask(oldPlayer);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<GriefPrevention> griefPrevention =
                     mockStatic(GriefPrevention.class))
        {
            bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID))
                    .thenReturn(currentPlayer);

            task.run();

            verify(currentPlayer).isOnline();
            verify(oldPlayer, never()).isOnline();

            griefPrevention.verify(() ->
                    GriefPrevention.sendMessage(
                            currentPlayer,
                            TextMode.Instr,
                            Messages.AvoidGriefClaimLand
                    )
            );
        }
    }


    @Test
    void shouldStopWhenPlayerIsNotOnline()
    {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);

        WelcomeTask task = new WelcomeTask(player);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<GriefPrevention> griefPrevention =
                     mockStatic(GriefPrevention.class))
        {
            bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID))
                    .thenReturn(player);

            when(player.isOnline()).thenReturn(false);

            task.run();

            verify(player).isOnline();
            griefPrevention.verifyNoInteractions();
        }
    }
}
