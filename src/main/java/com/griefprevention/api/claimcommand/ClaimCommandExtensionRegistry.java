package com.griefprevention.api.claimcommand;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry for {@code /claim} command extensions.
 */
public final class ClaimCommandExtensionRegistry
{
    private static final String MODE_KEYWORD = "mode";

    private final Map<String, ClaimCommandSubcommand> subcommands = new LinkedHashMap<>();
    private final Map<String, String> subcommandAliases = new LinkedHashMap<>();
    private final Map<String, ClaimCommandMode> modes = new LinkedHashMap<>();
    private final Map<String, String> modeAliases = new LinkedHashMap<>();

    public void registerSubcommand(@NotNull ClaimCommandSubcommand subcommand)
    {
        String key = normalize(subcommand.getName());
        if (MODE_KEYWORD.equals(key))
        {
            throw new IllegalArgumentException("\"mode\" is reserved by /claim mode.");
        }

        subcommandAliases.entrySet().removeIf(entry -> key.equals(entry.getValue()));
        subcommands.put(key, subcommand);
        subcommandAliases.put(key, key);
        for (String alias : subcommand.getAliases())
        {
            String normalizedAlias = normalize(alias);
            if (MODE_KEYWORD.equals(normalizedAlias))
            {
                throw new IllegalArgumentException("\"mode\" is reserved by /claim mode.");
            }

            subcommandAliases.put(normalizedAlias, key);
        }
    }

    public void unregisterSubcommand(@NotNull String name)
    {
        String canonical = subcommandAliases.remove(normalize(name));
        if (canonical == null)
        {
            canonical = normalize(name);
        }

        ClaimCommandSubcommand removed = subcommands.remove(canonical);
        if (removed == null)
        {
            return;
        }

        subcommandAliases.values().removeIf(canonical::equals);
    }

    public @Nullable ClaimCommandSubcommand getSubcommand(@NotNull String name)
    {
        String canonical = subcommandAliases.get(normalize(name));
        return canonical == null ? null : subcommands.get(canonical);
    }

    public @NotNull List<ClaimCommandSubcommand> getSubcommands()
    {
        return new ArrayList<>(subcommands.values());
    }

    public void registerMode(@NotNull ClaimCommandMode mode)
    {
        String key = normalize(mode.getName());
        modeAliases.entrySet().removeIf(entry -> key.equals(entry.getValue()));
        modes.put(key, mode);
        modeAliases.put(key, key);
        for (String alias : mode.getAliases())
        {
            modeAliases.put(normalize(alias), key);
        }
    }

    public void unregisterMode(@NotNull String name)
    {
        String canonical = modeAliases.remove(normalize(name));
        if (canonical == null)
        {
            canonical = normalize(name);
        }

        ClaimCommandMode removed = modes.remove(canonical);
        if (removed == null)
        {
            return;
        }

        modeAliases.values().removeIf(canonical::equals);
    }

    public @Nullable ClaimCommandMode getMode(@NotNull String name)
    {
        String canonical = modeAliases.get(normalize(name));
        return canonical == null ? null : modes.get(canonical);
    }

    public @NotNull List<ClaimCommandMode> getModes()
    {
        return new ArrayList<>(modes.values());
    }

    private static @NotNull String normalize(@NotNull String name)
    {
        return name.toLowerCase(Locale.ROOT);
    }

}
