# Secondary Tool Routing Rules

Specific routing rules and optimization guidelines for secondary, niche, local, and specialized AI models and tools. Read only the section matching the target tool.

---

## Language & Reasoning Models

### Grok (Grok 3, Grok 4, Grok 4.3)
- **Context Drift Control**: Grok is sensitive to context length and drift. Separating input blocks using XML tags (e.g., `<requirements>`, `<data>`) or clear Markdown headings is mandatory to lock focus.
- **Identity Enforcement**: Always define a strong system role. Grok requires a firm anchor to prevent chatty or casual tone drift.

---

### DeepSeek (DeepSeek-R1, DeepSeek V4)
- **Reasoning-native**: both R1 and V4 have thinking mode enabled by default. Do NOT add Chain of Thought (CoT) instructions — it actively degrades output.
- **Tool Discipline**: Treat it like o3 — short, clean, goal-oriented instructions. State the goal and desired output format.
- **Formatting**: By default, outputs reasoning in `<think>` tags. If clean outputs are required, instruct: "Output only the final answer without thinking tags."
- **Instruction style**: Short clean instructions only. Keep system prompts compact. Zero-shot preferred.

---

### GLM (GLM-5, GLM-5.1)
- **Long-horizon tasks**: Optimized for deep agentic flows.
- **Contract Prompts**: Define strict input-output schemas. Responds best to formal, contractual instruction layouts.

---

### NVIDIA Nemotron (Nemotron 3 Super / Ultra / Nano / Omni)
- **Local Deployment**: Optimized for high-throughput local pipelines. Keep instructions compact.
- **Multimodal Single-pass**: Nemotron Omni processes audio/video/text simultaneously. Provide unified temporal instructions (e.g., timestamps) for multimodal inputs.

---

### Qwen 2.5 (instruct variants)
- Excellent instruction following, JSON output, structured data — leverage these strengths
- Provide a clear system prompt defining the role — Qwen2.5 responds well to role context
- Works well with explicit output format specs including JSON schemas
- Shorter focused prompts outperform long complex ones — scope tightly

---

### Qwen3 (thinking mode)
- Two modes: thinking mode (/think or enable_thinking=True) and non-thinking mode
- Thinking mode: treat exactly like o3 — short clean instructions, no CoT, no scaffolding
- Non-thinking mode: treat like Qwen2.5 instruct — full structure, explicit format, role assignment

---

### Ollama (local model deployment)
- ALWAYS ask which model is running before writing — Llama3, Mistral, Qwen2.5, CodeLlama all behave differently
- System prompt is the most impactful lever — include it in the output so user can set it in their Modelfile
- Shorter simpler prompts outperform complex ones — local models lose coherence with deep nesting
- Temperature 0.1 for coding/deterministic tasks, 0.7-0.8 for creative tasks
- For coding: CodeLlama or Qwen2.5-Coder, not general Llama

---

### Llama 4 / Mistral / Gemma 4 / open-weight LLMs
- Shorter prompts work better — these models lose coherence with deeply nested instructions.
- Simple flat structure — avoid heavy nesting or multi-level hierarchies.
- Be more explicit than you would with Claude or GPT — instruction following is weaker.
- Always include a role in the system prompt.

---

### MiniMax (M2.7 / M2.5)
- OpenAI-compatible API — prompts that work with GPT models transfer directly
- Strong at instruction following, structured output, and long-context synthesis — 1M context window on M2.7
- M2.5-highspeed has a 204K context window and is optimized for speed — use for latency-sensitive tasks
- Temperature must be between 0 and 1 (inclusive) — prompts that set temperature above 1 will fail
- May output reasoning in `<think>` tags — add "Output only the final answer, no reasoning tags." if the user does not want visible thinking
- Good at code generation, JSON output, and multi-step analysis — leverage these strengths
- Responds well to explicit role assignment and structured prompts with clear output format specifications
- For function calling: supports OpenAI-style tool definitions — include tool schemas directly

---

## Coding, Agentic & Automation Tools

### Bolt / v0 / Lovable / Figma Make / Google Stitch
- Full-stack generators default to bloated boilerplate — scope it down explicitly
- Always specify: stack, version, what NOT to scaffold, clear component boundaries
- Lovable responds well to design-forward descriptions — include visual/UX intent
- v0 is Vercel-native — specify if you need non-Next.js output
- Bolt handles full-stack — be explicit about which parts are frontend vs backend vs database
- Figma Make is design-to-code native — reference your Figma component names directly
- Google Stitch is prompt-to-UI focused — describe the interface goal not the implementation. Add "match Material Design 3 guidelines" for Google-native styling
- Add "Do not add authentication, dark mode, or features not explicitly listed" to prevent feature bloat

---

### Devin / SWE-agent
- Fully autonomous — can browse web, run terminal, write and test code
- Very explicit starting state + target state required
- Forbidden actions list is critical — Devin will make decisions you did not intend without explicit constraints
- Scope the filesystem: "Only work within /src. Do not touch infrastructure, config, or CI files."

---

### Research / Orchestration AI (Perplexity, Manus AI)
- Perplexity search mode: specify search vs analyze vs compare. Add citation requirements. Reframe hallucination-prone questions as grounded queries.
- Manus and Perplexity Computer are multi-agent orchestrators — describe the end deliverable, not the steps. They decompose internally.
- For Perplexity Computer: specify the output artifact type (report / spreadsheet / code / summary). Add "Flag any data point you are not confident about."
- For long multi-step tasks: add verification checkpoints since each chained step compounds hallucination risk

---

### Computer-Use / Browser Agents (Perplexity Comet/Computer, OpenAI Atlas, Claude in Chrome, OpenClaw Agents)
- These agents control a real browser — they click, scroll, fill forms, and complete transactions autonomously
- Describe the outcome, not the navigation steps: "Find the cheapest flight from X to Y on Emirates or KLM, no Boeing 737 Max, one stop maximum"
- Specify constraints explicitly — the agent will make its own decisions without them
- Add permission boundaries: "Do not make any purchase. Research only."
- Add a stop condition for irreversible actions: "Ask me before submitting any form, completing any transaction, or sending any message"
- Comet works best with web research, comparison, and data extraction tasks
- Atlas is stronger for multi-step commerce and account management tasks

---

### Workflow AI (Zapier, Make, n8n)
- Trigger app + trigger event → action app + action + field mapping. Step by step.
- Auth requirements noted explicitly — "assumes [app] is already connected"
- For multi-step workflows: number each step and specify what data passes between steps

---

## Specialized Multi-Modal Tools

### Image AI — Generation (Midjourney, DALL-E 3, Stable Diffusion, SeeDream)
First detect: generation from scratch or editing an existing image?

- **Midjourney**: Comma-separated descriptors, not prose. Subject first, then style, mood, lighting, composition. Parameters at end: `--ar 16:9 --v 6 --style raw`. Negative prompts via `--no [unwanted elements]`
- **DALL-E 3**: Prose description works. Add "do not include text in the image unless specified." Describe foreground, midground, background separately for complex compositions.
- **Stable Diffusion**: `(word:weight)` syntax. CFG 7-12. Negative prompt is MANDATORY. Steps 20-30 for drafts, 40-50 for finals.
- **SeeDream**: Strong at artistic and stylized generation. Specify art style explicitly (anime, cinematic, painterly) before scene content. Mood and atmosphere descriptors work well. Negative prompt recommended.

---

### Image AI — Reference Editing (when user has an existing image to modify)
- Detect when: user mentions "change", "edit", "modify", "adjust" anything in an existing image, or uploads a reference.
- Always instruct the user to attach the reference image to the tool first. Build the prompt around the delta ONLY — what changes, what stays the same.
- Read [references/templates.md](templates.md) Template J for the full reference editing template.

---

### Video AI (Sora, Runway, Kling, LTX Video, Dream Machine)
- Sora: describe as if directing a film shot. Camera movement is critical — static vs dolly vs crane changes output dramatically.
- Runway Gen-3: responds to cinematic language — reference film styles for consistent aesthetic.
- Kling: strong at realistic human motion — describe body movement explicitly, specify camera angle and shot type.
- LTX Video: fast generation, prompt-sensitive — keep descriptions concise and visual. Specify resolution and motion intensity explicitly.
- Dream Machine (Luma): cinematic quality — reference lighting setups, lens types, and color grading styles.

---

### Music AI (Suno, Udio, ElevenLabs Music)
A music AI prompt has two parts: the **style prompt** (genre, mood, instruments, vocal, production, tempo) and the **lyrics** (with structural metatags).

- **Suno**: Style prompt is comma-separated tags, NOT prose. Front-load the most important genre/mood/vocal tags — Suno V5 allows up to 1000 chars but older models silently truncate at ~200. Use structural metatags: `[Verse]`, `[Chorus]`, `[Bridge]`, `[Outro]`, `[Instrumental Break]`. Strongest at vocal quality and complete song structures.
- **Udio**: Accepts longer, more descriptive prompts than Suno. Supports **negative prompting** (tell the AI what to avoid). Often cited for technically superior audio fidelity. Use detailed production language.
- **ElevenLabs Music**: Newer entrant focused on instrumental compositions. Describe genre + mood + instrumentation explicitly. Does not generate vocals — use for background music, soundscapes, and cinematic scores.
- **Common failures**: no structural metatags → unstructured sonic drift; too many genre tags → muddy output; vague mood ("chill") without instrument anchors → generic results.
- **Key principle**: each comma-separated term acts as a weighted tag pulling the output in a direction. Order matters — front-loaded tags carry more weight.
- Read references/templates.md Template N for the full Music AI template.

---

### ComfyUI
- Node-based workflow — not a single prompt box. Ask which checkpoint model is loaded before writing.
- Always output two separate blocks: Positive Prompt and Negative Prompt. Never merge them.
- Read [references/templates.md](templates.md) Template K for the full ComfyUI template.

---

### 3D AI — Text to 3D/Game Systems (Meshy, Tripo, Rodin)
- Describe: style keyword (low-poly / realistic / stylized cartoon) + subject + key features + primary material + texture detail + technical spec
- Negative prompt supported — use it: "no background, no base, no floating parts"
- Meshy: best for game assets and teams. Game asset prompts work best here.
- Tripo: fastest for clean topology. Rapid prototyping and concept assets.
- Rodin: highest quality for photorealistic prompts. Slower and more expensive.
- Specify intended export use: game engine (GLB/FBX), 3D printing (STL), web (GLB)
- For characters: specify A-pose or T-pose if the model will be rigged

---

### 3D AI — In-Engine AI (Unity AI, Blender AI tools)
- Unity AI (Unity 6.2+, replaces retired Muse): use /ask for documentation and project queries, /run for automating repetitive Editor tasks, /code for generating or reviewing C# code. Be precise — state exactly what needs to happen in the Editor.
- Unity AI Generators: text-to-sprite, text-to-texture, text-to-animation. Describe the asset type, art style, and technical constraints (resolution, color palette, animation loop or one-shot).
- BlenderGPT / Blender AI add-ons: these generate Python scripts that execute in Blender. Be specific about geometry, material names, and scene context. Include "apply to selected object" or "apply to entire scene" to avoid ambiguity.

---

### Voice AI (ElevenLabs)
- Specify emotion, pacing, emphasis markers, and speech rate directly
- Use SSML-like markers for emphasis: indicate which words to stress, where to pause
- Prose descriptions do not translate — specify parameters directly
