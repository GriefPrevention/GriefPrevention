package com.griefprevention.api.claimcommand;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A custom subcommand available through {@code /claim <subcommand> ...}.
 */
public interface ClaimCommandSubcommand
{

    /**
     * Get the primary subcommand name.
     *
     * @return the subcommand name
     */
    @NotNull String getName();

    /**
     * Get optional aliases for this subcommand.
     *
     * @return the aliases
     */
    default @NotNull List<String> getAliases()
    {
        return List.of();
    }

    /**
     * Execute the subcommand.
     *
     * @param context the command context
     * @param args any remaining arguments after the subcommand name
     * @return true if the command was handled
     */
    boolean onCommand(@NotNull ClaimCommandContext context, @NotNull String[] args);

    /**
     * Get completions for any remaining arguments after the subcommand name.
     *
     * @param context the command context
     * @param args the remaining arguments after the subcommand name
     * @return matching completions
     */
    default @NotNull List<String> onTabComplete(@NotNull ClaimCommandContext context, @NotNull String[] args)
    {
        return List.of();
    }

}
