package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
public class PlayerPreLoginEventHandlerTest
{
    private static final UUID PLAYER_ID = UUID.fromString("fc70e25f-f8da-4f33-bb9c-ff0db586cd30");
    private static final String PLAYER_NAME = "Offline1";
    private static final InetAddress ADDRESS = InetAddress.getLoopbackAddress();

    private static Server server;
    private GriefPrevention plugin;
    private BukkitScheduler scheduler;
    private BanList<PlayerProfile> banList;
    private PlayerProfile profile;
    private PlayerEventHandler handler;

    @BeforeAll
    static void beforeAll()
    {
        server = ServerMocks.newServer();
        Bukkit.setServer(server);
    }

    @AfterAll
    static void afterAll()
    {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach()
    {
        DataStore dataStore = mock(DataStore.class);
        when(dataStore.loadBannedWords()).thenReturn(Collections.emptyList());

        plugin = mock(GriefPrevention.class);
        plugin.dataStore = dataStore;
        plugin.config_spam_enabled = true;
        plugin.config_spam_loginCooldownSeconds = 60;
        plugin.config_smartBan = true;
        plugin.config_pvp_blockedCommands = new ArrayList<>();
        plugin.config_claims_commandsRequiringAccessTrust = new ArrayList<>();
        plugin.config_spam_monitorSlashCommands = new ArrayList<>();
        plugin.config_eavesdrop_whisperCommands = new ArrayList<>();
        when(plugin.getServer()).thenReturn(server);
        // MonitoredCommands' static initializer logs when the mocked server has no command map.
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        GriefPrevention.instance = plugin;

        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);

        profile = mock(PlayerProfile.class);
        when(server.createPlayerProfile(PLAYER_ID, PLAYER_NAME)).thenReturn(profile);
        banList = mock(BanList.class);
        when(server.getBanList(BanList.Type.PROFILE)).thenReturn(banList);

        handler = new PlayerEventHandler(dataStore, plugin);
    }

    private AsyncPlayerPreLoginEvent login()
    {
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(PLAYER_NAME, ADDRESS, PLAYER_ID);
        handler.onPlayerPreLogin(event);
        return event;
    }

    private void loggedInSecondsAgo(long seconds)
    {
        handler.lastLoginThisServerSessionMap.put(PLAYER_ID, new Date(System.currentTimeMillis() - seconds * 1000));
    }

    @Test
    void firstLoginThisSessionIsAllowed()
    {
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, login().getLoginResult());
    }

    @Test
    void reloginWithinCooldownIsRejectedWithRemainingSeconds()
    {
        loggedInSecondsAgo(10);

        AsyncPlayerPreLoginEvent event = login();

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
        assertTrue(event.getKickMessage().matches("You must wait (49|50) seconds before logging-in again\\."), event.getKickMessage());
    }

    @Test
    void reloginAfterCooldownIsAllowed()
    {
        loggedInSecondsAgo(61);

        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, login().getLoginResult());
    }

    @Test
    void cooldownIgnoredWhenSpamDisabled()
    {
        plugin.config_spam_enabled = false;
        loggedInSecondsAgo(1);

        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, login().getLoginResult());
    }

    @Test
    void cooldownIgnoredWhenZero()
    {
        plugin.config_spam_loginCooldownSeconds = 0;
        loggedInSecondsAgo(1);

        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, login().getLoginResult());
    }

    private Runnable scheduledSmartBanCheck()
    {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        return task.getValue();
    }

    @Test
    void bannedLoginRecordsAddressOnMainThread()
    {
        when(banList.isBanned(profile)).thenReturn(true);

        login();

        Runnable check = scheduledSmartBanCheck();
        assertTrue(handler.tempBannedIps.isEmpty());
        check.run();
        assertEquals(1, handler.tempBannedIps.size());
        IpBanInfo info = handler.tempBannedIps.get(0);
        assertSame(ADDRESS, info.address);
        assertEquals(PLAYER_NAME, info.bannedAccountName);
    }

    @Test
    void bannedLoginWithinCooldownStillRecordsAddress()
    {
        when(banList.isBanned(profile)).thenReturn(true);
        loggedInSecondsAgo(1);

        AsyncPlayerPreLoginEvent event = login();

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
        scheduledSmartBanCheck().run();
        assertEquals(1, handler.tempBannedIps.size());
    }

    @Test
    void loginKickedAsBannedByOtherPluginRecordsAddress()
    {
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(PLAYER_NAME, ADDRESS, PLAYER_ID);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "banned elsewhere");

        handler.onPlayerPreLogin(event);

        scheduledSmartBanCheck().run();
        assertEquals(1, handler.tempBannedIps.size());
    }

    @Test
    void unbannedLoginRecordsNothing()
    {
        login();

        scheduledSmartBanCheck().run();
        assertTrue(handler.tempBannedIps.isEmpty());
    }

    @Test
    void smartBanIgnoredWhenDisabled()
    {
        plugin.config_smartBan = false;
        when(banList.isBanned(profile)).thenReturn(true);

        login();

        verify(scheduler, never()).runTask(any(), any(Runnable.class));
    }
}
