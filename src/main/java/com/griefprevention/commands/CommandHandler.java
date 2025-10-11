package com.griefprevention.commands;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.GriefPrevention;

public abstract class CommandHandler implements TabExecutor
{

    protected final GriefPrevention plugin;

    CommandHandler(@NotNull GriefPrevention plugin, String command)
    {
        this.plugin = plugin;
        setExecutor(command);
    }

    CommandHandler(@NotNull GriefPrevention plugin, @NotNull String @NotNull... commands)
    {
        if (commands.length == 0)
        { throw new IllegalArgumentException("Must provide commands!"); }
        this.plugin = plugin;
        for (String command : commands)
        {
            setExecutor(command);
        }
    }

    private void setExecutor(@NotNull String commandName)
    {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null)
        { throw new IllegalStateException("Command not registered: " + commandName); }
        command.setExecutor(this);
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args)
    {
        // Tab complete visible online players by default.
        return TabCompletions.visiblePlayers(sender, args);
    }

}
