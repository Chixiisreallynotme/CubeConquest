# ECC (Enhanced Claude Code) — Reference Complete

## Vue d'ensemble

**ECC** (anciennement "Everything Claude Code") est un systeme d'exploitation pour agents IA cree par **Affaan Mustafa**. C'est le plugin Claude Code le plus populaire avec 210K+ stars sur GitHub.

- **Repo** : github.com/affaan-m/ECC
- **npm** : `ecc-universal`
- **Plugin** : `ecc@ecc`
- **Site** : ecc.tools
- **Version** : 2.0.0
- **Licence** : MIT
- **Pricing** : OSS gratuit / Pro $19/seat/mo (repos prives)
- **Harnesses supportes** : Claude Code, Codex, OpenCode, Cursor, Gemini, Zed, GitHub Copilot

## Installation

### Via Plugin (recommande)
```bash
/plugin marketplace add https://github.com/affaan-m/ECC
/plugin install ecc@ecc
```

### Via npm
```bash
npm i -g ecc-universal
```

### Via installer (profils selectifs)
```bash
git clone https://github.com/affaan-m/ECC.git
cd ECC && npm install
node scripts/install-apply.js --target claude --profile full
```

**Profils disponibles** :
| Profil | Description |
|--------|-------------|
| `minimal` | Setup low-context, pas de hooks runtime |
| `core` | Baseline avec commandes, hooks, configs |
| `developer` | Profil par defaut pour les devs |
| `security` | Focus securite |
| `research` | Focus recherche et contenu |
| `full` | Installation complete (819 fichiers) |

---

## Commandes Slash (~180 total)

### Planification
| Commande | Description |
|----------|-------------|
| `/plan [desc]` | Plan d'implementation complet, attend confirmation |
| `/plan-prd [sujet]` | Genere un PRD lean |
| `/feature-dev [feature]` | Developpement guide de feature |
| `/project-init` | Detection du stack et onboarding ECC |

### Developpement & Build
| Commande | Description |
|----------|-------------|
| `/build-fix` | Detecte et corrige les erreurs de build |
| `/react-build` | Fix React/Next.js |
| `/go-build` | Fix Go |
| `/rust-build` | Fix Rust |
| `/cpp-build` | Fix C++ |
| `/kotlin-build` | Fix Kotlin |
| `/flutter-build` | Fix Flutter/Dart |
| `/gradle-build` | Fix Gradle/Android |

### Code Review
| Commande | Description |
|----------|-------------|
| `/code-review` | Review local (diff non commite) |
| `/code-review 42` | Review PR #42 |
| `/code-review URL` | Review via URL |
| `/react-review` | Review React |
| `/python-review` | Review Python |
| `/rust-review` | Review Rust |
| `/go-review` | Review Go |
| `/vue-review` | Review Vue.js |
| `/fastapi-review` | Review FastAPI |
| `/cpp-review` | Review C++ |
| `/kotlin-review` | Review Kotlin |
| `/flutter-review` | Review Flutter |

### Tests (TDD)
| Commande | Description |
|----------|-------------|
| `/react-test` | TDD React |
| `/go-test` | TDD Go (table-driven) |
| `/rust-test` | TDD Rust |
| `/cpp-test` | TDD C++ (GoogleTest) |
| `/kotlin-test` | TDD Kotlin (Kotest) |
| `/flutter-test` | TDD Flutter |
| `/test-coverage` | Analyse couverture |

### Git & PRs
| Commande | Description |
|----------|-------------|
| `/pr` | Cree une PR (detect template, analyse, push) |
| `/prp-commit [desc]` | Commit en langage naturel |
| `/prp-plan` | Plan PRP complet |
| `/prp-implement` | Execute plan avec validation |
| `/prp-pr` | PR finale du workflow PRP |
| `/review-pr` | Review une PR existante |

### Securite & Qualite
| Commande | Description |
|----------|-------------|
| `/security-scan` | Scan vulnerabilites complet |
| `/quality-gate` | Verification qualite avant merge |
| `/refactor-clean` | Nettoyage code mort |

### Sessions & Memoire
| Commande | Description |
|----------|-------------|
| `/save-session` | Sauvegarde contexte |
| `/resume-session` | Restaure session |
| `/sessions` | Liste sessions |
| `/learn` | Extrait patterns reutilisables |
| `/learn-eval` | Learn avec auto-evaluation |
| `/checkpoint` | Checkpoint de workflow |

### Orchestration Multi-Agents
| Commande | Description |
|----------|-------------|
| `/orch-add-feature` | Feature end-to-end (research -> plan -> TDD -> review -> commit) |
| `/orch-build-mvp` | Bootstrap MVP depuis spec |
| `/orch-fix-defect` | Fix bug (reproduce -> fix -> review -> commit) |
| `/orch-refine-code` | Refactor preservant le comportement |
| `/orch-change-feature` | Modifie feature existante |
| `/multi-workflow` | Workflow multi-model complet |
| `/multi-plan` | Plan multi-model sans modifier le code |
| `/multi-execute` | Execute un plan multi-model |

### SPARC (sous-dossier sparc/)
| Commande | Description |
|----------|-------------|
| `/sparc/ask` | Question technique |
| `/sparc/architect` | Phase Architecture |
| `/sparc/code` | Phase Implementation |
| `/sparc/debug` | Phase Debug |
| `/sparc/tester` | Phase Tests |
| `/sparc/reviewer` | Phase Review |
| `/sparc/devops` | Phase DevOps/CI |
| `/sparc/security-review` | Audit securite |
| `/sparc/batch-executor` | Execution en batch |

### GitHub Integration (sous-dossier github/)
| Commande | Description |
|----------|-------------|
| `/github/issue-tracker` | Gestion issues |
| `/github/issue-triage` | Triage auto |
| `/github/pr-manager` | Cycle de vie PRs |
| `/github/code-review` | Review via GitHub |
| `/github/release-manager` | Coordination releases |
| `/github/multi-repo-swarm` | Swarm multi-repo |

### Monitoring (sous-dossier monitoring/)
| Commande | Description |
|----------|-------------|
| `/monitoring/status` | Statut agents |
| `/monitoring/swarm-monitor` | Monitoring swarm |
| `/monitoring/agents` | Vue agents |

### Analyse (sous-dossier analysis/)
| Commande | Description |
|----------|-------------|
| `/analysis/token-usage` | Rapport tokens |
| `/analysis/bottleneck-detect` | Goulots d'etranglement |
| `/analysis/token-efficiency` | Efficacite tokens |

### Autres
| Commande | Description |
|----------|-------------|
| `/ecc-guide` | Navigation dans ECC |
| `/cost-report` | Rapport de couts |
| `/model-route` | Recommandation de model |
| `/auto-update` | Mise a jour ECC |
| `/hookify` | Creer des hooks depuis l'analyse de conversation |
| `/aside [question]` | Question rapide sans perdre le contexte |
| `/loop-start` | Boucle autonome avec conditions d'arret |
| `/marketing-campaign` | Campagne marketing complete |
| `/skill-create` | Generer un skill depuis git history |
| `/pm2` | Generer commandes PM2 |
| `/jira` | Integration Jira |

---

## Agents (90 total)

### Principaux
| Agent | Role |
|-------|------|
| `planner` | Planification d'implementation |
| `architect` | Design systeme et scalabilite |
| `code-reviewer` | Qualite, securite, maintenabilite |
| `tdd-guide` | TDD strict (RED/GREEN/REFACTOR) |
| `security-reviewer` | Detection vulnerabilites OWASP |
| `build-error-resolver` | Correction erreurs build |
| `e2e-runner` | Tests E2E Playwright |
| `refactor-cleaner` | Nettoyage code mort |
| `doc-updater` | Documentation et codemaps |
| `database-reviewer` | PostgreSQL/Supabase |
| `marketing-agent` | Strategie et copywriting |
| `chief-of-staff` | Triage communications multi-canal |

### Par langage
| Agent | Langage |
|-------|---------|
| `react-reviewer` | React/JSX/TSX |
| `typescript-reviewer` | TypeScript/JavaScript |
| `python-reviewer` | Python |
| `rust-reviewer` | Rust |
| `go-reviewer` | Go |
| `java-reviewer` | Java (Spring/Quarkus) |
| `kotlin-reviewer` | Kotlin/Android |
| `swift-reviewer` | Swift |
| `cpp-reviewer` | C++ |
| `vue-reviewer` | Vue.js |
| `flutter-reviewer` | Flutter/Dart |
| `php-reviewer` | PHP |
| `csharp-reviewer` | C# |
| `fsharp-reviewer` | F# |
| `django-reviewer` | Django |

### Build resolvers
| Agent | Build |
|-------|-------|
| `react-build-resolver` | React/Vite/Next.js/webpack |
| `go-build-resolver` | Go |
| `rust-build-resolver` | Rust/Cargo |
| `cpp-build-resolver` | C++/CMake |
| `kotlin-build-resolver` | Kotlin/Gradle |
| `dart-build-resolver` | Flutter/Dart |
| `swift-build-resolver` | Swift/Xcode |
| `java-build-resolver` | Java/Maven/Gradle |
| `django-build-resolver` | Django/Python |
| `pytorch-build-resolver` | PyTorch/CUDA |

### Specialises
| Agent | Specialite |
|-------|-----------|
| `gan-planner` | GAN Harness - Planification |
| `gan-generator` | GAN Harness - Implementation |
| `gan-evaluator` | GAN Harness - Evaluation Playwright |
| `a11y-architect` | Accessibilite WCAG 2.2 |
| `seo-specialist` | SEO technique |
| `mle-reviewer` | Machine Learning Ops |
| `network-architect` | Architecture reseau |
| `homelab-architect` | Home/lab networking |
| `healthcare-reviewer` | Sante/CDSS/PHI |
| `spec-miner` | Extraction specs depuis code |
| `opensource-forker` | Fork open-source |
| `opensource-sanitizer` | Scan secrets avant release |

---

## Skills (198 dans ecc/)

### Categories principales
- **Coding** : tdd-workflow, continuous-learning, code-quality-gates
- **Architecture** : api-design, microservices, domain-driven-design
- **Security** : security-hardening, vulnerability-scanning, threat-modeling
- **DevOps** : ci-cd-pipeline, kubernetes, docker-optimization
- **Frontend** : react-patterns, vue-patterns, angular-developer
- **Mobile** : android-clean-architecture, flutter, react-native
- **ML/AI** : ml-pipelines, model-training, agentic-engineering
- **Testing** : e2e-testing, performance-testing, integration-testing
- **Research** : search-first, deep-research, research-synthesis
- **Business** : marketing-campaign, brand-voice, content-strategy
- **Orchestration** : swarm-coordination, multi-agent, autonomous-loops

---

## Hooks (automatisations)

### Events configures dans settings.json
| Event | Nombre de hooks | Action |
|-------|----------------|--------|
| PreToolUse (Bash) | 1 | Validation avant commande |
| PreToolUse (Write/Edit) | 1 | Validation avant edition |
| PostToolUse (Write/Edit) | 1 | Verification apres edition |
| PostToolUse (Bash) | 1 | Verification apres commande |
| UserPromptSubmit | 1 | Routage intelligent |
| SessionStart | 2 | Restore contexte + import memoire |
| SessionEnd | 1 | Sauvegarde session |
| Stop | 1 | Sync memoire |
| PreCompact (manual) | 2 | Sauvegarde avant compression |
| PreCompact (auto) | 2 | Sauvegarde avant auto-compression |
| SubagentStart | 1 | Status des agents |
| SubagentStop | 1 | Post-traitement tache |
| Notification | 1 | Handler notifications |

### Scripts helpers (41 fichiers)
- `hook-handler.cjs` — Dispatcher principal
- `auto-memory-hook.mjs` — Import/sync memoire automatique
- `intelligence.cjs` — Pattern recognition et suggestions
- `memory.js` — Gestion memoire persistante
- `metrics-db.mjs` — Base de donnees de metriques
- `learning-service.mjs` — Service d'apprentissage continu
- `github-safe.js` — Guard pour operations GitHub

---

## Rules (22 repertoires)

Regles par langage/framework couvrant :
- Coding style
- Patterns architecturaux
- Securite
- Testing
- Hooks

**Langages** : Angular, ArkTS, C++, C#, Dart, F#, Go, Java, Kotlin, Nuxt, Perl, PHP, Python, React, Ruby, Rust, Swift, TypeScript, Vue, Web + Common (transversal)

---

## Architecture interne

```
~/.claude/
├── agents/          (90 agents .md + dossiers)
├── commands/        (~180 commandes slash)
│   ├── sparc/       (32 commandes SPARC)
│   ├── github/      (19 commandes GitHub)
│   ├── monitoring/  (6 commandes)
│   ├── analysis/    (7 commandes)
│   ├── optimization/ (6 commandes)
│   ├── automation/  (7 commandes)
│   └── hooks/       (8 commandes)
├── helpers/         (41 scripts hook)
├── hooks/           (hooks.json plugin)
├── rules/ecc/       (22 rulesets)
├── scripts/         (runtime ECC)
├── skills/ecc/      (198 skills)
├── ecc/             (install-state.json)
├── AGENTS.md        (guide delegation)
├── settings.json    (config principale)
└── mcp-configs/     (32 MCP servers)
```

---

## Workflows typiques

### 1. Nouvelle feature (complet)
```
/plan [description]          → Confirmer
/orch-add-feature            → Orchestration automatique
```

### 2. Fix bug
```
/orch-fix-defect [description du bug]
```

### 3. MVP depuis zero
```
/plan-prd [description produit]  → Confirmer PRD
/orch-build-mvp                   → Bootstrap complet
```

### 4. Review avant merge
```
/code-review
/security-scan
/quality-gate
```

### 5. Refactoring safe
```
/orch-refine-code [ce qu'on veut refactorer]
```

### 6. Multi-model (utiliser plusieurs LLM)
```
/multi-plan [description]     → Plan sans modifier le code
/multi-execute                → Execution avec validation
```
