package com.griefprevention.api.claimcommand;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A mode that can be activated with {@code /claim mode <name>}.
 */
public interface ClaimCommandMode
{

    /**
     * Get the mode name.
     *
     * @return the mode name
     */
    @NotNull String getName();

    /**
     * Get optional aliases for this mode.
     *
     * @return the aliases
     */
    default @NotNull List<String> getAliases()
    {
        return List.of();
    }

    /**
     * Activate the mode.
     *
     * @param context the command context
     * @param args any remaining arguments after the mode name
     * @return true if the command was handled
     */
    boolean onCommand(@NotNull ClaimCommandContext context, @NotNull String[] args);

    /**
     * Get completions for any remaining arguments after the mode name.
     *
     * @param context the command context
     * @param args the remaining arguments after the mode name
     * @return matching completions
     */
    default @NotNull List<String> onTabComplete(@NotNull ClaimCommandContext context, @NotNull String[] args)
    {
        return List.of();
    }

}
