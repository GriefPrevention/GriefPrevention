package com.griefprevention.test;

import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;

import org.jetbrains.annotations.NotNull;

public final class ServerMocks
{

    public static @NotNull Server newServer()
    {
        Server mock = mock(Server.class);

        Logger noOp = mock(Logger.class);
        when(mock.getLogger()).thenReturn(noOp);
        when(mock.isPrimaryThread()).thenReturn(true);

        // One of the worst examples of Bukkit's static mess. Enchantments, biomes, other faux enums are all registry-based.
        // All the constants are loaded from the registry when the class is first accessed.
        // Additionally, all the registries are loaded from the server when the registry class is accessed.
        // That means that the registry class must be set up to be mocked before accessing it or any dependent class.
        // If that is not done, all the constants in the dependent class will be null.
        doAnswer(invocationGetRegistry -> {
            Registry<?> registry = mock();
            doAnswer(invocationGetEntry -> {
                NamespacedKey key = invocationGetEntry.getArgument(0);
                // Set registries to always return a new value. This allows static constants to be populated,
                // i.e. won't need reflection hackery to re-set a final value if Biome.PLAINS is accessed by a test.
                Class<? extends Keyed> arg = invocationGetRegistry.getArgument(0);
                Keyed keyed = mock(arg);
                doReturn(key).when(keyed).getKey();
                return keyed;
            }).when(registry).get(notNull());
            return registry;
        }).when(mock).getRegistry(notNull());

        return mock;
    }

    public static void unsetBukkitServer()
    {
        try
        {
            Field server = Bukkit.class.getDeclaredField("server");
            server.setAccessible(true);
            server.set(null, null);
        }
        catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
    }

    private ServerMocks() { }

}
