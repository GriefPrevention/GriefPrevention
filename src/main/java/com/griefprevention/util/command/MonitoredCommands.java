package com.griefprevention.util.command;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.FormattedCommandAlias;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.plugin.Plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.GriefPrevention;

public class MonitoredCommands
{

    private static CommandMap commandMap;

    static
    {
        try
        {
            Server server = Bukkit.getServer();
            Field cmdMapField = server.getClass().getDeclaredField("commandMap");
            cmdMapField.setAccessible(true);
            Object obj = cmdMapField.get(server);
            if (obj instanceof CommandMap)
            {
                commandMap = (CommandMap) obj;
            }
        }
        catch (ReflectiveOperationException e)
        {
            GriefPrevention.instance.getLogger().warning("""
                    Caught exception trying to access server command map!
                    Aliases can only be detected for plugin commands declared in relevant plugin.yml files!
                    """);
            GriefPrevention.instance.getLogger().log(Level.WARNING, e.getMessage(), e);
        }
    }

    private final Set<String> monitoredCommands = new HashSet<>();

    private int maxSpaces = -1;

    public MonitoredCommands(@NotNull Collection<String> commands)
    {
        for (String command : commands)
        {
            addMonitored(command);
        }
    }

    public boolean isMonitoredCommand(@NotNull MonitorableCommand command)
    {
        int max = Math.min(maxSpaces, command.getArgumentCount());
        for (int spaceCount = 0; spaceCount <= max; ++spaceCount)
        {
            if (monitoredCommands.contains(command.getCommand(spaceCount))) return true;
        }

        return false;
    }

    private void addMonitored(@NotNull String command)
    {
        command = command.toLowerCase().trim();

        if (command.isEmpty()) return;

        boolean slashStart = command.charAt(0) == '/';
        int firstSpace = command.indexOf(' ');
        String commandName;
        if (firstSpace > -1) commandName = command.substring(slashStart ? 1 : 0, firstSpace);
        else
            commandName = slashStart ? command.substring(1) : command;

        // If a specific subcommand or parameters are blocked, keep that as a separate suffix.
        String suffix = firstSpace > -1 ? command.substring(firstSpace) : "";

        // Try to add from command map if available - will yield more accurate results faster.
        if (commandMap != null)
        {
            addFromCommandMap(commandName, suffix);
            return;
        }

        // If not available, try to add using API-only method.
        // This will fail for plugins like WorldEdit who register their own commands.
        addFromBukkit(commandName, suffix);
    }

    private void addFromCommandMap(@NotNull String commandName, @NotNull String suffix)
    {
        Command command = commandMap.getCommand(commandName);
        Plugin activePlugin = command instanceof PluginIdentifiableCommand pluginCmd ? pluginCmd.getPlugin() : null;

        // Command may also be null if an invalid/empty commands.yml override exists.
        // As a result, there may still be relevant aliases.
        boolean present = command != null;

        if (present) addCommand(command, suffix, activePlugin);

        // If the command is a specific alias, that command is the one being targeted, not others.
        if (commandName.indexOf(':') != -1)
        {
            // Only update max spaces if this is a real command.
            if (present) maxSpaces = Math.max(maxSpaces, (int) suffix.chars().filter(ch -> ch == ' ').count());
            return;
        }

        // Also check plugins for copies of the commands.
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
        {
            if (activePlugin == plugin) continue;

            Command pluginCommand = commandMap.getCommand(plugin.getName().toLowerCase() + ':' + commandName);
            if (pluginCommand != null)
            {
                addCommand(pluginCommand, suffix, plugin);
                present = true;
            }
        }

        if (present) maxSpaces = Math.max(maxSpaces, (int) suffix.chars().filter(ch -> ch == ' ').count());
    }

    private void addCommand(@NotNull Command command, @NotNull String suffix, @Nullable Plugin plugin)
    {
        // Label is always the primary means of access for a command. It is either command_name
        // or prefix:command_name in the event of a conflict with another non-alias command.
        String commandName = command.getLabel().toLowerCase();
        monitoredCommands.add('/' + commandName + suffix);

        String prefix = getCommandPrefix(command, plugin);

        if (prefix != null && commandName.indexOf(':') == -1) monitoredCommands.add(prefix + commandName + suffix);

        command.getAliases().forEach(alias -> monitoredCommands.add('/' + alias.toLowerCase() + suffix));

        if (prefix == null) return;

        /*
         * Commands are registered with a fallback prefix.
         * A prefixed version is always forcibly registered immediately and is not tracked by the command.
         * In the event of a conflict, aliases are not registered and are removed from activeAliases, the
         * list returned by Command#getAliases. However, the prefixed copy of the alias IS registered.
         * Conflicting aliases that were prefixed and are active with that prefix can only be detected by
         * obtaining the original list.
         * There are a couple options for obtaining the original alias list.
         * For standard API plugins, the alias list can be obtained from the PluginDescriptionFile.
         * For other commands, this does not work. To obtain the prefixed aliases (and not leave behind a mess):
         * 1) The field can be obtained directly with reflection. This is "safer" than getting the command map
         *   (which is also largely held to be safe) because it is part of the API, which makes it a lot easier
         *   to check between updates.
         * 2) A copy of the field can be obtained by obtaining a copy of the current active aliases, telling the
         *   command it is unregistered using the command map, telling the command it has been re-registered,
         *   copying the new active aliases (which is a copy of the original aliases after unregistering) and
         *   removing any entries that were removed from the original active aliases during real registration.
         */

        try
        {
            Field aliasesField = Command.class.getDeclaredField("aliases");
            aliasesField.setAccessible(true);
            Object object = aliasesField.get(command);

            if (object instanceof List<?> list)
            {
                list.stream().map(Object::toString).map(String::toLowerCase)
                        .forEach(alias -> monitoredCommands.add(prefix + alias + suffix));
                return;
            }
        }
        catch (ReflectiveOperationException ignored)
        {
            // Can really only happen if someone is doing something very weird or API has changed.
            // If API has changed, IDE should warn that field doesn't exist.
        }
        // Fall back to potentially missing prefixed conflicting aliases.
        command.getAliases().forEach(alias -> monitoredCommands.add(prefix + alias.toLowerCase() + suffix));
    }

    private @Nullable String getCommandPrefix(@NotNull Command command, @Nullable Plugin plugin)
    {
        // Plugin command.
        if (plugin != null) return '/' + plugin.getName().toLowerCase() + ':';

        if (command instanceof BukkitCommand)
        {
            // If this is a command from Bukkit, it is in the same package.
            if (BukkitCommand.class.getPackage().equals(command.getClass().getPackage())) return "/bukkit:";
            // Otherwise this is probably a wrapper for a vanilla command.
            else
                return "/minecraft:";
        }

        // User-created commands.yml commands don't ever have a prefix, they're added directly to the map.
        if (command instanceof FormattedCommandAlias) return null;

        // There's no way for us to detect potential prefixes of commands that don't identify themselves.
        // The only other way to tell is if the command happened to conflict and had its label reassigned.
        int labelSeparator = command.getLabel().indexOf(':');
        if (labelSeparator == -1) return null;

        return command.getLabel().substring(labelSeparator + 1).toLowerCase();
    }

    private void addFromBukkit(@NotNull String commandName, @NotNull String suffix)
    {
        maxSpaces = Math.max(maxSpaces, (int) suffix.chars().filter(ch -> ch == ' ').count());
        monitoredCommands.add('/' + commandName + suffix);

        boolean specificAlias = commandName.indexOf(':') != -1;

        if (!specificAlias)
        {
            // Can only detect plugin commands. For safety, assume that all commands exist in vanilla or Bukkit.
            monitoredCommands.add("/minecraft:" + commandName + suffix);
            monitoredCommands.add("/bukkit:" + commandName + suffix);
        }

        // Get active version of this command by a plugin.
        PluginCommand pluginCommand = Bukkit.getPluginCommand(commandName);
        Plugin activePlugin;

        // If plugin command is present, add active version of command and aliases.
        if (pluginCommand != null)
        {
            activePlugin = pluginCommand.getPlugin();
            addCommand(pluginCommand, suffix, activePlugin);
        }
        // Otherwise, make a best-effort attempt to support aliases of
        // commands that tried to register this command and got overridden.
        else
            activePlugin = null;

        // If the command was identified by a specific alias, no other matches to find.
        if (specificAlias) return;

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
        {
            // Same plugin, command and aliases already added.
            if (activePlugin == plugin) continue;

            for (Map.Entry<String, Map<String, Object>> commandEntry : plugin.getDescription().getCommands().entrySet())
            {
                Collection<String> aliases = getAliases(commandEntry);
                if (!aliases.contains(commandName)) continue;

                String pluginPrefix = '/' + plugin.getName().toLowerCase() + ':';
                for (String alias : aliases)
                {
                    monitoredCommands.add('/' + alias + suffix);
                    monitoredCommands.add(pluginPrefix + alias + suffix);
                }
            }
        }
    }

    private @NotNull Collection<String> getAliases(@NotNull Map.Entry<String, Map<String, Object>> commandEntry)
    {
        Object aliases = commandEntry.getValue().get("aliases");

        return switch (aliases)
        {
            // No aliases.
            case null -> Set.of(commandEntry.getKey().toLowerCase());

            // One alias in String form.
            case String alias -> Set.of(commandEntry.getKey().toLowerCase(), alias.toLowerCase());

            // Zero or more aliases in List form.
            case List<?> list -> Stream.concat(
                    Stream.of(commandEntry.getKey().toLowerCase()),
                    list.stream().map(Object::toString).map(String::toLowerCase)).collect(Collectors.toSet());

            // Invalid alias declaration.
            default -> Set.of(commandEntry.getKey().toLowerCase());
        };

    }

}
