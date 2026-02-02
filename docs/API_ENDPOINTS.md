# GriefPrevention Extension API – Endpoints Outline

Use `GriefPrevention.instance.dataStore` to get the `DataStore`. All claim lookups and mutations go through the DataStore unless noted.

---

## DataStore – Claim lookup & listing

| Method | Description |
|--------|-------------|
| `getClaimAt(Location location, boolean ignoreHeight, Claim cachedClaim)` | Claim at a location; optional cached claim for performance. Respects 3D (returns most specific claim, e.g. 3D subdivision over parent). |
| `getClaimAt(Location location, boolean ignoreHeight, boolean ignoreSubclaims, Claim cachedClaim)` | Same, with option to return top-level claim only (ignore subdivisions). |
| `getClaim(long id)` | Claim by unique ID. Works for top-level claims and subdivisions. |
| `getClaims()` | Unmodifiable collection of all top-level claims. |
| `getClaims(int chunkx, int chunkz)` | Unmodifiable collection of top-level claims in a chunk. |
| `getChunkClaims(World world, BoundingBox boundingBox)` | Set of top-level claims overlapping the box (by chunk). |
| `getChunkHash(long chunkx, long chunkz)` | Chunk hash for use with chunk maps. |
| `getChunkHash(Location location)` | Chunk hash from location. |
| `getChunkHashes(Claim claim)` | List of chunk hashes for a claim. |
| `getChunkHashes(Location min, Location max)` | List of chunk hashes for a bounding area. |

---

## DataStore – Claim creation, resize, delete, extend

| Method | Description |
|--------|-------------|
| `createClaim(World world, int x1, int x2, int y1, int y2, int z1, int z2, UUID ownerID, Claim parent, Long id, Player creatingPlayer)` | Create claim; parent null = top-level, id null = new ID. Fires ClaimCreatedEvent. |
| `createClaim(..., boolean dryRun, boolean is3D)` | Same, with dry run (no save) and 3D subdivision flag. Use for stackable subdivisions (same X/Z, different Y). |
| `createClaim(..., boolean dryRun)` | Same, dryRun only; is3D = false. |
| `resizeClaim(Claim claim, int newx1, int newx2, int newy1, int newy2, int newz1, int newz2, Player resizingPlayer)` | Resize claim; preserves is3D and Y for 3D claims. Fires ClaimResizeEvent. |
| `deleteClaim(Claim claim)` | Delete claim and all its subdivisions. Fires ClaimDeletedEvent. |
| `deleteClaim(Claim claim, boolean releasePets)` | Deprecated; use deleteClaim(Claim). |
| `extendClaim(Claim claim, int newDepth)` | Extend claim downward; does not change 3D subdivisions. Fires ClaimExtendEvent. |
| `deleteClaimsForPlayer(UUID playerID, boolean releasePets)` | Delete all claims owned by a player. |
| `saveClaim(Claim claim)` | Persist claim (and assign ID if needed). |

---

## DataStore – Ownership & player data

| Method | Description |
|--------|-------------|
| `changeClaimOwner(Claim claim, UUID newOwnerID)` | Transfer claim ownership. Fires ClaimTransferEvent. |
| `getPlayerData(UUID playerID)` | Player data (claim blocks, claims list, etc.); creates empty if missing. |
| `savePlayerData(UUID playerID, PlayerData playerData)` | Save player data (async). |
| `savePlayerDataSync(UUID playerID, PlayerData playerData)` | Save player data synchronously. |
| `asyncSavePlayerData(UUID playerID, PlayerData playerData)` | Low-level async save. |
| `getGroupBonusBlocks(UUID playerID)` | Bonus claim blocks from permission groups. |
| `adjustGroupBonusBlocks(String groupName, int amount)` | Change a group’s bonus blocks. |

---

## DataStore – Other

| Method | Description |
|--------|-------------|
| `loadBannedWords()` | Banned words list. |
| `isSoftMuted(UUID playerID)` | Whether player is soft-muted. |
| `getMessage(Messages messageID, String... args)` | Localized message string. |
| `tryAdvertiseAdminAlternatives(Player player)` | Sends admin-claim info to player if applicable. |

---

## Claim – Identity & boundaries

| Method | Description |
|--------|-------------|
| `getID()` | Unique Long id (null only before first save). |
| `getLesserBoundaryCorner()` | Lower corner location. |
| `getGreaterBoundaryCorner()` | Upper corner location. |
| `getArea()` | Area in blocks (X×Z). |
| `getWidth()` | Width (X). |
| `getHeight()` | Height (Z). |
| `getChunks()` | Chunks that this claim overlaps. |
| `getOwnerID()` | Owner UUID; null = admin claim. |
| `getOwnerName()` | Owner display name or "an administrator". |
| `isAdminClaim()` | True if owner is null. |
| `parent` | Parent claim; null for top-level. |
| `children` | List of subdivisions (direct children only). |

---

## Claim – 3D subdivisions

| Method | Description |
|--------|-------------|
| `is3D()` | True if Y boundaries are enforced (3D subdivision). |
| `set3D(boolean is3D)` | Set 3D flag. |
| `containsY(int y)` | True if y is inside this claim’s Y range (for 3D); always true for non-3D. |

---

## Claim – Containment & proximity

| Method | Description |
|--------|-------------|
| `contains(Location location, boolean ignoreHeight, boolean excludeSubdivisions)` | Whether location is in this claim; 3D claims ignore ignoreHeight for Y. |
| `isNear(Location location, int howNear)` | Whether location is within a band around the claim. |

---

## Claim – Permissions (trust)

| Method | Description |
|--------|-------------|
| `setPermission(String playerID, ClaimPermission permissionLevel)` | Set trust (Build, Inventory, Access, Manage). |
| `dropPermission(String playerID)` | Remove trust. |
| `clearPermissions()` | Clear all trust entries. |
| `getPermissions(ArrayList<String> builders, ArrayList<String> containers, ArrayList<String> accessors, ArrayList<String> managers)` | Fill lists with trusted IDs. |
| `hasExplicitPermission(UUID uuid, ClaimPermission level)` | Whether UUID has that permission level. |
| `hasExplicitPermission(Player player, ClaimPermission level)` | Same for player (includes permission-node checks). |
| `checkPermission(Player player, ClaimPermission permission, Event event)` | Full check; returns denial Supplier or null if allowed. |
| `getSubclaimRestrictions()` / `setSubclaimRestrictions(boolean)` | Whether subclaim inherits nothing. |
| `managers` | List of manager IDs (e.g. UUID strings). |

---

## Supporting types

| Type | Use |
|------|-----|
| `CreateClaimResult` | Result of createClaim; `succeeded`, `claim` (created or conflicting). |
| `ClaimPermission` | Build, Inventory, Access, Manage, Edit. |
| `PlayerData` | Accrued/bonus claim blocks, claims list, etc. |
| `BoundingBox` (me.ryanhamshire.GriefPrevention.util) | 3D box; constructor from Claim respects is3D (exact Y for 3D). |
| `ShovelMode` | Basic, Admin, Subdivide, Subdivide3D (for addons that set player mode). |

---

## Events (me.ryanhamshire.GriefPrevention.events)

| Event | When |
|-------|------|
| `ClaimCreatedEvent` | After a claim is created (cancel to block). |
| `ClaimDeletedEvent` | After a claim is deleted. |
| `ClaimResizeEvent` | When a claim is resized (cancel to block). |
| `ClaimTransferEvent` | When claim owner is changed (cancel to block). |
| `ClaimExtendEvent` | When claim is extended downward (cancel to block). |
| `ClaimPermissionCheckEvent` | When a permission is checked (override result). |
| `ClaimModifiedEvent` | When claim data is modified (deprecated). |
| Others | ClaimInspectionEvent, ProtectDeathDropsEvent, TrustChangedEvent, etc. |

---

## 3D subdivision usage summary

- **Create:** `dataStore.createClaim(world, x1, x2, y1, y2, z1, z2, ownerID, parent, null, player, false, true)`.
- **Lookup by ID:** `dataStore.getClaim(id)` (works for subdivisions).
- **Check at location:** `dataStore.getClaimAt(loc, false, null)` (returns 3D subclaim when applicable).
- **Test Y:** `claim.is3D()` and `claim.containsY(y)`.
- **Resize:** `dataStore.resizeClaim(claim, ...)` keeps is3D and Y.
- **Extend down:** `dataStore.extendClaim(claim, depth)` does not change 3D subdivisions.
