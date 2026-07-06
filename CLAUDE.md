# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Cube Conquest** — mod multijoueur compétitif Fabric pour Minecraft **26.2** ("Chaos Cubed"). Deux équipes (Rouge / Bleu) s'affrontent pour détruire le cube adverse. Le cube ne peut être détruit que par l'équipe adverse.

## Environnement cible

| Outil | Version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3+ |
| Fabric API | 0.153.0+26.2 |
| Gradle | 9.5.1 |
| Fabric Loom | 1.17 |

## Commandes de développement

```bash
# Générer les sources Minecraft (à faire une fois après clonage)
./gradlew genSources

# Build
./gradlew build

# Lancer le client de test
./gradlew runClient

# Lancer le serveur de test
./gradlew runServer

# Build + tests
./gradlew test
```

## Spécificités Minecraft 26.2 à respecter

Ces points cassent la compatibilité avec les tutoriels pré-26.2 — ne pas les ignorer :

**Enregistrement des blocs/items** : Le système `valueLookupBuilder` n'existe plus. Les IDs doivent être enregistrés séparément dans des classes dédiées (`BlockIds`, `BlockItemIds`). Le lien Block ↔ BlockItem doit suivre les nouveaux standards Loom 1.17.

**HUD / Rendu client** : La classe `Hud` est maintenant séparée de `Minecraft`. Utiliser l'API Blaze3D via `Hud` (pas via `MinecraftClient`) pour afficher le timer, le statut des cubes et le nom de l'équipe gagnante — requis pour la compatibilité backend Vulkan.

## Architecture du mod

Le `CubeConquestGameManager` est le point central. Il s'abonne à `ServerTickEvents.END_SERVER_TICK` et pilote une machine d'états à trois phases :

1. **PREPARATION** — PvP bloqué (`AttackEntityCallback` → `ActionResult.FAIL`), un porteur par équipe reçoit le cube. Si le porteur meurt, le cube est retiré des drops et transféré à un autre joueur vivant de la même équipe. Si le countdown expire sans placement, le porteur est immobilisé (mixin sur `PlayerEntity.travel()` ou effet de potion personnalisé).

2. **PLACEMENT** — Le cube ne peut être posé que dans l'Overworld (`world.getRegistryKey() == World.OVERWORLD`). La protection du bloc passe par `PlayerBlockBreakEvents.BEFORE` : les membres de l'équipe propriétaire ne peuvent pas casser leur propre cube ; un casse par l'équipe adverse déclenche la victoire.

3. **COMBAT** — PvP autorisé. Chaque joueur reçoit une boussole personnalisée (`ItemProperties.register` côté client) dont l'angle pointe vers le cube adverse. Les coordonnées du cube sont synchronisées via Custom Payload Packet S2C.

**Persistance** : les données d'équipes sont stockées dans un `PersistentState` attaché au `ServerStateManager` (survit aux redémarrages).

**Commandes** : racine `/cubeconquest` enregistrée via `CommandRegistrationCallback`.

## Convention de commit

Commiter après chaque **grand changement** (nouvelle feature, refactor significatif, correction de bug majeur, milestone de phase).

Format :
```
<type>: <description courte>

<corps optionnel>
```

Types : `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

Exemples de seuils qui méritent un commit :
- Nouvelle phase du jeu fonctionnelle
- Nouveau système (boussole, persistance, packets…)
- Bug critique corrigé
- Refactor qui change l'architecture
