# Migration HUD — Minecraft 26.2 / Fabric API 0.153.0

> [!NOTE]
> Ce document récapitule les modifications apportées à [CubeConquestHud.java](file:///C:/Users/Chixi/Documents/Projects/Minecraft/CubeConquest/src/client/java/fr/chixi/cubeconquest/client/CubeConquestHud.java) pour corriger les 4 erreurs de compilation sur MC 26.2.

---

## Contexte

Le module `fabric-rendering-v1` (v25.2.0) a supprimé l'ancien callback `HudRenderCallback` au profit d'un système déclaratif basé sur `HudElement` + `HudElementRegistry`.  
Minecraft 26.2 a également renommé `GuiGraphics` en `GuiGraphicsExtractor` et `drawString()` en `text()`.

---

## Erreurs corrigées

| # | Erreur | Cause |
|---|--------|-------|
| 1 | `cannot find symbol: class HudRenderCallback` | Classe supprimée de l'API Fabric |
| 2 | `cannot find symbol: class GuiGraphics` | Renommée en `GuiGraphicsExtractor` dans MC 26.2 |
| 3 | `GuiGraphics` dans la signature de `render()` | Idem — type inexistant |
| 4 | `package HudRenderCallback does not exist` | Conséquence de l'erreur #1 |

---

## Changements apportés

### 1. Imports

```diff
-import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
+import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
+import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
-import net.minecraft.client.gui.GuiGraphics;
+import net.minecraft.client.gui.GuiGraphicsExtractor;
+import net.minecraft.resources.Identifier;
```

### 2. Déclaration de la classe

La classe implémente désormais l'interface `HudElement` :

```diff
-public final class CubeConquestHud {
+public final class CubeConquestHud implements HudElement {
+
+    private static final Identifier HUD_ID =
+        Identifier.fromNamespaceAndPath("cubeconquest", "game_hud");
```

### 3. Enregistrement

L'ancien callback événementiel est remplacé par un enregistrement déclaratif avec un `Identifier` :

```diff
 public static void register() {
-    HudRenderCallback.EVENT.register(CubeConquestHud::render);
+    HudElementRegistry.addLast(HUD_ID, new CubeConquestHud());
 }
```

### 4. Méthode de rendu

La méthode statique `render` devient l'implémentation de l'interface `HudElement.extractRenderState()` :

```diff
-private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
+@Override
+public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
```

### 5. Appels de dessin

`drawString()` est renommé en `text()` sur `GuiGraphicsExtractor` :

```diff
-graphics.drawString(mc.font,
+graphics.text(mc.font,
     Component.translatable("cubeconquest.hud.placement_countdown", countdown / 20),
     x, y + 10, 0xFFFF55, true);
```

---

## Tableau récapitulatif des renommages MC 26.2

| Ancien (pré-26.2) | Nouveau (26.2) | Package |
|---|---|---|
| `HudRenderCallback` | `HudElement` + `HudElementRegistry` | `net.fabricmc.fabric.api.client.rendering.v1.hud` |
| `GuiGraphics` | `GuiGraphicsExtractor` | `net.minecraft.client.gui` |
| `drawString()` | `text()` | méthode sur `GuiGraphicsExtractor` |
| Callback lambda statique | Interface `HudElement#extractRenderState()` | — |

---

## Vérification

```
> gradlew build

BUILD SUCCESSFUL in 4s
10 actionable tasks: 4 executed, 6 up-to-date
```

> [!TIP]
> L'API `HudElementRegistry` offre un contrôle plus fin sur l'ordre de rendu via `addFirst()`, `addLast()`, `attachElementBefore()` et `attachElementAfter()` — voir [VanillaHudElements](file:///C:/Users/Chixi/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-rendering-v1/25.2.0+2b0d8a229e) pour les points d'injection disponibles (ex. `BOSS_BAR`, `CHAT`, `SUBTITLES`).
