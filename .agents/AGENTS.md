# AGENTS.md

Ces règles guident le comportement et le contexte pour l'assistant Antigravity / Gemini lorsqu'il travaille sur ce projet. Elles priment sur les règles globales.

## Contexte du Projet

**Cube Conquest** — Mod multijoueur compétitif Fabric pour Minecraft **26.2** ("Chaos Cubed"). 
- **Principe** : Deux équipes (Rouge / Bleu) s'affrontent pour détruire le cube adverse. 
- **Règle clé** : Le cube ne peut être détruit que par l'équipe adverse.

## Environnement Cible

- **Minecraft** : 26.2
- **Fabric Loader** : 0.19.3+
- **Fabric API** : 0.153.0+26.2
- **Gradle** : 9.5.1
- **Fabric Loom** : 1.17

## Commandes de Développement

Utilise le terminal et ces commandes pour tester ou compiler le projet :
- Générer les sources : `./gradlew genSources`
- Compiler (Build) : `./gradlew build`
- Client de test : `./gradlew runClient`
- Serveur de test : `./gradlew runServer`
- Lancer les tests : `./gradlew test`

## Spécificités Minecraft 26.2 (CRITIQUE)

Il est indispensable de respecter ces contraintes liées à la version 26.2 :

1. **Enregistrement des blocs/items** : Le système `valueLookupBuilder` n'existe plus. 
   - Enregistrer les IDs dans des classes dédiées (`BlockIds`, `BlockItemIds`). 
   - Lier Block ↔ BlockItem selon les standards Loom 1.17.
2. **HUD / Rendu client** : La classe `Hud` est dissociée de `Minecraft`. 
   - Utiliser l'API Blaze3D **exclusivement via `Hud`** (et non `MinecraftClient`) pour le timer, l'état des cubes et la victoire. Requis pour le backend Vulkan.

## Architecture du Mod

- **Core** : `CubeConquestGameManager` est le contrôleur central (s'abonne à `ServerTickEvents.END_SERVER_TICK`).
- **Phases de jeu** (Machine d'états) :
  1. **PREPARATION** : PvP désactivé (`AttackEntityCallback` → `ActionResult.FAIL`). Un porteur par équipe détient le cube. En cas de décès, transfert à un coéquipier. Expiration du timer = immobilisation du porteur (`PlayerEntity.travel()` ou effet de potion).
  2. **PLACEMENT** : Par défaut, le cube doit être posé dans l'Overworld. Configurable via `/cubeconquest overworldOnly <true|false>`. Les joueurs ne peuvent pas casser leur propre cube (`PlayerBlockBreakEvents.BEFORE`).
  3. **COMBAT** : PvP activé. Boussole personnalisée (`ItemProperties.register`) pointant vers le cube adverse (coordonnées synchronisées par `Custom Payload Packet S2C`).
- **Sauvegarde/Persistance** : Utilisation de `PersistentState` lié au `ServerStateManager`.
- **Commandes** : L'API `CommandRegistrationCallback` gère la racine `/cubeconquest`.

## Conventions de Code et de Commit

- **Commits** : Après chaque milestone (feature, refactoring, fix de bug critique).
- **Sécurité (AVANT modification)** : Toujours faire un commit et push l'état actuel du code **avant** de commencer à modifier une feature existante, afin d'avoir une sauvegarde au cas où des erreurs seraient commises.
- **Format** : `<type>: <description courte>` suivi d'un corps optionnel. Types autorisés : `feat`, `fix`, `refactor`, `docs`, `test`, `chore`.
