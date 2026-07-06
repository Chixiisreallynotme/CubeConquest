---
name: ecc-question
description: Pose n'importe quelle question sur ECC (Enhanced Claude Code), Claude Code, l'ecosysteme des modeles Claude, ou l'IA generative au sens large. Repond avec des donnees verifiees et sourcees.
argument-hint: "Pose ta question sur ECC, Claude Code ou l'IA"
metadata:
  origin: custom
  scope: global
---

# ECC Question — Base de connaissances interactive

Skill interactif pour repondre a toute question sur :
- **ECC** (Enhanced Claude Code) — skills, commandes, agents, hooks, architecture, installation
- **Claude Code** — fonctionnalites, CLI, MCP, hooks, permissions, configuration
- **Modeles Claude** — Fable 5, Opus 4.8, Sonnet 4.6, Haiku 4.5, pricing, benchmarks
- **Ecosysteme IA** — Anthropic, OpenAI, Google DeepMind, Meta, Mistral, concepts fondamentaux

## Activation

Ce skill s'active UNIQUEMENT via la commande `/ecc-question`.
Ne pas activer automatiquement sur des questions generales — attendre l'invocation explicite.

## Comment repondre

### Principes

1. **Donnees verifiees uniquement** — Ne jamais inventer de chiffres ou benchmarks. Citer les sources.
2. **Consulter les fichiers knowledge/** — Lire les fichiers de reference avant de repondre.
3. **Distinguer les niveaux** — ECC vs Claude Code natif vs IA generale.
4. **Etre pratique** — Donner des exemples concrets, des commandes a taper.
5. **Admettre les limites** — Si une info est post-cutoff, suggerer une recherche web.

### Routage par type de question

| Type de question | Source de verite |
|------------------|-----------------|
| Commandes ECC (`/plan`, `/tdd`, etc.) | `knowledge/ecc-reference.md` + fichiers dans `~/.claude/commands/` |
| Skills ECC | `knowledge/ecc-reference.md` + fichiers dans `~/.claude/skills/ecc/` |
| Agents ECC | `knowledge/ecc-reference.md` + fichiers dans `~/.claude/agents/` |
| Hooks et automatisations | `knowledge/ecc-reference.md` + `~/.claude/settings.json` |
| Installation ECC | `knowledge/ecc-reference.md` |
| Claude Code (natif) | `knowledge/claude-code-reference.md` |
| Modeles Claude | `knowledge/ai-landscape.md` |
| IA generale | `knowledge/ai-landscape.md` + web search si necessaire |

### Format de reponse

Pour chaque reponse :
1. Repondre de maniere concise et directe
2. Donner un exemple pratique si pertinent
3. Indiquer la source (fichier, doc officielle, benchmark)
4. Proposer des questions de suivi pertinentes

## Fichiers de reference

Lire ces fichiers AVANT de repondre pour avoir des donnees a jour :

- `knowledge/ecc-reference.md` — Catalogue complet ECC (commandes, skills, agents, hooks)
- `knowledge/claude-code-reference.md` — Guide Claude Code (fonctionnalites, config, MCP)
- `knowledge/ai-landscape.md` — Ecosysteme IA (modeles, pricing, benchmarks, acteurs)

## Exemples d'interactions

**Q: "C'est quoi /plan ?"**
> `/plan` est une commande ECC qui cree un plan d'implementation complet avant d'ecrire du code. Elle analyse les risques, decompose en phases, et attend ta confirmation. Usage : `/plan Ajouter un systeme d'auth OAuth2`

**Q: "Quel modele Claude utiliser pour coder ?"**
> Pour le coding quotidien : Sonnet 4.6 ($3/$15 par MTok, 69.2% SWE-bench Pro). Pour les taches complexes et longues : Opus 4.8 ($5/$25, 1M context). Pour les cas les plus durs : Fable 5 ($10/$50, 80.3% SWE-bench Pro).

**Q: "Comment marche MCP dans Claude Code ?"**
> MCP (Model Context Protocol) est un standard ouvert pour connecter Claude Code a des services externes (GitHub, Slack, Jira, Google Drive). Configure dans `~/.claude/settings.json` sous `mcpServers`. Exemple : `claude mcp add github -- npx @anthropic-ai/mcp-server-github`

**Q: "C'est quoi un hook dans ECC ?"**
> Les hooks sont des scripts qui s'executent automatiquement a certains moments du cycle de vie de Claude Code (avant/apres edition, debut/fin de session, etc.). Configures dans `settings.json` sous `hooks`. ECC en installe ~15 par defaut pour la memoire, le routage, et les quality gates.
