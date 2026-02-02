# Add API for 3D (stackable) subdivisions

This PR adds a minimal API so extensions (and optionally core) can support **3D subdivisions**: subclaims that enforce Y boundaries. That allows multiple subdivisions of the same parent to share the same X/Z area at different Y levels—“stackable” subclaims—without overlapping.

No new commands, no storage format changes. One file per claim (including subdivisions) with existing `Parent Claim ID`; new `Is3D` flag and Y-aware behavior so addons can implement the feature.

---

## Community feedback

> **Screenshot placeholder:** Add a screenshot here of someone saying their server loves this feature / that they’d like to see it in base GP (e.g. Discord or forum message). Example caption: *"Community feedback from [source] – players want 3D subdivisions so we can add it as an addon."*

![Screenshot: paste image of community message here](paste-your-screenshot-here.png)

---

## What this PR does

- **Claim**
  - `is3D()` / `set3D(boolean)` – whether the claim enforces Y boundaries (3D subdivision).
  - `containsY(int y)` – for 3D claims, returns whether block Y is inside the claim’s Y range; for non-3D, always `true`.
- **DataStore**
  - `createClaim(..., boolean dryRun, boolean is3D)` – when `is3D` is true (and `parent != null`), the new subdivision keeps the exact Y range from the given corners instead of being depth-sanitized like 2D subdivisions.
  - `resizeClaim(Claim claim, int newx1, int newx2, int newy1, int newy2, int newz1, int newz2, Player resizingPlayer)` – already takes full bounds; for 3D claims the newy1/newy2 values are used as the new vertical bounds (no depth sanitization). For non-3D claims, existing depth logic is unchanged.
  - Subdivisions are registered in `claimIDMap` so `getClaim(long id)` works for them.
- **Lookup & overlap**
  - `getClaimAt` respects 3D: if a location is inside both a parent and a 3D child, the 3D child is returned. Overlap checks use `BoundingBox(Claim)`, which for 3D claims keeps exact Y, so two 3D subdivisions on the same X/Z but different Y do not overlap (stackable).
- **Extend claim downward**
  - `extendClaim` does not change 3D subdivisions; they keep their explicit Y bounds.
- **ShovelMode**
  - `Subdivide3D` exists so core (or an addon) can offer a 3D subdivision mode; creation uses the new `createClaim(..., dryRun, true)` overload.

Storage: FlatFileDataStore still writes one file per claim; subdivisions have `Parent Claim ID` and new `Is3D` in YAML. No Children-in-parent format change.

---

## Y enforcement when `is3D` is true

- **Creation:** `createClaim(..., is3D: true)` uses the provided y1/y2 as the claim’s vertical bounds (no depth sanitization). So the subdivision’s corners keep that Y range.
- **Containment:** For 3D claims, `contains(location, ...)` effectively requires the location’s block Y to be inside the claim’s Y range (via `BoundingBox(Claim)` and, where used, `containsY`). For non-3D claims, Y is ignored as today.
- **Lookup:** `getClaimAt(location, ...)` returns a 3D subdivision only if the location is inside its X/Y/Z box; it prefers the most specific 3D child when multiple overlap in X/Z.
- **Overlap:** Two claims overlap only if their bounding boxes intersect. For 3D claims, `BoundingBox(Claim)` uses the claim’s actual min/max Y, so same X/Z but different Y ranges = no overlap (stackable).
- **Resize:** `resizeClaim(claim, newx1, newx2, newy1, newy2, newz1, newz2, player)` passes these into a dry-run create that uses `claim.is3D()`. For 3D claims, newy1/newy2 become the new vertical bounds; for non-3D, existing depth behavior is unchanged.
- **Extend down:** `extendClaim(claim, newDepth)` only adjusts non-3D claims; 3D subdivisions are skipped so their Y bounds stay explicit.

So: when `is3D` is true, the claim’s Y range is stored and used everywhere (containment, lookup, overlap, resize). When false, behavior matches current GP (no Y enforcement for subdivisions).

---

## Resize and the new `newy1` / `newy2` parameters

`resizeClaim` already takes six coordinates:

- **newx1, newx2** – new X bounds  
- **newy1, newy2** – new Y (vertical) bounds  
- **newz1, newz2** – new Z bounds  

For **3D claims**, these are applied directly: the claim’s corners are set to `(newx1, newy1, newz1)` and `(newx2, newy2, newz2)` (with min/max normalized). So addons and core can resize 3D subdivisions in height (Y) as well as in X/Z.

For **non-3D claims**, the existing depth logic still runs after resize (e.g. `setNewDepth`), so current behavior is unchanged. Only 3D claims get “full 3D” resize from newy1/newy2.

**Note:** `Claim.getWidth()` and `Claim.getHeight()` are unchanged: they return the claim’s extent in X and Z (horizontal dimensions), not the vertical Y extent. The vertical extent of a 3D claim is `getGreaterBoundaryCorner().getBlockY() - getLesserBoundaryCorner().getBlockY() + 1` (or from the corner locations).

---

## Addon use case

An addon can:

1. Let players create 3D subdivisions via `createClaim(world, x1, x2, y1, y2, z1, z2, ownerID, parent, null, player, false, true)`.
2. Look them up by ID with `getClaim(id)`.
3. Resize them with `resizeClaim(claim, newx1, newx2, newy1, newy2, newz1, newz2, player)`.
4. Query at a location with `getClaimAt(location, false, null)` and get the 3D subclaim when applicable.

No changes to base GP’s file-per-claim storage or commands are required; the PR only adds data (Is3D, Y in corners), API (createClaim overload, is3D/containsY, resize behavior), and subdivision registration in `claimIDMap`.

---

## Checklist

- [x] One file per claim; `Parent Claim ID` and `Is3D` in YAML only; no Children section.
- [x] Subdivisions get unique IDs and are in `claimIDMap`.
- [x] 3D overlap uses Y (stackable when same X/Z, different Y).
- [x] Public Api.txt and docs updated for 3D and resize/newy1/newy2.
