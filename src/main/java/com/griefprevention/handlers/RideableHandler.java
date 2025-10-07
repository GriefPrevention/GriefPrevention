package com.griefprevention.handlers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import me.ryanhamshire.GriefPrevention.GriefPrevention;

public class RideableHandler implements Listener {

    private final GriefPrevention instance;

    public RideableHandler(GriefPrevention instance) {
        this.instance = instance;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {

        if(!(event.getDismounted() instanceof Player player)) return;
        String playerId = player.getUniqueId().toString();

        // set rideable owner
        PersistentDataContainer pdc = event.getEntity().getPersistentDataContainer();
        pdc.set(instance.RIDEABLE_OWNER, PersistentDataType.STRING, playerId);
    }
}
