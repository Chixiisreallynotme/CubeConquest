# Claude Code — Reference Complete

## Vue d'ensemble

**Claude Code** est l'outil de codage agentique officiel d'Anthropic. Il lit le codebase, edite des fichiers, execute des commandes, et s'integre avec les outils de developpement. Disponible en terminal, IDE, desktop, et navigateur.

- **Editeur** : Anthropic
- **Type** : CLI agentique + extensions IDE + desktop + web
- **Modele par defaut** : Claude Sonnet 4.6 (configurable)
- **Version actuelle** : v2.1.x (mars 2026+)
- **Platforms** : macOS, Linux, Windows
- **Install** : `npm install -g @anthropic-ai/claude-code` ou installeur natif
- **Auth** : Browser OAuth (claude.ai) ou API key
- **Site** : code.claude.com

## Surfaces disponibles

| Surface | Description |
|---------|-------------|
| **CLI** (`claude`) | Reference — terminal complet |
| **VS Code Extension** | Sidebar avec diffs inline temps reel |
| **JetBrains Plugin** | Integration native avec selection context bridging |
| **Desktop App** | Sessions paralleles, diffs visuels, taches recurrentes |
| **Web** (`claude.ai/code`) | Sans setup local, taches longues, sessions paralleles |
| **Claude Agent SDK** | Construire ses propres agents avec les tools Claude Code |
| **GitHub Action** | CI/CD integration |
| **GitHub App** | Integration repo-level |

## Outils integres (Built-in Tools)

### Fichiers
| Outil | Fonction |
|-------|----------|
| `Read` | Lire fichiers (texte, images, PDFs) |
| `Edit` | Editer fichiers existants (remplacement exact) |
| `Write` | Creer/ecraser fichiers |
| `Glob` | Trouver fichiers par pattern |
| `Grep` | Chercher dans le contenu (regex) |
| `NotebookEdit` | Editer cellules Jupyter |

### Execution
| Outil | Fonction |
|-------|----------|
| `Bash` | Executer n'importe quelle commande shell |
| `WebSearch` | Recherche web |
| `WebFetch` | Recuperer contenu d'une URL |

### Coordination
| Outil | Fonction |
|-------|----------|
| `Task` / `Agent` | Lancer des sous-agents |
| `AskUserQuestion` | Poser des questions clarificatrices |
| `TaskCreate/Update/List/Get` | Gestion de todos |
| `SendMessage` | Communication entre agents |
| `ToolSearch` | Decouverte dynamique d'outils |

### Avances
| Outil | Fonction |
|-------|----------|
| `EnterPlanMode` | Entrer en mode plan |
| `ExitPlanMode` | Sortir du mode plan |
| `EnterWorktree` | Isolation worktree git |
| `ExitWorktree` | Sortir du worktree |
| `Workflow` | Orchestration multi-agents deterministe |

## CLAUDE.md — Fichier de configuration projet

Fichier markdown a la racine du projet lu au debut de chaque session. Contient :
- Standards de code
- Decisions architecturales
- Libraries preferees
- Checklists de review
- Commandes de build/test
- Preferences de l'equipe

**Niveaux** :
- `~/.claude/CLAUDE.md` — Global (toutes sessions)
- `./CLAUDE.md` — Projet (racine du repo)
- `./.claude/CLAUDE.md` — Projet (dans .claude/)

## Commandes slash natives

| Commande | Description |
|----------|-------------|
| `/help` | Commandes disponibles |
| `/clear` | Effacer le contexte |
| `/compact` | Compresser le contexte pour economiser des tokens |
| `/model [nom]` | Changer de modele mid-session |
| `/cost` | Cout en tokens de la session |
| `/status` | Info session |
| `/voice` | Mode vocal (20 langues) |
| `/loop` | Taches recurrentes |
| `/fast` | Mode rapide (2.5x vitesse sur Opus) |
| `/rewind` | Revenir a un checkpoint |
| `/mcp enable/disable` | Activer/desactiver MCP |
| `/add-dir` | Ajouter un repertoire au contexte |

## Hooks — Automatisations lifecycle

24 evenements disponibles :

| Event | Quand |
|-------|-------|
| `PreToolUse` | Avant utilisation d'un outil |
| `PostToolUse` | Apres utilisation d'un outil |
| `PostToolUseFailure` | Apres echec d'un outil |
| `UserPromptSubmit` | Quand l'utilisateur envoie un message |
| `SessionStart` | Debut de session |
| `SessionEnd` | Fin de session |
| `Stop` | Quand Claude s'arrete |
| `StopFailure` | Quand l'arret echoue |
| `PreCompact` | Avant compression contexte |
| `PostCompact` | Apres compression |
| `SubagentStart` | Lancement sous-agent |
| `SubagentStop` | Fin sous-agent |
| `Notification` | Notification |
| `CwdChanged` | Changement de repertoire |
| `FileChanged` | Fichier modifie |

**Format** (dans settings.json) :
```json
{
  "hooks": {
    "PreToolUse": [{
      "matcher": "Bash",
      "hooks": [{
        "type": "command",
        "command": "node script.js",
        "timeout": 5000
      }]
    }]
  }
}
```

## MCP (Model Context Protocol)

Standard ouvert pour connecter Claude Code a des services externes.

**Configuration** : `~/.claude/settings.json` sous `mcpServers` ou via CLI.

```bash
# Ajouter un serveur MCP
claude mcp add <nom> -- <commande>

# Exemples
claude mcp add github -- npx @anthropic-ai/mcp-server-github
claude mcp add filesystem -- npx @anthropic-ai/mcp-server-filesystem
```

**Serveurs populaires** : GitHub, Slack, Jira, Google Drive, Supabase, Vercel, Playwright, Context7, Exa, Filesystem.

## Permissions et securite

**Modes** :
| Mode | Description |
|------|-------------|
| `default` | Demande permission pour tout |
| `auto` | Classifieurs distinguent safe/risky |
| `bypassPermissions` | Tout autoriser (dangereux) |
| `plan` | Mode plan, ne modifie rien sans approbation |

**Configuration** (settings.json) :
```json
{
  "permissions": {
    "allow": [
      "Bash(npm test)",
      "Bash(git *)",
      "mcp__github__*"
    ]
  }
}
```

## Sous-agents et parallelisme

- **Task/Agent tool** : Lance des sous-agents specialises
- **Agent Teams** : Multi-agents paralleles coordonnes
- **Background tasks** (`claude --bg`) : Sessions en arriere-plan
- **Worktree isolation** : Chaque agent dans un worktree git separe
- **Workflows** : Orchestration deterministe multi-agents

## Modeles disponibles dans Claude Code

| Alias | Modele | Usage |
|-------|--------|-------|
| `fable` | Claude Fable 5 | Taches les plus complexes |
| `opus` | Claude Opus 4.8 | Raisonnement complexe, coding long |
| `sonnet` | Claude Sonnet 4.6 | Defaut — equilibre vitesse/intelligence |
| `haiku` | Claude Haiku 4.5 | Rapide, economique |

Changer : `/model opus` ou `claude --model opus`

## Fonctionnalites cles (v2.1.x, 2026)

| Feature | Description |
|---------|-------------|
| **1M Context Window** | Opus 4.6+ supporte 1M tokens |
| **128K Output** | Max output double sur Opus |
| **Fast Mode** | 2.5x plus rapide sur Opus |
| **Voice Mode** | Input vocal, 20 langues |
| **Checkpoints** | Sauvegarde auto avant chaque changement, `/rewind` pour revenir |
| **Agent Teams** | Coordination multi-agents native |
| **Skills** | Chargement dynamique de competences |
| **Plugins** | Marketplace de plugins |
| **Computer Use** | Controle desktop a distance (research preview) |
| **Auto Memory** | Apprentissage automatique inter-sessions |
| **Session Forking** | Brancher conversations en parallele |
| **24 Hook Events** | Automatisations lifecycle completes |
| **Worktree Isolation** | Git worktrees pour agents paralleles |
| **Tool Search** | Decouverte dynamique d'outils (1864+ serveurs MCP) |
| **Ultrathink** | Keyword pour max effort thinking |

## Settings.json — Configuration complete

Fichier principal : `~/.claude/settings.json`

```json
{
  "model": "sonnet",           // Modele par defaut
  "effortLevel": "high",      // low/medium/high/xhigh/max
  "permissions": { "allow": [] },
  "hooks": { ... },
  "env": { ... },
  "enabledPlugins": { ... },
  "skillOverrides": { ... },
  "mcpServers": { ... },
  "autoUpdatesChannel": "latest"
}
```

## CLI — Commandes principales

```bash
claude                          # Demarrer session interactive
claude "prompt"                 # Session avec prompt initial
claude -p "prompt"              # Print mode (non-interactif)
claude --bg "tache"             # Background agent
claude --model opus             # Specifier modele
claude --resume nom             # Reprendre session
claude agents                   # Vue agents paralleles
claude mcp add <nom> -- <cmd>   # Ajouter MCP server
claude auth login               # Authentification
claude config set key value     # Configuration
```

## Cout typique

- Usage quotidien leger : $5-15/mois
- Usage quotidien intense : $10-50/mois
- Facturation : tokens API Anthropic standard
- Pas d'abonnement separe pour Claude Code
