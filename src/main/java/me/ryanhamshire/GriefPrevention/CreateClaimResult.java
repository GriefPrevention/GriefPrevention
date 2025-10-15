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

import org.jetbrains.annotations.Nullable;

public class CreateClaimResult
{

    //whether or not the creation succeeded (it would fail if the new claim overlapped another existing claim)
    public boolean succeeded;

    //when succeeded, this is a reference to the new claim
    //when failed, this is a reference to the pre-existing, conflicting claim
    public @Nullable Claim claim;
}
