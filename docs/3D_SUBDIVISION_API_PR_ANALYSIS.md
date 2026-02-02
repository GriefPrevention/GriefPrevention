# 3D Subdivision API – PR Analysis & Recommendations

This document analyzes the current GriefPrevention PR branch (minimal 3D API) against the GriefPrevention3D fork, and recommends a minimal PR scope for base GP that enables addons to implement **stackable 3D subdivisions** (multiple claims on the same X/Z at different Y levels) without changing base GP’s storage format.

---

## 1. Current State of the PR Branch (GriefPrevention)

### 1.1 What’s Already There

- **Claim**
  - `is3D`, `set3D(boolean)`, `is3D()`, `containsY(int y)`.
  - Constructor takes `id`; no `is3D` in constructor (set via `set3D()` after creation).
- **DataStore**
  - `createClaim(..., boolean dryRun, boolean is3D)` and internal implementation that:
    - Preserves exact Y for 3D subdivisions (no depth sanitization).
    - Passes `is3D` into the new claim.
  - `assignClaimID(claim)` gives any claim (including subdivisions) a unique ID when `id == null || id == -1`.
  - Overlap check uses `otherClaim.overlaps(newClaim)` → `BoundingBox(this).intersects(BoundingBox(other))`.
- **BoundingBox(Claim)**
  - For 3D claims: keeps exact Y from corners.
  - For 2D claims: extends to world max height.
- **Overlap / containment**
  - `Claim.overlaps()` uses `BoundingBox` → 3D claims only overlap if they intersect in X/Y/Z. So **stackable** (same X/Z, different Y) is already correct.
- **getClaimAt**
  - Respects 3D: uses `containsY`, prefers smaller Y-range when multiple 3D children match.
- **resizeClaim**
  - Passes `claim.is3D()` into `createClaim(..., dryRun, claim.is3D())`.
- **extendClaim / setNewDepth**
  - Skips 3D subdivisions when updating depth (they have explicit Y).
- **FlatFileDataStore**
  - One file per claim (including subdivisions): `{claimID}.yml`.
  - YAML: `Parent Claim ID`, `Is3D`, boundaries, permissions.
  - **No** `Children:` section; subdivisions are separate files with `Parent Claim ID` set.
- **ShovelMode**
  - `Subdivide3D` exists; `PlayerEventHandler` uses it when creating subdivisions (`createClaim(..., null, playerData.claimSubdividing, null, player, false, is3DMode)`).

So the PR branch already has:

- 3D flag and Y-aware logic (create, resize, extend, getClaimAt, contains, overlaps).
- Unique IDs for subdivisions via `assignClaimID` and file name = claim ID.
- Base GP’s **separate-file** model for subdivisions (no Children section).

### 1.2 Gaps for a Minimal “Addon-Ready” API

1. **Subdivisions not in `claimIDMap`**  
   When `addClaim(newClaim, …)` is called with `newClaim.parent != null`, the code only adds the claim to `parent.children` and saves; it never does `claimIDMap.put(newClaim.id, newClaim)`. So `getClaim(subdivisionId)` returns `null` for subdivisions. Addons (and any code that looks up by ID) need subdivisions to be in `claimIDMap`.

2. **ID assignment order for subdivisions**  
   For subdivisions, `assignClaimID` is currently run inside `saveClaim`, which is called from `addClaim` after adding to `parent.children`. To safely put the subdivision in `claimIDMap`, the ID must be assigned before the map put (and before save). So when adding a subdivision, call `assignClaimID(newClaim)` first, then `claimIDMap.put(newClaim.id, newClaim)`, then add to parent and save.

3. **Public API surface**  
   `Public Api.txt` does not document 3D subdivision behavior or the new overloads. It should describe:
   - `createClaim(..., dryRun, is3D)` and that `is3D` enables stackable (same X/Z, different Y) subdivisions.
   - That subdivisions have unique IDs (same as top-level claims) and can be looked up with `getClaim(id)`.
   - `Claim#is3D()`, `containsY(int)`, and that resize/extend respect 3D.

---

## 2. GriefPrevention3D Fork – FlatFileDataStore and Children

### 2.1 How GP3D Stores Subdivisions

- **Root claims**: one file per root claim, `{id}.yml`.
- **Subdivisions**: stored **inside** the root’s YAML under a **`Children:`** section (nested sections keyed by child ID or index).
- **No separate files** for subdivisions; writing a subdivision triggers a write of the **root** file (which includes all children recursively).
- **Subdivision IDs**: GP3D assigns each subdivision a unique ID (same `nextClaimID` pool). Children can have a `Claim ID` in their section; when missing, index is used as key.

### 2.2 Why Not Propose “Children” for Base GP

- Base GP’s design is **one file per claim** and **Parent Claim ID** for subdivisions. The maintainer has not asked for 3D or rental features; a storage format change (moving to a single file with nested Children) is a big behavioral and migration step.
- Your goal is a **minimal API PR** so that **addons** can implement 3D (and optionally different storage). So:
  - **Keep base GP storage as-is**: one file per claim, `Parent Claim ID`, `Is3D`.
  - **Ensure subdivisions have unique IDs** and are in `claimIDMap` so addons (and future GP3D addon) can rely on `getClaim(id)` and consistent behavior.

### 2.3 Addon Compatibility With Base GP Storage

- Addon creates 3D subdivisions via `DataStore.createClaim(..., parent, null, player, false, true)`.
- Base GP will:
  - Assign a unique ID via `assignClaimID`.
  - Write a new file `{id}.yml` with `Parent Claim ID: <parentId>`, `Is3D: true`.
- So the **addon does not need** a Children section in base GP; it uses the existing “one file per claim” model. If the addon later wants a different layout (e.g. one file per root with embedded children), it can do that in its own layer on top of the API.

---

## 3. Subdivision IDs – Aligning With Base GP

### 3.1 Base GP (PR Branch)

- **Top-level claims**: ID from filename when loading, or `nextClaimID` when creating.
- **Subdivisions**: same rules:
  - When **loading**: ID from subdivision file name (`{id}.yml`).
  - When **creating**: `assignClaimID(claim)` sets `claim.id = nextClaimID` and increments.
- So subdivisions **already have** unique IDs in the PR; the only problem is they are not registered in `claimIDMap`.

### 3.2 What “More Inline With Base GP” Means

- Use the **same** ID source as base GP: one global `nextClaimID` for all claims (top-level and subdivisions).
- No “no ID” or “same ID as parent”; every claim has its own `Long id`. The PR already does this; we only need to ensure `claimIDMap` and `assignClaimID` are used consistently for subdivisions (see fixes below).

---

## 4. Cross-Analysis and Minimal PR Scope

### 4.1 In Scope for Minimal PR

- **Stackable 3D subdivisions**: multiple claims on same X/Z, different Y when `is3D == true` and boundaries are valid (no Y overlap). **Already implemented** via `BoundingBox(Claim)` and `overlaps()`.
- **Subdivision IDs**: unique, from same pool as top-level. **Already implemented**; need to **register subdivisions in `claimIDMap`** and document.
- **API**: `createClaim(..., dryRun, is3D)`, `Claim#is3D()`, `containsY(int)`, resize/extend behavior. **Already implemented**; need **documentation** in Public Api and, if desired, a short note in release notes.

### 4.2 Explicitly Out of Scope (Per Your Choice)

- Glow visualization.
- Unified commands / alias.yml.
- Nestable subclaims (subdivisions of subdivisions); base GP’s comment says “subdivisions themselves never have children,” and the PR does not add nesting.
- Any change to base GP’s flatfile format (no Children section; keep one file per claim with Parent Claim ID).

### 4.3 Storage Summary

| Aspect              | Base GP (PR)              | GP3D Fork                 | Recommendation for PR      |
|---------------------|---------------------------|----------------------------|-----------------------------|
| Subdivision storage | One file per claim        | Children in parent YAML    | **Keep** one file per claim |
| Subdivision ID      | Unique, from nextClaimID  | Unique, from nextClaimID   | **Keep**; add claimIDMap    |
| Parent link         | `Parent Claim ID` in YAML| Parent in Children tree    | **Keep** Parent Claim ID    |

---

## 5. Concrete Code Changes for the PR

### 5.1 DataStore – Register Subdivisions in `claimIDMap` (Required)

In `addClaim(Claim newClaim, boolean writeToStorage)` when `newClaim.parent != null`:

1. Call `assignClaimID(newClaim)` so the subdivision has an ID before any map put or save.
2. Put the subdivision in the map: `claimIDMap.put(newClaim.id, newClaim)`.
3. Then add to parent: `parent.children.add(newClaim)`, set `inDataStore = true`, and if `writeToStorage` call `saveClaim(newClaim)`.

No change to `deleteClaim`: it already removes `claim.id` and `child.id` from `claimIDMap`.

### 5.2 Public Api.txt (Recommended)

Add a short section describing:

- 3D subdivisions: `Claim#is3D()`, `containsY(int)`, and that when `is3D` is true, Y boundaries are enforced and multiple subdivisions can share the same X/Z at different Y (stackable).
- Creating 3D subdivisions: use `createClaim(World, x1, x2, y1, y2, z1, z2, ownerID, parent, id, creatingPlayer, dryRun, true)` (last parameter `is3D`).
- Resize/extend: 3D claims keep their Y on resize; extend depth does not change 3D subdivisions.
- Uniquely identifying claims: all claims (including subdivisions) have a unique `Long` ID and can be retrieved with `getClaim(long id)`.

---

## 6. Summary

- **PR branch** already implements stackable 3D subdivisions, unique IDs for subdivisions, and base GP’s separate-file storage with `Parent Claim ID` and `Is3D`.
- **Do not** propose changing base GP to a Children-in-parent-YAML format; keep one file per claim so the PR stays minimal and addon-friendly.
- **Do** fix `addClaim` so subdivisions are in `claimIDMap` (and assign ID before map put), and document the 3D API in Public Api.txt. That gives a minimal, addon-ready 3D subdivision API without new commands or storage changes.
