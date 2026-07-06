# Changelog — Session du 5 juillet 2026

## Contexte

Correction d'erreurs de compatibilité avec **Minecraft 26.2** dans le mixin `PlayerDropItemMixin`, qui empêche les joueurs de drop les cubes et boussoles pendant une partie.

---

## Fichiers modifiés

### `src/main/java/fr/chixi/cubeconquest/mixin/PlayerDropItemMixin.java`

#### 1. `Inventory.getSelected()` → `Inventory.getSelectedItem()`

| | |
|---|---|
| **Type** | `fix` — Renommage d'API MC 26.2 |
| **Problème** | `getSelected()` n'existe plus sur `Inventory` en 26.2, provoquant une erreur de compilation. |
| **Cause** | Mojang a renommé la méthode en `getSelectedItem()`. |

```diff
- ItemStack itemStack = serverPlayer.getInventory().getSelected();
+ ItemStack itemStack = serverPlayer.getInventory().getSelectedItem();
```

#### 2. Cible du Mixin modifiée (`Player` → `ServerPlayer`)

| | |
|---|---|
| **Type** | `fix` — Drop infini du cube (bypass) |
| **Problème** | Le joueur pouvait contourner l'interdiction de drop son cube. Le mixin ciblait `Player.class` avec la méthode à deux arguments `drop(ItemStack, boolean)`, mais le jeu utilisait une méthode différente sur le serveur. |
| **Cause** | En MC 26.2, c'est la méthode de `ServerPlayer` (ou `LivingEntity`) avec **3 arguments** (`drop(ItemStack, boolean, boolean)`) qui est appelée directement lors d'un drop manuel. Le hook sur `Player.drop(ItemStack, boolean)` était donc ignoré par le serveur. |
| **Solution** | Le mixin `@Mixin(Player.class)` a été transformé en `@Mixin(ServerPlayer.class)`. La cible du `@Inject` a été ajustée à `drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;`. Ce nouveau hook intercepte bien le drop effectif côté serveur et empêche la duplication du cube. |

### `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java`

#### 1. Correction de la formule d'angle de la boussole

| | |
|---|---|
| **Type** | `fix` — Inversion (effet miroir) de la boussole |
| **Problème** | L'aiguille de la boussole tournait dans la direction opposée (symétrie par rapport à l'axe de vision). |
| **Cause** | La soustraction `yaw - targetAngleDeg` entraînait une rotation inversée en fonction de la direction de vue de Minecraft. |
| **Solution** | Remplacement de la soustraction par `targetAngleDeg - yaw` pour la normalisation de l'angle. Cela s'aligne avec le calcul standard MC. |

---

## Vérification

- ✅ `./gradlew test` — Tests unitaires validés (incluant la vérification de la nouvelle formule de la boussole).
- ⏳ `./gradlew runClient` et `./gradlew runServer` — à tester en jeu pour confirmer le fonctionnement.
