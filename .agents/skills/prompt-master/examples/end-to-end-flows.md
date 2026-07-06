# End-to-End Flow Examples

Three complete examples showing the full Prompt Master loop: Interview → (Fast-Path check) → Research → Diagnose → Synthesize. Read this file when you want to see how the 4-step process works from start to finish.

---

## Example 1 — Simple Task (Fast-Path)

### User Request
> "Écris un tweet promotionnel pour mon app de méditation"

### Step 1: Interview (silent extraction)
| Dimension | Extracted |
|-----------|-----------|
| Task | Write a promotional tweet |
| Target Tool | Twitter/X (text) |
| AI Tool | ChatGPT (not specified → default) |
| Format | Tweet, max 280 chars |
| Audience | General public |
| Tone | Promotional, engaging |
| Constraints | 280 character limit |
| Context | Meditation app |
| Prior failures | None mentioned |

### Fast-Path Check
1. ✅ **Simple task** — single action (write), clear verb, no ambiguity
2. ✅ **Known tool** — ChatGPT is in the routing table
3. ✅ **Obvious format** — tweet = short text, no negotiation needed

**Result: ALL THREE → Fast-Path activated. Skip Steps 2-3.**

### Step 4: Synthesis (Template A — RTF)

**Delivered prompt:**
```
Role: You are a social media copywriter specializing in wellness brands.
Task: Write one promotional tweet (max 280 characters) for a meditation app. The tweet MUST include a clear benefit, a sense of urgency or curiosity, and a call to action. No hashtags unless they fit within the character limit.
Format: Raw tweet text only. No quotes, no explanation, no alternatives.
```

**Why this works:**
- Fast-path saved ~60% of the processing overhead
- Template A is sufficient for a single clear task
- Character limit and format are explicitly locked
- Role grounds the tone without lengthy instructions

---

## Example 2 — Complex Task (Claude Code Agent)

### User Request
> "Refactore l'authentification de mon app Next.js pour passer de JWT custom à NextAuth.js. J'utilise Prisma et PostgreSQL."

### Step 1: Interview (silent extraction)
| Dimension | Extracted |
|-----------|-----------|
| Task | Refactor authentication system |
| Target Tool | Claude Code (agentic) |
| AI Tool | Claude Opus 4.7 |
| Format | Code changes across multiple files |
| Audience | Developer (self) |
| Tone | Technical, precise |
| Constraints | Next.js, Prisma, PostgreSQL stack |
| Context | Existing JWT auth → NextAuth.js migration |
| Prior failures | None mentioned |

### Fast-Path Check
1. ❌ **Simple task** — multi-step, multi-file refactor, architectural change

**Result: FAIL → proceed to Steps 2-3.**

### Step 2: Research Phase

**Research triggers detected:**
- Agentic tool (Claude Code) → check latest best practices
- Claude Opus 4.7 → apply literal mode, front-loading, task budgets
- NextAuth.js migration → check for framework-specific patterns

**Findings silently absorbed:**
- Opus 4.7 requires front-loaded intent with explicit scope in first turn
- Adaptive thinking should be enabled for multi-file refactors
- NextAuth.js v5 uses `auth()` instead of `getServerSession()`
- Claude Code best practice: scope lock filesystem, forbidden actions list, ✅ checkpoints

### Step 3: Diagnostic Phase

**Patterns detected:**
| # | Pattern | Issue | Fix |
|---|---------|-------|-----|
| 6 | Build-the-whole-thing | "Refactore l'authentification" is a multi-step task | Decompose into sequential phases |
| 20 | No scope boundary | No file restrictions specified | Add explicit scope lock |
| 31 | No starting state | No description of current auth setup | Add starting state block |
| 35 | No human review trigger | Auth is a destructive change domain | Add stop conditions |
| 36 | Vague first turn on Opus 4.7 | Missing constraints, criteria, file scope | Use Template M |

### Step 4: Synthesis (Template M — Opus 4.7 Task Brief)

**Delivered prompt:**
```
## Goal
Migrate the authentication system from custom JWT to NextAuth.js v5.
After this migration: all existing routes remain functional, login/signup use NextAuth providers, session management uses NextAuth's built-in session handling, and all Prisma user models stay intact.

## Context
- Framework: Next.js 14 (App Router)
- Database: PostgreSQL via Prisma ORM
- Current auth: custom JWT implementation in `src/lib/auth.ts`
- User model: defined in `prisma/schema.prisma`
- Protected routes: `src/middleware.ts` checks JWT

## Scope
ONLY edit files in `src/` and `prisma/`. Touch nothing else.
- `src/lib/auth.ts` → replace with NextAuth configuration
- `src/middleware.ts` → update to use NextAuth session
- `prisma/schema.prisma` → add NextAuth required models (Account, Session, VerificationToken)
- `src/app/api/auth/[...nextauth]/route.ts` → create NextAuth API route
- `src/app/(auth)/login/page.tsx` → update login form to use signIn()
- `src/app/(auth)/signup/page.tsx` → update signup to use NextAuth

## Constraints
- Use NextAuth.js v5 with `auth()` helper, NOT deprecated `getServerSession()`
- Keep the existing Prisma User model fields — extend, do not replace
- Credentials provider for email/password login
- Do NOT add OAuth providers unless explicitly asked
- Do NOT modify any non-auth files
- Preserve all existing API routes

## Acceptance Criteria
- `npm run build` passes with zero errors
- Existing user records in the database are not affected
- Login with email/password works end-to-end
- Protected routes redirect to login when unauthenticated
- Session persists across page refreshes

## Forbidden Actions
- Do NOT delete the existing User model or any user data
- Do NOT add new dependencies beyond `next-auth` and `@auth/prisma-adapter`
- Do NOT touch `package.json` scripts, `.env.example`, or CI config

## Stop Conditions
Stop and ask before:
- Running any database migration
- Deleting any existing file
- Adding any dependency not listed above

## Progress
After each completed step: ✅ [what was done] — [file(s) affected]
```

⚠️ **This prompt is for an agentic tool with real system access. Review the scope locks, forbidden actions, and stop conditions before pasting. Confirm file paths, directories, and permissions match the actual project.**

---

## Example 3 — Image AI (Midjourney)

### User Request
> "Je veux un portrait cyberpunk d'une femme avec des implants néon"

### Step 1: Interview (silent extraction)
| Dimension | Extracted |
|-----------|-----------|
| Task | Generate an image |
| Target Tool | Midjourney (user mentioned in follow-up) |
| AI Tool | Midjourney |
| Format | Image prompt with parameters |
| Audience | Personal project |
| Tone | N/A (visual) |
| Constraints | Cyberpunk style, neon implants |
| Context | Portrait of a woman |
| Prior failures | None |

### Fast-Path Check
1. ✅ **Simple task** — single image generation
2. ✅ **Known tool** — Midjourney is in the routing table
3. ✅ **Obvious format** — Midjourney comma-separated descriptor format

**Result: ALL THREE → Fast-Path activated. Skip Steps 2-3.**

### Step 4: Synthesis (Template I — Visual Descriptor)

**Delivered prompt:**
```
cyberpunk woman portrait, glowing neon implants along jawline and temples, electric blue and magenta circuitry traces under translucent skin, rain-slicked hair, dark urban backdrop with holographic signage out of focus, dramatic rim lighting from neon signs, shallow depth of field, cinematic color grading with teal and orange contrast, hyper-detailed skin texture, 8K rendering --ar 2:3 --v 6 --style raw --no text, watermark, extra fingers, blurry background details
```

**Why this works:**
- Comma-separated descriptors, not prose (Midjourney sweet spot)
- Subject first, then style, mood, lighting, composition
- Specific technical descriptors replace vague aesthetics ("cyberpunk" → electric blue circuitry, holographic signage)
- Negative prompt via `--no` prevents common Midjourney artifacts
- Parameters at end: aspect ratio, version, style
- No hedge words, no ambiguity
