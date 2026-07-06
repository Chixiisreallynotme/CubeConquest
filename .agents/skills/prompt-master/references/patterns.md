# Credit-Killing Patterns Reference

43 patterns that waste tokens and cause re-prompts. Read this file when the user pastes a bad prompt and asks you to fix it, or when diagnosing why a prompt is underperforming.

---

## Task Patterns

| # | Pattern | Bad Example | Fixed |
|---|---------|------------|-------|
| 1 | **Vague task verb** | "help me with my code" | "Refactor `getUserData()` to use async/await and handle null returns" |
| 2 | **Two tasks in one prompt** | "explain AND rewrite this function" | Split into two prompts: explain first, rewrite second |
| 3 | **No success criteria** | "make it better" | "Done when the function passes existing unit tests and handles null input without throwing" |
| 4 | **Over-permissive agent** | "do whatever it takes" | Explicit allowed actions list + explicit forbidden actions list |
| 5 | **Emotional task description** | "it's totally broken, fix everything" | "Throws uncaught TypeError on line 43 when `user` is null" |
| 6 | **Build-the-whole-thing** | "build my entire app" | Break into Prompt 1 (scaffold), Prompt 2 (core feature), Prompt 3 (polish) |
| 7 | **Implicit reference** | "now add the other thing we discussed" | Always restate the full task — never reference "the thing we discussed" |

---

## Context Patterns

| # | Pattern | Bad Example | Fixed |
|---|---------|------------|-------|
| 8 | **Assumed prior knowledge** | "continue where we left off" | Include Memory Block with all prior decisions |
| 9 | **No project context** | "write a cover letter" | "PM role at B2B fintech, 2yr SWE experience transitioning to product, shipped 3 features as tech lead" |
| 10 | **Forgotten stack** | New prompt contradicts prior tech choice | Always include Memory Block with established stack |
| 11 | **Hallucination invite** | "what do experts say about X?" | "Cite only sources you are certain of. If uncertain, say so explicitly rather than guessing." |
| 12 | **Undefined audience** | "write something for users" | "Non-technical B2B buyers, no coding knowledge, decision-maker level" |
| 13 | **No mention of prior failures** | (blank) | "I already tried X and it didn't work because Y. Do not suggest X." |

---

## Format Patterns

| # | Pattern | Bad Example | Fixed |
|---|---------|------------|-------|
| 14 | **Missing output format** | "explain this concept" | "3 bullet points, each under 20 words, with a one-sentence summary at top" |
| 15 | **Implicit length** | "write a summary" | "Write a summary in exactly 3 sentences" |
| 16 | **No role assignment** | (blank) | "You are a senior backend engineer specializing in Node.js and PostgreSQL" |
| 17 | **Vague aesthetic adjectives** | "make it look professional" | "Monochrome palette, 16px base font, 24px line height, no decorative elements" |
| 18 | **No negative prompts for image AI** | "a portrait of a woman" | Add: "no watermark, no blur, no extra fingers, no distortion, no text overlay" |
| 19 | **Prose prompt for Midjourney** | Full descriptive sentence | "subject, style, mood, lighting, composition, --ar 16:9 --v 6" |

---

## Scope Patterns

| # | Pattern | Bad Example | Fixed |
|---|---------|------------|-------|
| 20 | **No scope boundary** | "fix my app" | "Fix only the login form validation in `src/auth.js`. Touch nothing else." |
| 21 | **No stack constraints** | "build a React component" | "React 18, TypeScript strict, no external libraries, Tailwind only" |
| 22 | **No stop condition for agents** | "build the whole feature" | Explicit stop conditions + ✅ checkpoint output after each step |
| 23 | **No file path for IDE AI** | "update the login function" | "Update `handleLogin()` in `src/pages/Login.tsx` only" |
| 24 | **Wrong template for tool** | GPT-style prose prompt used in Cursor | Adapt to File-Scope Template (Template G) |
| 25 | **Pasting entire codebase** | Full repo context every prompt | Scope to only the relevant function and file only |

---

## Reasoning Patterns

| # | Pattern | Bad Example | Fixed |
|---|---------|------------|-------|
| 26 | **No CoT for logic task** | "which approach is better?" | "Think through both approaches step by step before recommending" |
| 27 | **Adding CoT to reasoning models** | "think step by step" sent to o1/o3 | Remove it — reasoning models think internally, CoT instructions degrade output |
| 28 | **No self-check on complex output** | (nothing) | "Before finishing, verify output against the constraints above" |
| 29 | **Expecting inter-session memory** | "you already know my project" | Always re-provide the Memory Block in every new session |
| 30 | **Contradicting prior decisions** | New prompt ignores earlier architecture | Memory Block with all established facts |

---

## Agentic Patterns

| # | Pattern | Bad Example | Fixed |
|---|---------|------------|-------|
| 31 | **No starting state** | "build me a REST API" | "Empty Node.js project, Express installed, `src/app.js` exists" |
| 32 | **No target state** | "add authentication" | "`/src/middleware/auth.js` with JWT verify. `POST /login` and `POST /register` in `/src/routes/auth.js`" |
| 33 | **Silent agent** | No progress output | "After each step output: ✅ [what was completed]" |
| 34 | **Unlocked filesystem** | No file restrictions | "Only edit files inside `src/`. Do not touch `package.json`, `.env`, or any config file." |
| 35 | **No human review trigger** | Agent decides everything autonomously | "Stop and ask before: deleting any file, adding any dependency, or changing the database schema" |
| 36 | **Vague first turn on Opus 4.7** | "fix the auth bug" with no scope, no files, no criteria | Opus 4.7 reads prompts literally — it no longer fills implicit context like 4.6 did. Use Template M. Front-load intent, file scope, constraints, and acceptance criteria. |
| 37 | **Context rot on long sessions** | Keeps correcting in the same session for 60+ turns | New task = new session. Use /rewind instead of correcting. /compact at ~50% context. Subagents for file-heavy investigation. |

---

## Model Migration & Production Patterns

| # | Pattern | Bad Example | Fixed |
|---|---------|------------|-------|
| 38 | **Legacy prompt stack on new model** | Process-heavy "step 1, step 2, step 3" prompt ported unchanged to GPT-5.5 | Rewrite as outcome-first: define what "done" looks like, success criteria, and constraints. Let GPT-5.5 choose the path. |
| 39 | **Wrong reasoning effort** | `reasoning_effort: "xhigh"` on a simple formatting task | Match effort to task complexity. Use `none` or `low` for simple tasks. `medium` is GPT-5.5 default. Only escalate to `high`/`xhigh` with eval data showing improvement. |
| 40 | **Missing compaction strategy** | Long-running agent with no compaction → context drift after 30+ turns | Add compaction triggers: OpenAI `/responses/compact`, Claude `cache_control` breakpoints + session hygiene, Gemini encrypted thinking context. Trigger at ~60% of context window. |
| 41 | **Data before instructions on Gemini** | Instructions at the top, then 100K tokens of data, then more instructions | Gemini 3 attends most strongly to the end. Place all data first, then instructions at the END. Negative constraints as the final line. |
| 42 | **Music prompt without structure tags** | "chill lofi vibes with some piano" sent to Suno without any metatags | Add structural metatags: `[Verse]`, `[Chorus]`, `[Bridge]`, `[Outro]`. Front-load genre and mood in the style prompt. Without tags, Suno drifts structurally. |
| 43 | **Over-verbose prompt on reasoning model** | 500+ word prompt with manual CoT, step-by-step, examples on Gemini 3 / o3 / DeepSeek-R1 | Reasoning models over-analyze verbose prompts. Cut to ~100 words: goal + constraints + output format. Set `thinking_level` or `reasoning_effort` via API instead. |
