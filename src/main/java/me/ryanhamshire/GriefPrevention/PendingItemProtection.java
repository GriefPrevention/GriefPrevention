package me.ryanhamshire.GriefPrevention;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

class PendingItemProtection
{

    public Location location;

    public UUID owner;

    long expirationTimestamp;

    ItemStack itemStack;

    public PendingItemProtection(Location location, UUID owner, long expirationTimestamp, ItemStack itemStack)
    {
        this.location = location;
        this.owner = owner;
        this.expirationTimestamp = expirationTimestamp;
        this.itemStack = itemStack;
    }
}
