package com.griefprevention.util.command;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

public class MonitorableCommand
{

    private final String command;

    private final List<Integer> spaces = new ArrayList<>();

    public MonitorableCommand(@NotNull String command)
    {
        this.command = command.toLowerCase();
        for (int lastSpace = command.indexOf(' '); lastSpace != -1; lastSpace = command.indexOf(' ', lastSpace + 1))
        {
            spaces.add(lastSpace);
        }
    }

    public @Range(from = 0, to = Integer.MAX_VALUE) int getArgumentCount()
    {
        return spaces.size();
    }

    public @NotNull String getCommand()
    {
        return command;
    }

    public @NotNull String getCommand(int arguments)
    {
        if (arguments == spaces.size()) return command;
        return command.substring(0, this.spaces.get(arguments));
    }

    public @NotNull String getArgument(int index)
    {
        int start = spaces.get(index) + 1;
        int end = index + 1 == spaces.size() ? command.length() : spaces.get(index + 1);
        return command.substring(start, end);
    }

}
