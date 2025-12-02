package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.events.PreventPvPEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Handles wind charge knockback events. This class supports both Paper's
 * {@code com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent} and Bukkit's
 * {@code org.bukkit.event.entity.EntityKnockbackByEntityEvent}.
 * <p>
 * At runtime, the appropriate listener is registered based on server implementation:
 * Paper's event is preferred when available since it fires first and is not deprecated on Paper.
 * <p>
 * Note: Paper resolves projectiles to their shooter, so the Paper listener handles all
 * EXPLOSION knockback caused by players (covering wind charges and other player-caused explosions).
 * On Spigot/Bukkit, the handler checks for {@link AbstractWindCharge} directly.
 */
public class WindChargeKnockbackHandler
{

    private final DataStore dataStore;
    private final GriefPrevention instance;

    WindChargeKnockbackHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        this.dataStore = dataStore;
        this.instance = plugin;
    }

    /**
     * Creates and returns the appropriate listener for the current server implementation.
     * Prefers Paper's event when available, falls back to Bukkit's event otherwise.
     *
     * @return the listener to register, or null if no knockback event is available
     */
    public Listener createListener()
    {
        try
        {
            Class.forName("com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent");
            return new PaperKnockbackListener();
        }
        catch (ClassNotFoundException ignored)
        {
            // Paper event not available, try Bukkit's.
        }

        try
        {
            Class.forName("org.bukkit.event.entity.EntityKnockbackByEntityEvent");
            return new BukkitKnockbackListener();
        }
        catch (ClassNotFoundException ignored)
        {
            // Neither event is available.
        }

        return null;
    }

    /**
     * Listener for Paper's EntityKnockbackByEntityEvent.
     * <p>
     * Paper resolves projectiles to their shooter, so we cannot check for AbstractWindCharge
     * directly. Instead, we handle EXPLOSION knockback caused by players, which covers
     * wind charges and other player-caused explosions.
     */
    private class PaperKnockbackListener implements Listener
    {
        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onEntityKnockbackByEntity(
                @NotNull com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent event)
        {
            var explosionCause = io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.EXPLOSION;
            if (event.getCause() != explosionCause) return;
            if (!(event.getHitBy() instanceof Player attacker)) return;

            if (event.getEntity() instanceof Player defender)
            {
                handleWindChargeKnockbackPlayer(event, attacker, defender);
            }
            else
            {
                handleWindChargeKnockbackEntity(event, attacker, event.getEntity());
            }
        }
    }

    /**
     * Listener for Bukkit's EntityKnockbackByEntityEvent.
     */
    private class BukkitKnockbackListener implements Listener
    {
        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onEntityKnockbackByEntity(
                @NotNull org.bukkit.event.entity.EntityKnockbackByEntityEvent event)
        {
            Entity sourceEntity = event.getSourceEntity();
            Entity knockedEntity = event.getEntity();

            if (!(sourceEntity instanceof AbstractWindCharge windCharge)) return;

            // Only handle player-caused wind charges.
            if (!(windCharge.getShooter() instanceof Player attacker)) return;

            if (knockedEntity instanceof Player defender)
            {
                handleWindChargeKnockbackPlayer(event, attacker, defender);
            }
            else
            {
                handleWindChargeKnockbackEntity(event, attacker, knockedEntity);
            }
        }
    }

    /**
     * Handle wind charge knockback against players. Uses PVP rules to determine if knockback
     * should be allowed.
     *
     * @param event the knockback event
     * @param attacker the {@link Player} who shot the wind charge
     * @param defender the {@link Player} being knocked back
     * @param <T> event type that extends Event and implements Cancellable
     */
    private <T extends Event & Cancellable> void handleWindChargeKnockbackPlayer(
            @NotNull T event,
            @NotNull Player attacker,
            @NotNull Player defender)
    {
        // Always allow self-knockback for mobility.
        if (attacker.equals(defender)) return;

        PlayerData defenderData = this.dataStore.getPlayerData(defender.getUniqueId());
        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());

        if (attackerData.ignoreClaims) return;

        // Check if defender is in a claim where attacker has trust.
        Claim defenderClaim = this.dataStore.getClaimAt(defender.getLocation(), false, defenderData.lastClaim);
        if (defenderClaim != null)
        {
            defenderData.lastClaim = defenderClaim;

            // If the attacker has container trust, allow the knockback.
            if (defenderClaim.checkPermission(attacker, ClaimPermission.Inventory, null) == null)
            {
                return;
            }
        }

        // If PVP rules don't apply to this world, prevent knockback.
        // In PvE worlds, players shouldn't be able to push each other
        // around (unless in a trusted claim, see previous checks).
        if (!instance.pvpRulesApply(defender.getWorld()))
        {
            event.setCancelled(true);
            return;
        }

        // Protect fresh spawns from knockback abuse.
        if (instance.config_pvp_protectFreshSpawns)
        {
            if (attackerData.pvpImmune || defenderData.pvpImmune)
            {
                event.setCancelled(true);
                GriefPrevention.sendMessage(
                        attacker,
                        TextMode.Err,
                        attackerData.pvpImmune ? Messages.CantFightWhileImmune : Messages.ThatPlayerPvPImmune);
                return;
            }
        }

        // Check if defender is in a PVP safezone.
        if (defenderClaim != null && instance.claimIsPvPSafeZone(defenderClaim))
        {
            PreventPvPEvent pvpEvent = new PreventPvPEvent(defenderClaim, attacker, defender);
            Bukkit.getPluginManager().callEvent(pvpEvent);
            if (!pvpEvent.isCancelled())
            {
                event.setCancelled(true);
                GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.PlayerInPvPSafeZone);
            }
            return;
        }

        // Check if attacker is in a PVP safezone (prevent shooting from safezone).
        Claim attackerClaim = this.dataStore.getClaimAt(attacker.getLocation(), false, attackerData.lastClaim);
        if (attackerClaim != null)
        {
            attackerData.lastClaim = attackerClaim;
            if (instance.claimIsPvPSafeZone(attackerClaim))
            {
                PreventPvPEvent pvpEvent = new PreventPvPEvent(attackerClaim, attacker, defender);
                Bukkit.getPluginManager().callEvent(pvpEvent);
                if (!pvpEvent.isCancelled())
                {
                    event.setCancelled(true);
                    GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.CantFightWhileImmune);
                }
            }
        }
    }

    /**
     * Handle wind charge knockback against non-player entities. Prevents moving protected
     * entities out of claims.
     *
     * @param event the knockback event
     * @param attacker the {@link Player} who shot the wind charge
     * @param entity the {@link Entity} being knocked back
     * @param <T> event type that extends Event and implements Cancellable
     */
    private <T extends Event & Cancellable> void handleWindChargeKnockbackEntity(
            @NotNull T event,
            @NotNull Player attacker,
            @NotNull Entity entity)
    {
        if (!instance.claimsEnabledForWorld(entity.getWorld())) return;

        // Determine protection type and required permission.
        ClaimPermission requiredPermission;
        if (entity instanceof ArmorStand || entity instanceof Hanging)
        {
            // These require build trust, matching handleClaimedBuildTrustDamageByEntity.
            requiredPermission = ClaimPermission.Build;
        }
        else if (entity instanceof Creature && instance.config_claims_protectCreatures)
        {
            // Creatures require container trust, matching handleCreatureDamageByEntity,
            // but skip monsters - they are never protected.
            if (EntityDamageHandler.isHostile(entity)) return;
            requiredPermission = ClaimPermission.Inventory;
        }
        else
        {
            // Entity type not protected.
            return;
        }

        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());

        if (attackerData.ignoreClaims) return;

        Claim claim = this.dataStore.getClaimAt(entity.getLocation(), false, attackerData.lastClaim);

        if (claim == null) return;

        attackerData.lastClaim = claim;

        Supplier<String> noPermissionReason = claim.checkPermission(attacker, requiredPermission, event);
        if (noPermissionReason != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(attacker, TextMode.Err, noPermissionReason.get());
        }
    }

}
