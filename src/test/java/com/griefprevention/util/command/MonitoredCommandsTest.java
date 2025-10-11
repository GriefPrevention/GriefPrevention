package com.griefprevention.util.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.griefprevention.test.ServerMocks;

import me.ryanhamshire.GriefPrevention.GriefPrevention;

class MonitoredCommandsTest
{

    private static Server server;

    private CommandMap commandMap;

    @BeforeEach
    void beforeEach() throws ReflectiveOperationException
    {
        PluginManager pluginManager = mock();
        doReturn(new Plugin[0]).when(pluginManager).getPlugins();
        doReturn(pluginManager).when(server).getPluginManager();
        doReturn(null).when(server).getPluginCommand(anyString());

        commandMap = mock();
        setCommandMap(commandMap);
    }

    @ParameterizedTest
    @MethodSource("commandsAndPrefixes")
    void commandFromMap(@NotNull Class<? extends Command> clazz, @Nullable String prefix)
    {
        Command command = mock(clazz);
        doReturn(command).when(commandMap).getCommand("test");
        doReturn("test").when(command).getLabel();
        doReturn(List.of("tset")).when(command).getAliases();

        MonitoredCommands monitor = new MonitoredCommands(List.of("/test"));

        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/tset")));
        List<String> invalidPrefixes = new ArrayList<>(List.of("/minecraft:", "/bukkit:", "/invalid:"));
        if (prefix != null)
        {
            assertTrue(monitor.isMonitoredCommand(new MonitorableCommand(prefix + "test")));
            assertTrue(monitor.isMonitoredCommand(new MonitorableCommand(prefix + "tset")));
            invalidPrefixes.remove(prefix);
        }

        for (String invalidPrefix : invalidPrefixes)
        {
            assertFalse(monitor.isMonitoredCommand(new MonitorableCommand(invalidPrefix + "test")));
            assertFalse(monitor.isMonitoredCommand(new MonitorableCommand(invalidPrefix + "tset")));
        }
    }

    private static Collection<Arguments> commandsAndPrefixes()
    {
        return List.of(
                Arguments.of(Command.class, null),
                Arguments.of(BukkitCommand.class, "/bukkit:"),
                Arguments.of(MinecraftCommand.class, "/minecraft:"));
    }

    @Test
    void commandFromMapAlternates()
    {
        Command command = mock();
        doReturn(command).when(commandMap).getCommand("prefix:test");
        doReturn("prefix:test").when(command).getLabel();
        doReturn(List.of("tset")).when(command).getAliases();

        Plugin plugin = mock();
        doReturn("PreFix").when(plugin).getName();
        PluginManager pluginManager = server.getPluginManager();
        doReturn(new Plugin[]{plugin}).when(pluginManager).getPlugins();

        MonitoredCommands monitor = new MonitoredCommands(List.of("/test"));

        assertFalse(monitor.isMonitoredCommand(new MonitorableCommand("/test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/prefix:test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/tset")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/prefix:tset")));
    }

    @ParameterizedTest
    @CsvSource({"test,/test one two", "test one,/test one two", "test one two,/test one two"})
    void commandWithArgs(String monitored, String executed)
    {
        Command command = mock();
        doReturn(command).when(commandMap).getCommand("test");
        doReturn("test").when(command).getLabel();

        MonitoredCommands monitor = new MonitoredCommands(List.of(monitored));

        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand(executed)));
    }

    @ParameterizedTest
    @CsvSource({"test one,/test", "test one,/test oops", "test one,/test oh no"})
    void commandWithoutArgs(String monitored, String executed)
    {
        Command command = mock();
        doReturn(command).when(commandMap).getCommand("test");
        doReturn("test").when(command).getLabel();

        MonitoredCommands monitor = new MonitoredCommands(List.of(monitored));

        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand('/' + monitored)));
        assertFalse(monitor.isMonitoredCommand(new MonitorableCommand(executed)));
    }

    @Test
    void commandFromBukkit() throws ReflectiveOperationException
    {
        setCommandMap(null);

        MonitoredCommands monitor = new MonitoredCommands(List.of("/test"));

        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/bukkit:test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/minecraft:test")));
        assertFalse(monitor.isMonitoredCommand(new MonitorableCommand("/tset")));
        assertFalse(monitor.isMonitoredCommand(new MonitorableCommand("/invalid:test")));
    }

    @Test
    void pluginCommandFromBukkit() throws ReflectiveOperationException
    {
        setCommandMap(null);

        Plugin plugin = mock();
        doReturn("TestPlugin").when(plugin).getName();
        Command command = pluginCommand(plugin);

        doReturn(command).when(server).getPluginCommand("test");

        MonitoredCommands monitor = new MonitoredCommands(List.of("/test"));

        List<String> expectedAliases = List
                .of("/test", "/tset", "/testplugin:test", "/testplugin:tset", "/minecraft:test", "/bukkit:test");
        for (String alias : expectedAliases)
        {
            assertTrue(monitor.isMonitoredCommand(new MonitorableCommand(alias)));
        }
        assertFalse(monitor.isMonitoredCommand(new MonitorableCommand("/invalid:test")));
    }

    @Test
    void commandFromBukkitAlternates() throws ReflectiveOperationException
    {
        setCommandMap(null);

        Plugin plugin1 = mock();
        doReturn("AliasString").when(plugin1).getName();
        Map<String, Object> commandData = new HashMap<>();
        commandData.put("aliases", "tset");
        Map<String, Map<String, Object>> commands = new HashMap<>();
        commands.put("test", commandData);
        PluginDescriptionFile pdf = spy(new PluginDescriptionFile("AliasString", "1", "com.example.SampleText"));
        doReturn(commands).when(pdf).getCommands();
        doReturn(pdf).when(plugin1).getDescription();

        Plugin plugin2 = mock();
        doReturn("AliasArray").when(plugin2).getName();
        commandData = new HashMap<>();
        commandData.put("aliases", List.of("cool"));
        commands = new HashMap<>();
        commands.put("test", commandData);
        pdf = spy(new PluginDescriptionFile("AliasArray", "2", "com.example.SampleText"));
        doReturn(commands).when(pdf).getCommands();
        doReturn(pdf).when(plugin2).getDescription();

        Plugin plugin3 = mock();
        doReturn("AliasNull").when(plugin3).getName();
        commands = new HashMap<>();
        commands.put("test", new HashMap<>());
        pdf = spy(new PluginDescriptionFile("AliasNull", "3", "com.example.SampleText"));
        doReturn(commands).when(pdf).getCommands();
        doReturn(pdf).when(plugin3).getDescription();

        Plugin plugin4 = mock();
        doReturn("AliasInvalid").when(plugin4).getName();
        commandData = new HashMap<>();
        commandData.put("aliases", 5);
        commands = new HashMap<>();
        commands.put("test", commandData);
        pdf = spy(new PluginDescriptionFile("AliasInvalid", "4", "com.example.SampleText"));
        doReturn(commands).when(pdf).getCommands();
        doReturn(pdf).when(plugin4).getDescription();

        PluginManager pluginManager = server.getPluginManager();
        doReturn(new Plugin[]{plugin1, plugin2, plugin3, plugin4}).when(pluginManager).getPlugins();

        MonitoredCommands monitor = new MonitoredCommands(List.of("/test"));

        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/bukkit:test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/minecraft:test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/tset")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/aliasstring:test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/aliasstring:tset")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/cool")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/aliasarray:test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/aliasarray:cool")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/aliasnull:test")));
        assertTrue(monitor.isMonitoredCommand(new MonitorableCommand("/aliasinvalid:test")));
    }

    @Test
    void detectConflictedAliases() throws ReflectiveOperationException
    {
        setCommandMap(null);

        Plugin plugin = mock();
        doReturn("TestPlugin").when(plugin).getName();
        Command command = pluginCommand(plugin);
        command.getAliases().clear(); // Simulate alias conflict

        doReturn(command).when(server).getPluginCommand("test");

        MonitoredCommands monitor = new MonitoredCommands(List.of("/test"));

        List<String> expectedAliases = List.of(
                "/test",
                "/testplugin:test",
                "/testplugin:tset", // Prefixed copies of conflicted aliases must be registered.
                "/minecraft:test",
                "/bukkit:test");
        for (String alias : expectedAliases)
        {
            assertTrue(monitor.isMonitoredCommand(new MonitorableCommand(alias)));
        }
        assertFalse(monitor.isMonitoredCommand(new MonitorableCommand("/tset")));
        assertFalse(monitor.isMonitoredCommand(new MonitorableCommand("/invalid:test")));
    }

    private static PluginCommand pluginCommand(Plugin owner) throws ReflectiveOperationException
    {
        Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        PluginCommand command = constructor.newInstance("test", owner);
        command.setAliases(List.of("tset"));
        return command;
    }

    @AfterEach
    void afterEach() throws ReflectiveOperationException
    {
        setCommandMap(null);
    }

    /**
     * Helper used to set command map used by MonitoredCommands rather than create a server implementation.
     *
     * @param commandMap the command map instance
     * @exception ReflectiveOperationException if setting fails
     */
    private static void setCommandMap(@Nullable CommandMap commandMap) throws ReflectiveOperationException
    {
        Field mapField = MonitoredCommands.class.getDeclaredField("commandMap");
        mapField.setAccessible(true);
        mapField.set(null, commandMap);
    }

    @BeforeAll
    static void beforeAll()
    {
        server = ServerMocks.newServer();
        Bukkit.setServer(server);

        // Set up dummy GP instance with dummy logger to prevent NPE when MonitoredCommands class is loaded.
        GriefPrevention.instance = mock(GriefPrevention.class);
        Logger logger = mock(Logger.class);
        doReturn(logger).when(GriefPrevention.instance).getLogger();
    }

    @AfterAll
    static void afterAll()
    {
        //noinspection DataFlowIssue
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    private static abstract class MinecraftCommand extends BukkitCommand
    {

        // Mockito's mocks are constructed with a package matching that of the mocked object.
        // This means that direct BukkitCommand mocking will always result in the bukkit prefix.
        protected MinecraftCommand(@NotNull String name)
        {
            super(name);
        }
    }

}
