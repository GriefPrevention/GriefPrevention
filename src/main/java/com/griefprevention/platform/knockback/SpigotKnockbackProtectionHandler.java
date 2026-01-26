package com.griefprevention.platform.knockback;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Spigot/Bukkit implementation of knockback protection handling.
 * Uses {@link EntityKnockbackByEntityEvent} from the Bukkit API.
 * <p>
 * Handles all player-caused knockback including melee attacks (spears),
 * projectiles (wind charges), and other mechanisms (shield blocks).
 * <p>
 * Unlike Paper, Bukkit does not resolve projectiles to their shooter in this event,
 * so this handler must check for {@link Projectile} sources and extract the shooter.
 */
public class SpigotKnockbackProtectionHandler extends KnockbackProtectionHandler
{

    public SpigotKnockbackProtectionHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        super(dataStore, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityKnockbackByEntity(@NotNull EntityKnockbackByEntityEvent event)
    {
        Entity sourceEntity = event.getSourceEntity();
        Entity knockedEntity = event.getEntity();

        Player attacker;

        // Handle projectiles (wind charges, etc.) - must extract shooter
        if (sourceEntity instanceof Projectile projectile)
        {
            if (!(projectile.getShooter() instanceof Player shooter)) return;
            attacker = shooter;
        }
        // Handle direct player knockback (melee, shield block, etc.)
        else if (sourceEntity instanceof Player player)
        {
            attacker = player;
        }
        else
        {
            return;
        }

        if (knockedEntity instanceof Player defender)
        {
            handleKnockbackPlayer(event, attacker, defender);
        }
        else
        {
            handleKnockbackEntity(event, attacker, knockedEntity);
        }
    }

}