/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

/**
 * Enum representing the permissions available in a {@link Claim}.
 */
public enum ClaimPermission
{
    /**
     * ClaimPermission used for owner-based checks. Cannot be granted and grants all other permissions.
     */
    Edit(Messages.OnlyOwnersModifyClaims),
    /**
     * ClaimPermission that allows users to grant ClaimPermissions. Grants {@link #Build}, {@link #Inventory}, and {@link #Access}.
     * Command: /permissiontrust or /managetrust
     */
    Manage(Messages.NoPermissionTrust),
    /**
     * ClaimPermission used for building checks. Grants {@link #Inventory} and {@link #Access}.
     * Command: /trust
     */
    Build(Messages.NoBuildPermission),
    /**
     * ClaimPermission used for inventory management, such as containers and farming. Grants {@link #Access}.
     * Command: /containertrust
     */
    Inventory(Messages.NoContainersPermission),
    /**
     * ClaimPermission used for basic access.
     * Command: /accesstrust
     */
    Access(Messages.NoAccessPermission);

    private final Messages denialMessage;

    ClaimPermission(Messages messages)
    {
        this.denialMessage = messages;
    }

    /**
     * @return the {@link Messages Message} used when alerting a user that they lack the ClaimPermission
     */
    public Messages getDenialMessage()
    {
        return denialMessage;
    }

    /**
     * Check if a ClaimPermission is granted by another ClaimPermission.
     *
     * @param other the ClaimPermission to compare against
     * @return true if this ClaimPermission is equal or lesser than the provided ClaimPermission
     */
    public boolean isGrantedBy(ClaimPermission other)
    {
        // This uses declaration order to compare! If trust levels are reordered this method must be rewritten.
        return other != null && other.ordinal() <= this.ordinal();
    }

}
