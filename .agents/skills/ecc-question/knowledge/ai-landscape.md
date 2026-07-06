# Ecosysteme IA — Reference Verifiee (juin 2026)

> Sources : docs officielles Anthropic, OpenAI, Google DeepMind, Meta. Benchmarks publics.
> Derniere verification : juin 2026.

---

## 1. Modeles Claude (Anthropic)

### Lineup actuel (juin 2026)

| Modele | API ID | Input $/MTok | Output $/MTok | Context | Max Output | Date |
|--------|--------|-------------|---------------|---------|------------|------|
| **Claude Fable 5** | `claude-fable-5` | $10 | $50 | 1M | 128K | Juin 2026 |
| **Claude Mythos 5** | `claude-mythos-5` | $10 | $50 | 1M | 128K | Juin 2026 (restreint) |
| **Claude Opus 4.8** | `claude-opus-4-8` | $5 | $25 | 1M | 128K | Mai 2026 |
| **Claude Opus 4.7** | `claude-opus-4-7` | $5 | $25 | 1M | 128K | Avril 2026 |
| **Claude Sonnet 4.6** | `claude-sonnet-4-6` | $3 | $15 | 1M | 64K | Fev 2026 |
| **Claude Haiku 4.5** | `claude-haiku-4-5-20251001` | $1 | $5 | 200K | 64K | Oct 2025 |

### Tiers de modeles

| Tier | Modeles | Usage |
|------|---------|-------|
| **Mythos** (nouveau) | Fable 5, Mythos 5 | Le plus haut tier, au-dessus d'Opus |
| **Opus** | 4.8, 4.7, 4.6, 4.5, 4.1, 4, 3 | Raisonnement complexe, agents long-horizon |
| **Sonnet** | 4.6, 4.5, 4, 3.7, 3.5, 3 | Equilibre vitesse/intelligence |
| **Haiku** | 4.5, 3.5, 3 | Rapide, economique, haut volume |

### Benchmarks (sources : blog Anthropic, papiers)

| Benchmark | Fable 5 | Opus 4.8 | Sonnet 4.6 | Haiku 4.5 |
|-----------|---------|----------|------------|-----------|
| SWE-bench Verified | ~85%+ | 88.6% | ~80% | — |
| SWE-bench Pro | 80.3% | 69.2% | 69.2% | — |
| GPQA Diamond | — | 93.6% | — | — |
| Senior Engineer (interne) | 91/100 | ~63/100 | — | — |

### Claude Fable 5 — Details

- Premier modele "Mythos-class" disponible publiquement
- Meme modele sous-jacent que Mythos 5, avec des safeguards supplementaires
- Safeguards : sur des sujets cybersecurite, biologie, chimie, distillation → fallback auto vers Opus 4.8
- <5% des sessions declenchent un fallback
- State-of-the-art sur : coding, vision, memoire longue, recherche scientifique
- Vision : reconstruit le code source d'une app web depuis des screenshots
- Memoire : reste focus sur des millions de tokens dans des taches longues
- **Project Glasswing** : programme pour acceder a Mythos 5 sans safeguards (invite-only)

### Claude Opus 4.8 — Details

- Modele le plus deploye pour le travail serieux (juin 2026)
- 4x moins de defauts de code que Opus 4.7
- Dynamic Workflows : centaines de sous-agents paralleles
- Fast Mode : 2.5x vitesse, $10/$50 par MTok en fast
- Browser agent state-of-the-art (84% Online-Mind2Web)
- Supporte Zero Data Retention (ZDR)
- Effort control : choisir combien d'effort Claude met dans une tache

### Claude Sonnet 4.6 — Details

- Best value : intelligence Opus-level au prix Sonnet ($3/$15)
- Prefere a Sonnet 4.5 par 70% des devs, a Opus 4.5 par 59%
- 1M context window
- Gere 90%+ des taches de coding sans compromis
- Defaut recommande pour la plupart des equipes

### Timeline complete

| Date | Modele | Innovation cle |
|------|--------|---------------|
| Mars 2023 | Claude 1 | Premier modele |
| Juillet 2023 | Claude 2 | Ameliorations generales |
| Nov 2023 | Claude 2.1 | 200K context |
| Mars 2024 | Claude 3 (Opus/Sonnet/Haiku) | Famille a 3 tiers, multimodal |
| Juin 2024 | Claude 3.5 Sonnet | Percee coding |
| Oct 2024 | Claude 3.5 Sonnet v2 | Ameliore |
| Fev 2025 | Claude 3.7 Sonnet | Extended thinking |
| Mai 2025 | Claude 4 (Opus + Sonnet) | Generation 4, Claude Code GA |
| Aout 2025 | Claude Opus 4.1 | Ameliorations |
| Sep 2025 | Claude Sonnet 4.5 | Surpasse Opus sur benchmarks |
| Oct 2025 | Claude Haiku 4.5 | Extended thinking + Computer Use |
| Nov 2025 | Claude Opus 4.5 | 80.9% SWE-bench Verified |
| Fev 2026 | Claude Opus 4.6 + Sonnet 4.6 | 1M context, Agent Teams |
| Avril 2026 | Claude Opus 4.7 | Ameliorations |
| Mai 2026 | Claude Opus 4.8 | Dynamic Workflows, 4x moins de bugs |
| Juin 2026 | Claude Fable 5 + Mythos 5 | Tier Mythos, SOTA general |

---

## 2. Anthropic — L'entreprise

- **Fondee** : 2021 par Dario Amodei (CEO) et Daniela Amodei (President)
- **Fondateurs** : ex-chercheurs OpenAI (VP Research)
- **Mission** : Securite de l'IA, construire des systemes IA fiables
- **Approche** : Constitutional AI (RLHF + principes constitutionnels)
- **Produits** : Claude (chat), Claude Code (coding), Claude API, Claude Platform
- **Siege** : San Francisco, CA
- **Recherche** : Alignment, interpretabilite, safety benchmarks
- **Concepts cles** :
  - **Constitutional AI** : L'IA s'auto-corrige selon des principes ecrits
  - **RLHF** : Reinforcement Learning from Human Feedback
  - **Responsible Scaling Policy** : Augmentation progressive des capacites avec safety gates

---

## 3. Ecosysteme IA — Autres acteurs majeurs

### OpenAI

| Modele | Pricing approx. | Context | Notes |
|--------|-----------------|---------|-------|
| GPT-4o | $2.5/$10 MTok | 128K | Multimodal, rapide |
| GPT-4o mini | $0.15/$0.60 MTok | 128K | Economique |
| o1 | $15/$60 MTok | 200K | Raisonnement chaine |
| o1-mini | $3/$12 MTok | 128K | Raisonnement economique |
| o3 | Variable | — | Dernier modele raisonnement (2025+) |

- **Produits** : ChatGPT, API, Codex (deprecated), DALL-E, Whisper, Sora
- **CEO** : Sam Altman
- **Fondee** : 2015 (non-profit → capped-profit)

### Google DeepMind

| Modele | Notes |
|--------|-------|
| Gemini 2.5 Pro | Modele flagship, 1M context, multimodal |
| Gemini 2.5 Flash | Rapide, economique |
| Gemini 2.0 | Predecesseur |
| Gemma (open) | Modeles open-weight |

- **Produits** : Google AI Studio, Vertex AI, Gemini App, NotebookLM
- **CEO DeepMind** : Demis Hassabis (Nobel 2024 avec AlphaFold)
- **Recherche** : AlphaFold, AlphaGo, Gemini, multimodalite

### Meta AI

| Modele | Notes |
|--------|-------|
| Llama 3.1 405B | Plus grand modele open-weight |
| Llama 3.1 70B | Equilibre open-weight |
| Llama 3.1 8B | Leger, edge |
| Llama 4 | Annonce 2025-2026, MoE |

- **Approche** : Open-weight (pas fully open-source, licence Meta)
- **CEO** : Mark Zuckerberg
- **Recherche** : FAIR (Fundamental AI Research), Yann LeCun (Chief AI Scientist)

### Mistral AI

| Modele | Notes |
|--------|-------|
| Mistral Large | Flagship |
| Mistral Medium | Equilibre |
| Mistral Small | Economique |
| Mixtral 8x7B | MoE open-weight |
| Codestral | Specialise code |

- **Fondee** : 2023, Paris
- **Fondateurs** : Ex-Meta (Arthur Mensch) et ex-DeepMind
- **Approche** : Open-weight + API

### Autres acteurs notables

| Entreprise | Modeles | Specialite |
|------------|---------|-----------|
| **Cohere** | Command R+ | Enterprise RAG |
| **Databricks** | DBRX | Open-weight enterprise |
| **xAI** (Elon Musk) | Grok | Integration X/Twitter |
| **DeepSeek** (Chine) | DeepSeek V2/V3 | Performance/cout |
| **Qwen** (Alibaba) | Qwen 2.5 | Multilingue, open |
| **01.AI** (Yi) | Yi-Large | Open, chinois |

---

## 4. Concepts fondamentaux de l'IA generative

### Architecture

| Concept | Definition |
|---------|-----------|
| **Transformer** | Architecture neuronale basee sur l'attention (Vaswani et al., 2017, Google) |
| **LLM** | Large Language Model — modele de langage a grande echelle |
| **Token** | Unite de texte (~0.75 mot en anglais, ~0.5 mot en francais) |
| **Context Window** | Nombre max de tokens en entree + sortie |
| **Attention** | Mecanisme qui pondere l'importance relative des tokens |
| **MoE** | Mixture of Experts — active seulement une partie du modele par token |

### Entrainement

| Concept | Definition |
|---------|-----------|
| **Pre-training** | Entrainement sur d'enormes corpus de texte (prediction du token suivant) |
| **Fine-tuning** | Adaptation a une tache specifique |
| **RLHF** | Reinforcement Learning from Human Feedback — alignement avec preferences humaines |
| **DPO** | Direct Preference Optimization — alternative plus simple a RLHF |
| **Constitutional AI** | Auto-correction selon des principes ecrits (Anthropic) |
| **Distillation** | Transfert de connaissances d'un gros modele vers un petit |

### Inference et usage

| Concept | Definition |
|---------|-----------|
| **Prompting** | Art de formuler des instructions pour le modele |
| **Chain-of-Thought** | Raisonnement etape par etape |
| **Extended Thinking** | Tokens de reflexion internes avant la reponse |
| **RAG** | Retrieval-Augmented Generation — enrichir avec des sources externes |
| **Function/Tool Calling** | Le modele appelle des fonctions/outils externes |
| **Agentic** | Systeme qui agit vers un objectif avec autonomie |
| **MCP** | Model Context Protocol — standard ouvert pour connecter IA a des services |

### Securite et alignment

| Concept | Definition |
|---------|-----------|
| **Alignment** | S'assurer que l'IA agit selon les intentions humaines |
| **Red-teaming** | Tests adverses pour trouver des failles |
| **Guardrails** | Barrieres de securite empechant les outputs dangereux |
| **Hallucination** | L'IA genere des informations fausses presentees comme vraies |
| **Jailbreak** | Technique pour contourner les restrictions du modele |
| **Prompt Injection** | Injection d'instructions malveillantes dans le contexte |

---

## 5. Benchmarks de reference

| Benchmark | Mesure |
|-----------|--------|
| **SWE-bench Verified** | Resolution de vraies issues GitHub (software engineering) |
| **SWE-bench Pro** | Version plus difficile de SWE-bench |
| **GPQA Diamond** | Questions de niveau PhD en sciences |
| **MMLU** | Comprehension et connaissances generales (57 domaines) |
| **HumanEval** | Generation de code Python |
| **MATH** | Problemes mathematiques |
| **ARC-AGI** | Raisonnement abstrait (Francois Chollet) |
| **FrontierCode** | Coding frontier (specifique a Claude) |

---

## 6. Tendances 2025-2026

1. **Agents autonomes** — Les modeles travaillent sur des taches pendant des heures sans supervision
2. **Fenetre de contexte 1M+** — Plus besoin de chunking pour la plupart des projets
3. **MCP comme standard** — Protocol unifie pour les integrations
4. **Multi-agents** — Equipes d'agents specialises coordonnes
5. **Code comme interface** — CLI et API plutot que GUI pour les devs
6. **Open-weight vs closed** — Meta (Llama) et Mistral vs Anthropic/OpenAI/Google
7. **Safety scaling** — Plus un modele est puissant, plus il a de safeguards
8. **Computer Use** — Modeles qui controlent des interfaces graphiques
9. **Voice et multimodal** — Input/output audio, image, video integres
10. **Cost reduction** — Baisse continue des prix (Haiku: $1/$5 vs GPT-4 $30/$60 il y a 2 ans)
