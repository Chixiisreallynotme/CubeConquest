# Iter18 Implementation Plan — 2026-07-02

Base commit: ecb44a0

## Findings approved (4)

| # | Sev | Title |
|---|-----|-------|
| 1 | HIGH | Phantom-check peut dupliquer le cube si vanilla rejette le placement (collision entité) |
| 2 | MEDIUM | removeCubeFromInventory ne retire que le premier exemplaire — doublon survit au nettoyage |
| 3 | MEDIUM | inventory.add() silencieux si inventaire plein — porteur bloqué sans feedback |
| 4 | LOW | Pas de PlacementCountdownPayload à la transition PREP→PLACEMENT — 1 seconde de HUD vide |

## Tasks

### Task 1 — HIGH+MEDIUM: Fix phantom-check duplication + removeCubeFromInventory sweep
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

Covers findings #1 and #2.

**1a** — In phantom-check block (inside `handlePlacementTick`, the section that calls
`porteur.getInventory().add(...)`), add a `hasIt` guard before re-issuing — same pattern
already used in per-tick recovery. The current code at ~line 216:

```java
porteur.getInventory().add(new ItemStack(cubeBlockFor(team).asItem()));
```

Replace with:
```java
Item cubeItem = cubeBlockFor(team).asItem();
boolean hasIt = false;
for (int i = 0; i < porteur.getInventory().getContainerSize(); i++) {
    if (porteur.getInventory().getItem(i).getItem() == cubeItem) { hasIt = true; break; }
}
if (!hasIt) porteur.getInventory().add(new ItemStack(cubeItem)); // ponytail: guard against phantom-check + vanilla-rejection duplicate
```

**1b** — In `removeCubeFromInventory`, remove the `break` so all matching slots are cleared:

```java
// ponytail: no break — sweep all slots in case of duplicates (phantom + vanilla-rejection race)
private static void removeCubeFromInventory(ServerPlayer player, Team team) {
    net.minecraft.world.item.Item cubeItem = cubeBlockFor(team).asItem();
    var inv = player.getInventory();
    for (int i = 0; i < inv.getContainerSize(); i++) {
        if (inv.getItem(i).getItem() == cubeItem) {
            inv.setItem(i, ItemStack.EMPTY);
        }
    }
}
```

### Task 2 — MEDIUM: Full-inventory feedback
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

Covers finding #3.

In the per-tick recovery block (inside `handlePlacementTick`), the last line currently
calls `porteur.getInventory().add(...)` unconditionally when `!hasIt`. Change it to check
the return value and notify every 60 ticks if add fails:

```java
if (!hasIt) {
    boolean added = porteur.getInventory().add(new ItemStack(cubeItem));
    // ponytail: notify porteur on full inventory — throttled to every 3 s to avoid chat spam
    if (!added && tickCount % 60 == 0) {
        porteur.sendSystemMessage(Component.literal(
            "[CubeConquest] Inventory full — drop something to receive the cube!")
            .withStyle(ChatFormatting.RED));
    }
}
```

### Task 3 — LOW: Initial countdown on PREP→PLACEMENT transition
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

Covers finding #4.

In `handlePreparationTick`, after setting phase to PLACEMENT and broadcasting, send the
initial countdown payload immediately so the HUD shows 3:00 from tick 0:

```java
// ponytail: send initial countdown immediately — first handlePlacementTick modulo fires at tick 20
PlacementCountdownPayload initial = new PlacementCountdownPayload(PLACEMENT_TIMEOUT_TICKS);
for (ServerPlayer player : server.getPlayerList().getPlayers()) {
    ServerPlayNetworking.send(player, initial);
}
```

Place this block after the `sendTitle` call and before the closing `}` of the
`if (tickCount >= PREPARATION_TICKS)` block.

## Order of execution

Task 1: phantom-check + removeCubeFromInventory — run first
Tasks 2 + 3: parallel (different sections, no dependency on each other; both after Task 1)

Each task: implementer subagent → reviewer subagent → commit on NICE.
