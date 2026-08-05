package com.griefprevention.api.claimcommand;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Context for a {@code /claim} command execution.
 */
public final class ClaimCommandContext
{

    private final @NotNull GriefPrevention plugin;
    private final @NotNull Player player;
    private final @NotNull PlayerData playerData;
    private final @NotNull Command command;
    private final @NotNull String label;

    public ClaimCommandContext(
            @NotNull GriefPrevention plugin,
            @NotNull Player player,
            @NotNull PlayerData playerData,
            @NotNull Command command,
            @NotNull String label)
    {
        this.plugin = plugin;
        this.player = player;
        this.playerData = playerData;
        this.command = command;
        this.label = label;
    }

    public @NotNull GriefPrevention getPlugin()
    {
        return plugin;
    }

    public @NotNull Player getPlayer()
    {
        return player;
    }

    public @NotNull PlayerData getPlayerData()
    {
        return playerData;
    }

    public @NotNull Command getCommand()
    {
        return command;
    }

    public @NotNull String getLabel()
    {
        return label;
    }

}
