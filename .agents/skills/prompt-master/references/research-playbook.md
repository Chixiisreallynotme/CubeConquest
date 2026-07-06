# Research Playbook Reference

Detailed research methodology for Prompt Master v3. Read this file when you need the full research protocol, context engineering principles, compaction strategies, or token optimization techniques.

---

## Research Protocol

### When to Research

| Trigger | Action |
|---------|--------|
| User names a model not in the routing table | Search for `[model name] prompt engineering best practices 2025 2026` |
| User asks about a specific framework (DSPy, TextGrad) | Use Context7 to fetch framework documentation |
| User mentions "latest techniques" or "state of the art" | Search for latest prompt engineering research papers and playbooks |
| User's task involves structured outputs or function calling | Search for `[model name] structured output JSON schema 2025 2026` |
| User asks about prompt caching or token optimization | Search for `[model name] API prompt caching token optimization` |

### Research Tool Routing

```
1. Context7 (resolve-library-id → query-docs)
   └── Best for: Framework docs (DSPy, LangChain, LlamaIndex, model SDKs)
   └── Query format: Use the user's full question, not keywords

2. Web Search (brave_web_search or web_search_exa)
   └── Best for: Blog posts, papers, model-specific playbooks, latest techniques
   └── Query format: "[model] prompt engineering [specific topic] 2025 2026"

3. Documentation Search (ref_search_documentation)
   └── Best for: Official API docs, changelogs, migration guides
   └── Query format: "[library] [specific API or feature]"
```

### Research Absorption Rules

- Do NOT surface raw research to the user
- Do NOT cite papers or blog posts unless the user explicitly asks for sources
- Silently absorb findings and apply them to the prompt you are building
- If research contradicts your existing routing table, apply the newer information
- If research is ambiguous or conflicting, default to the more conservative technique

---

## Model-Specific Sweet Spots

For model-specific routing and best practices, see the **Tool Routing** section in [SKILL.md](../SKILL.md). Each model entry includes a `<!-- verified: YYYY-MM -->` tag — if older than 3 months, trigger a research pass before writing the prompt.

This file covers research methodology, advanced frameworks, and optimization strategies that complement the routing table.

---

## Context Engineering Principles

Context engineering (Karpathy & Lütke, 2025) treats the LLM as a CPU and the context window as RAM. For production systems, prompt quality depends less on "the prompt" and more on the entire information architecture loaded into the context window.

**Core principles:**

| Principle | Detail |
|-----------|--------|
| **Optimal density > volume** | LLM accuracy drops ~24% when relevant info is embedded in long irrelevant context (Stanford/SambaNova, 2025). Compact ruthlessly. |
| **LLM = CPU, context = RAM** | Your job is to load the right code and data for the task — instructions, examples, retrieved facts, tool descriptions, state history — then compact to optimal density. |
| **Placement matters** | Instructions at the start and end of the window get highest attention. Data in the middle gets weakest attention ("lost in the middle" effect). |
| **Static vs. dynamic separation** | Separate cacheable static context (system prompt, tool descriptions, examples) from dynamic context (user input, session state, retrieved docs). |
| **Compaction over summarization** | Summarization loses nuance. Use provider compaction endpoints (see below) for loss-aware compression that preserves the model's internal reasoning state. |

**When to apply context engineering vs. prompt engineering:**
- **Prompt engineering**: single-turn interactions, user-facing chat, one-shot tasks
- **Context engineering**: multi-turn agents, RAG pipelines, API-level production systems, long-running autonomous workflows

---

## Compaction Strategies

For long-running agents that exceed the context window, compaction is mandatory. Each provider offers different mechanisms.

### OpenAI Compaction
```
POST /responses/compact
```
- Loss-aware compression of conversation state into encrypted, opaque items
- Preserves task-relevant information while dramatically reducing token footprint
- Budgets carry forward across compaction cycles
- **Critical**: never try to parse the compacted item — it is an encrypted vector blob meant only for the model
- Trigger compaction when history exceeds a configurable threshold (e.g., 60% of context window)

### Claude Prompt Caching & Session Management
```
cache_control: {type: "ephemeral"}  // mark breakpoints for caching
```
- Automatic caching on the first ~1500 tokens of the prompt
- Explicit `cache_control` breakpoints for strategic caching of large reference blocks (50K+)
- Session hygiene: new task = new session. Use `/rewind` over mid-session corrections. Compact at ~50% context.
- **Task Budgets (Opus 4.7)**: set via `output_config.task_budget` with beta header `task-budgets-2026-03-13`. Budgets carry forward across compaction cycles. Use `max_tokens` as the hard stop.
- Caching long static prefixes is the single best mitigation for the new tokenizer cost (+35% on 4.7 vs 4.6).

### Gemini Encrypted Thinking Context
- Reasoning context is preserved across API calls automatically in the Interactions API
- For GenerateContent, pass `thoughtSignature` back unchanged between turns
- Structured outputs can be combined with built-in tools (Search, URL context, code execution, function calling) in a single call
- For tool overuse: first reduce `thinking_level` (medium → low → minimal), then add system instruction constraints


## Token Optimization Strategies

### Prompt Caching (API-level)

Prompt caching reduces cost and latency by reusing the processing of static prompt content across API calls.

**How to structure for caching:**
```
┌─────────────────────────────────────────────┐
│  CACHEABLE PREFIX (static across calls)     │
│  - System identity and role                 │
│  - Hard rules and constraints               │
│  - Output format specification              │
│  - Few-shot examples (if always the same)   │
│  - Tool descriptions (for agentic)          │
├─────────────────────────────────────────────┤
│  VARIABLE SUFFIX (changes per call)         │
│  - User input / query                       │
│  - Dynamic context / documents              │
│  - Session-specific memory block            │
└─────────────────────────────────────────────┘
```

**Provider specifics:**
- **Claude API**: Automatic caching on first ~1500 tokens. Explicit `cache_control` breakpoints available. Cache big things (50K+ reference blocks), not small things (500-token system prompts) — the break-even depends on cache read rate.
- **OpenAI API**: Automatic prompt caching on longer prompts. No manual control needed
- **Gemini API**: Context caching available for repeated large contexts

### Compression Techniques

| Technique | When to use | Example |
|-----------|------------|---------|
| **Remove default restaters** | Always | Remove "be helpful" from ChatGPT prompts — it's already the default |
| **Merge redundant instructions** | When multiple rules say the same thing | "Do not add features" + "Do not refactor" → "Only make changes directly requested" |
| **Format spec over example** | When format is simple | "Output as JSON with keys: name, age, role" instead of showing a full example |
| **Example over format spec** | When format is complex | Show 2-3 examples instead of a long format description |
| **Structured > prose** | For instructions | Tables and numbered lists compress better than paragraphs |
| **Signal word upgrade** | Always | MUST > should, NEVER > avoid, ALWAYS > typically |
| **Hedge word audit (4.7)** | For Claude Opus 4.7 | Delete "try to", "if possible", "ideally", "you might", "consider" — harden or remove |
| **Scope word audit (4.7)** | For Claude Opus 4.7 | Add "every", "all", "across N" for instructions that should apply broadly |
| **Process strip (5.5)** | For GPT-5.5 | Delete step-by-step sequences unless order is genuinely required |

### Cost Equation Reference

```
Total AI Cost = (Input Tokens × Input Price) + (Output Tokens × Output Price) × Number of Calls
```

- Shorter structured prompts → less variance, lower latency, lower cost
- Hill-climb quality first, then down-climb cost
- A 76% cost reduction is achievable by switching from verbose to structured prompts (Aakash Gupta, 2025)
- Caching long static prefixes is the single best mitigation for new tokenizer costs on Opus 4.7

---

## Advanced Framework Quick Reference

### DSPy — When the User Asks

DSPy replaces manual prompting with programmable signatures, modules, and optimizers.

**Key concepts to know:**
- **Signatures**: Declarative input/output specs (e.g., `question -> answer`)
- **Modules**: `Predict`, `ChainOfThought`, `ReAct`, `Module`
- **Optimizers**: `MIPROv2` (flagship, joint instruction + few-shot), `BootstrapFewShot`, `BestOfN`, `Refine`
- **Model-agnostic**: Same signature compiles to different prompts for GPT, Claude, Llama
- **Upfront cost**: 100-500 LLM calls for compilation, but guaranteed runtime reliability

**When to recommend DSPy:**
- User is building a production pipeline with multiple LLM steps
- User needs cross-model portability (switch between GPT, Claude, Llama without rewriting)
- User needs 99%+ reliability on structured outputs
- User has evaluation data to drive optimization

**When NOT to recommend DSPy:**
- User wants a single prompt to paste into ChatGPT
- User has no evaluation data or metrics
- Task is simple enough that manual prompting suffices

### TextGrad — When the User Asks

TextGrad uses natural language feedback as gradients for prompt refinement. Published in Nature 2025.

**When to recommend:**
- User is iterating on a single hard task (coding, scientific QA)
- User has a clear quality metric and wants automated refinement
- Instance-level optimization needed (one specific input, not a pipeline)

### Meta-Prompting — When the User Asks

Using an LLM to generate, evaluate, and refine its own prompts.

**When to recommend:**
- User wants to automate prompt improvement
- User has a feedback loop (metric → refine → re-evaluate)
- User is building a self-improving system

---

## "Lost in the Middle" — Context Placement Rules

Research consistently shows that LLMs attend more strongly to the beginning and end of their context window, with weaker attention in the middle (the "lost in the middle" effect).

**Placement strategy:**
1. **First 30%**: Critical instructions, identity, hard rules → highest attention
2. **Middle**: Supporting context, documents, data → weakest attention
3. **Last 20%**: Output format, final constraints → recency bias reinforces these

The 4-Block Layout in SKILL.md is specifically designed to exploit this effect:
- Block 1 (Instructions) → first 30%
- Block 2 (Context/Inputs) → middle
- Block 3 (Constraints) → late-middle, reinforced by signal words
- Block 4 (Output Format) → last position, recency bias

---

## Eval-Driven Iteration (2026 Standard)

Both Anthropic and OpenAI now state that prompt decisions need empirical grounding. Eval-driven iteration is mandatory, not optional.

**Minimum viable eval process:**
1. Build a set of 10-20 representative test cases before tuning prompts
2. Run old prompt → record outputs and quality scores
3. Run new prompt → compare against same test cases
4. Measure consistency across runs (not just single-shot quality)
5. Only declare a prompt "better" if it wins on the eval set, not just on the one example you tested

**When to mention to the user:**
- User is building production prompts (API-level deployment)
- User has been re-prompting the same task 3+ times
- User asks "which version is better" — suggest they eval both
