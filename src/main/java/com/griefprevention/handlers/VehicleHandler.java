package com.griefprevention.handlers;

import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.persistence.PersistentDataType;

import me.ryanhamshire.GriefPrevention.GriefPrevention;

public class VehicleHandler implements Listener{

    private final GriefPrevention instance;

    public VehicleHandler(GriefPrevention instance) {
        this.instance = instance;
    }

    @EventHandler (ignoreCancelled = true)
    public void onExitVehicle(VehicleExitEvent event) {

        // only care about players
        if(!(event.getExited() instanceof Player player)) return;
        String playerId = player.getUniqueId().toString();
        
        Vehicle vehicle = event.getVehicle();
        vehicle.getPersistentDataContainer().set(instance.VEHICLE_OWNER, PersistentDataType.STRING, playerId);
    }
    
}
