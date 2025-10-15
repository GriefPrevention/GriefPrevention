package com.griefprevention.util.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MonitorableCommandTest
{

    @Test
    void commandIsLowercase()
    {
        String input = "/CAPS LOCK ENGAGED";
        MonitorableCommand monitorableCommand = new MonitorableCommand(input);
        assertEquals("/caps lock engaged", monitorableCommand.getCommand(), "Command must be lower case.");
    }

    @ParameterizedTest
    @MethodSource("getArgumentData")
    void getArgument(String command, List<String> arguments)
    {
        MonitorableCommand monitorable = new MonitorableCommand(command);
        assertEquals(arguments.size(), monitorable.getArgumentCount(), "Arguments must be counted correctly");
        for (int i = 0; i < monitorable.getArgumentCount(); ++i)
        {
            assertEquals(arguments.get(i), monitorable.getArgument(i), "Proper starting subsequence must be returned");
        }
    }

    private static List<Arguments> getArgumentData()
    {
        return List.of(
                Arguments.of("/cool command execution yeah", List.of("command", "execution", "yeah")),
                Arguments.of("/single", List.of()));
    }

    @Test
    void getArgumentBad()
    {
        MonitorableCommand command = new MonitorableCommand("/test single");
        assertThrows(IndexOutOfBoundsException.class, () -> command.getArgument(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> command.getArgument(2));
    }

    @ParameterizedTest
    @MethodSource("getCommandData")
    void getCommand(String command, List<String> subcommand)
    {
        MonitorableCommand monitorable = new MonitorableCommand(command);
        assertEquals(command, monitorable.getCommand(), "Full command must be returned");
        for (int i = 0; i <= monitorable.getArgumentCount(); ++i)
        {
            assertEquals(subcommand.get(i), monitorable.getCommand(i), "Proper starting subsequence must be returned");
        }
    }

    private static List<Arguments> getCommandData()
    {
        return List.of(
                Arguments.of(
                        "/cool command execution yeah",
                        List.of("/cool", "/cool command", "/cool command execution", "/cool command execution yeah")),
                Arguments.of("/single", List.of("/single")));
    }

    @Test
    void getCommandBad()
    {
        MonitorableCommand command = new MonitorableCommand("/test single");
        assertThrows(IndexOutOfBoundsException.class, () -> command.getCommand(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> command.getCommand(2));
    }

}
