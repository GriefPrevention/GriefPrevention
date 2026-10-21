package com.griefprevention.commands;

import com.griefprevention.api.claimcommand.ClaimCommandContext;
import com.griefprevention.api.claimcommand.ClaimCommandExtensionRegistry;
import com.griefprevention.api.claimcommand.ClaimCommandMode;
import com.griefprevention.api.claimcommand.ClaimCommandSubcommand;
import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.AutoExtendClaimTask;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.ShovelMode;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ClaimCommand extends CommandHandler
{
    public ClaimCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin, "claim");
        registerBuiltInModes();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args)
    {
        if (!(sender instanceof Player player))
            return false;

        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
        ClaimCommandContext context = new ClaimCommandContext(plugin, player, playerData, command, label);
        if (dispatchExtension(context, args))
        {
            return true;
        }

        World world = player.getWorld();
        if (!plugin.claimsEnabledForWorld(world))
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimsDisabledWorld);
            return true;
        }

        //if he's at the claim count per player limit already and doesn't have permission to bypass, display an error message
        if (plugin.config_claims_maxClaimsPerPlayer > 0 &&
                !player.hasPermission("griefprevention.overrideclaimcountlimit") &&
                playerData.getClaims().size() >= plugin.config_claims_maxClaimsPerPlayer)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimCreationFailedOverClaimCountLimit);
            return true;
        }

        int radius;

        //allow for specifying the radius
        if (args.length > 0)
        {
            if (needsShovel(playerData, player))
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.RadiusRequiresGoldenShovel);
                return true;
            }

            try
            {
                radius = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException e)
            {
                return false;
            }

            int minRadius = getClaimMinRadius();
            if (radius < minRadius)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.MinimumRadius, String.valueOf(minRadius));
                return true;
            }
        }

        // If the player has no claims, allow them to create their starter claim via command instead of chest placement.
        else if (playerData.getClaims().isEmpty() && plugin.config_claims_automaticClaimsForNewPlayersRadius >= 0)
        {
            radius = plugin.config_claims_automaticClaimsForNewPlayersRadius;
        }

        //if player has any claims, respect claim minimum size setting
        else
        {
            //if player has exactly one land claim, this requires the claim modification tool to be in hand (or creative mode player)
            if (needsShovel(playerData, player))
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.MustHoldModificationToolForThat);
                return true;
            }

            radius = getClaimMinRadius();
        }

        if (radius < 0) radius = 0;

        Location playerLoc = player.getLocation();
        int lesserX;
        int lesserZ;
        int greaterX;
        int greaterZ;
        try
        {
            lesserX = Math.subtractExact(playerLoc.getBlockX(), radius);
            lesserZ = Math.subtractExact(playerLoc.getBlockZ(), radius);
            greaterX = Math.addExact(playerLoc.getBlockX(), radius);
            greaterZ = Math.addExact(playerLoc.getBlockZ(), radius);
        }
        catch (ArithmeticException e)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(Integer.MAX_VALUE));
            return true;
        }

        Location lesser = new Location(world, lesserX, playerLoc.getY(), lesserZ);
        Location greater = new Location(world, greaterX, world.getMaxHeight(), greaterZ);

        UUID ownerId;
        if (playerData.shovelMode == ShovelMode.Admin)
        {
            ownerId = null;
        } else
        {
            //player must have sufficient unused claim blocks
            int area;
            try
            {
                int dX = Math.addExact(Math.subtractExact(greater.getBlockX(), lesser.getBlockX()), 1);
                int dZ = Math.addExact(Math.subtractExact(greater.getBlockZ(), lesser.getBlockZ()), 1);
                area = Math.abs(Math.multiplyExact(dX, dZ));
            }
            catch (ArithmeticException e)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(Integer.MAX_VALUE));
                return true;
            }
            int remaining = playerData.getRemainingClaimBlocks();
            if (remaining < area)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(area - remaining));
                plugin.dataStore.tryAdvertiseAdminAlternatives(player);
                return true;
            }
            ownerId = player.getUniqueId();
        }

        createClaim(player, playerData, lesser, greater, ownerId);
        return true;
    }

    private void createClaim(
            @NotNull Player player,
            @NotNull PlayerData playerData,
            @NotNull Location lesser,
            @NotNull Location greater,
            @Nullable UUID ownerId)
    {
        World world = player.getWorld();
        CreateClaimResult result = plugin.dataStore.createClaim(world,
                lesser.getBlockX(), greater.getBlockX(),
                lesser.getBlockY() - plugin.config_claims_claimsExtendIntoGroundDistance - 1,
                world.getHighestBlockYAt(greater) - plugin.config_claims_claimsExtendIntoGroundDistance - 1,
                lesser.getBlockZ(), greater.getBlockZ(),
                ownerId, null, null, player);
        if (!result.succeeded || result.claim == null)
        {
            if (result.claim != null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapShort);

                BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE);
            }
            else
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
            }
        }
        else
        {
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.CreateClaimSuccess);

            //link to a video demo of land claiming, based on world type
            if (plugin.creativeRulesApply(player.getLocation()))
            {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
            }
            else if (plugin.claimsEnabledForWorld(world))
            {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
            }
            BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM);
            playerData.claimResizing = null;
            playerData.lastShovelLocation = null;

            AutoExtendClaimTask.scheduleAsync(result.claim);

        }
    }

    private boolean needsShovel(@NotNull PlayerData playerData, @NotNull Player player)
    {
        return playerData.getClaims().size() < 2
                && player.getGameMode() != GameMode.CREATIVE
                && player.getInventory().getItemInMainHand().getType() != plugin.config_claims_modificationTool;
    }

    private int getClaimMinRadius()
    {
        return (int) Math.ceil(Math.sqrt(plugin.config_claims_minArea) / 2);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args)
    {
        if (!(sender instanceof Player player))
        {
            return List.of();
        }

        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
        ClaimCommandContext context = new ClaimCommandContext(plugin, player, playerData, command, alias);

        if (args.length >= 1)
        {
            String root = args[0];
            if (equalsIgnoreCase(root, "mode"))
            {
                return completeModes(context, args);
            }

            ClaimCommandSubcommand subcommand = plugin.getClaimCommandExtensionRegistry().getSubcommand(root);
            if (subcommand != null)
            {
                return subcommand.onTabComplete(context, trimArgs(args, 1));
            }
        }

        if (args.length == 1)
        {
            return completeFirstArgument(args);
        }

        return List.of();
    }

    private boolean dispatchExtension(@NotNull ClaimCommandContext context, @NotNull String[] args)
    {
        if (args.length == 0)
        {
            return false;
        }

        if (equalsIgnoreCase(args[0], "mode"))
        {
            return dispatchMode(context, trimArgs(args, 1));
        }

        ClaimCommandSubcommand subcommand = plugin.getClaimCommandExtensionRegistry().getSubcommand(args[0]);
        if (subcommand == null)
        {
            return false;
        }

        return subcommand.onCommand(context, trimArgs(args, 1));
    }

    private boolean dispatchMode(@NotNull ClaimCommandContext context, @NotNull String[] args)
    {
        List<ClaimCommandMode> modes = plugin.getClaimCommandExtensionRegistry().getModes();
        if (args.length == 0)
        {
            if (modes.isEmpty())
            {
                context.getPlayer().sendMessage("/claim mode <name>");
                return true;
            }

            String modeList = String.join(", ", modes.stream().map(ClaimCommandMode::getName).toList());
            context.getPlayer().sendMessage("/claim mode <name>  Available: " + modeList);
            return true;
        }

        ClaimCommandMode mode = plugin.getClaimCommandExtensionRegistry().getMode(args[0]);
        if (mode == null)
        {
            String modeList = String.join(", ", modes.stream().map(ClaimCommandMode::getName).toList());
            context.getPlayer().sendMessage("/claim mode <name>  Available: " + modeList);
            return true;
        }

        return mode.onCommand(context, trimArgs(args, 1));
    }

    private @NotNull List<String> completeModes(@NotNull ClaimCommandContext context, @NotNull String[] args)
    {
        ClaimCommandExtensionRegistry registry = plugin.getClaimCommandExtensionRegistry();
        if (args.length == 2)
        {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            for (ClaimCommandMode mode : registry.getModes())
            {
                if (prefix.isEmpty() || mode.getName().toLowerCase(Locale.ROOT).startsWith(prefix))
                {
                    completions.add(mode.getName());
                }
            }
            completions.sort(String.CASE_INSENSITIVE_ORDER);
            return completions;
        }

        if (args.length > 2)
        {
            ClaimCommandMode mode = registry.getMode(args[1]);
            if (mode != null)
            {
                return mode.onTabComplete(context, trimArgs(args, 2));
            }
        }

        return List.of();
    }

    private @NotNull List<String> completeFirstArgument(@NotNull String[] args)
    {
        String prefix = args[0].toLowerCase(Locale.ROOT);
        ArrayList<String> completions = new ArrayList<>(TabCompletions.integer(args, 3, false));

        if ("mode".startsWith(prefix))
        {
            completions.add("mode");
        }

        for (ClaimCommandSubcommand subcommand : plugin.getClaimCommandExtensionRegistry().getSubcommands())
        {
            if (prefix.isEmpty() || subcommand.getName().toLowerCase(Locale.ROOT).startsWith(prefix))
            {
                completions.add(subcommand.getName());
            }
        }

        completions.sort(String.CASE_INSENSITIVE_ORDER);
        ArrayList<String> unique = new ArrayList<>(completions.size());
        for (String completion : completions)
        {
            if (unique.isEmpty() || !completion.equalsIgnoreCase(unique.getLast()))
            {
                unique.add(completion);
            }
        }
        return unique;
    }

    private void registerBuiltInModes()
    {
        ClaimCommandExtensionRegistry registry = plugin.getClaimCommandExtensionRegistry();
        registry.registerMode(new ClaimCommandMode()
        {
            @Override
            public @NotNull String getName()
            {
                return "basic";
            }

            @Override
            public boolean onCommand(@NotNull ClaimCommandContext context, @NotNull String[] args)
            {
                PlayerData playerData = context.getPlayerData();
                playerData.shovelMode = ShovelMode.Basic;
                playerData.claimSubdividing = null;
                GriefPrevention.sendMessage(context.getPlayer(), TextMode.Success, Messages.BasicClaimsMode);
                return true;
            }
        });
        registry.registerMode(new ClaimCommandMode()
        {
            @Override
            public @NotNull String getName()
            {
                return "admin";
            }

            @Override
            public boolean onCommand(@NotNull ClaimCommandContext context, @NotNull String[] args)
            {
                Player player = context.getPlayer();
                if (!player.hasPermission("griefprevention.adminclaims"))
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
                    return true;
                }

                context.getPlayerData().shovelMode = ShovelMode.Admin;
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.AdminClaimsMode);
                return true;
            }
        });
        registry.registerMode(new ClaimCommandMode()
        {
            @Override
            public @NotNull String getName()
            {
                return "subdivide";
            }

            @Override
            public @NotNull List<String> getAliases()
            {
                return List.of("subdivision");
            }

            @Override
            public boolean onCommand(@NotNull ClaimCommandContext context, @NotNull String[] args)
            {
                PlayerData playerData = context.getPlayerData();
                playerData.shovelMode = ShovelMode.Subdivide;
                playerData.claimSubdividing = null;
                GriefPrevention.sendMessage(context.getPlayer(), TextMode.Instr, Messages.SubdivisionMode);
                GriefPrevention.sendMessage(context.getPlayer(), TextMode.Instr, Messages.SubdivisionVideo2, DataStore.SUBDIVISION_VIDEO_URL);
                return true;
            }
        });
    }

    private static boolean equalsIgnoreCase(@Nullable String left, @Nullable String right)
    {
        return left != null && left.equalsIgnoreCase(right);
    }

    private static @NotNull String[] trimArgs(@NotNull String[] args, int count)
    {
        if (count >= args.length)
        {
            return new String[0];
        }

        return Arrays.copyOfRange(args, count, args.length);
    }

}
