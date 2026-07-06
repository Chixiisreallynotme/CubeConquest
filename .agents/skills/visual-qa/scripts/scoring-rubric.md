# Visual QA — Scoring Rubric (Calibrated)

Each framework is scored 0–10 using this anchored scale. Always use this rubric to score — do not estimate from general impressions alone.

## Anchor Points (all frameworks)

| Score | Anchor |
|-------|--------|
| 0–2 | Broken, inaccessible, or missing entirely |
| 3–4 | Functional but generic; a default library install |
| 5–6 | Intentional but incomplete; some good decisions mixed with anti-patterns |
| 7–8 | Solid craft; clear professional decisions with minor issues |
| 9–10 | Exceptional; would ship at a design-focused company without comments |

---

## Framework 1: gpt-taste — Awwwards-Level Critique

**Score 9–10:** Hierarchy is unmistakable; typography has real character (non-system, hand-paired weights); colors are calibrated (not default library presets); no AI tells (no neon glow, no generic gradients); AIDA flow is legible on first read.

**Score 7–8:** Typography works but uses a single safe font; color palette is intentional but borrowed from a design system without customization; AIDA present but one section is weak.

**Score 5–6:** Layout is centered around a hero with generic card grid; font is Inter or system-ui with no hierarchy; one or more AI tells present.

**Score 3–4:** All defaults; no visual hierarchy; colors directly from component library; gradient backgrounds; generic CTAs.

**Score 0–2:** Illegible contrast; multiple LILA violations; broken layout at common viewport.

**Key deductions (each -1):**
- Neon glow CSS anywhere: -1
- Oversaturated accent color (saturation > 90% with lightness < 50%): -1
- Pure black `#000000` on backgrounds: -1
- No discernible heading hierarchy: -1
- More than 3 fonts loaded: -1

---

## Framework 2: applying-design-systems — Token Discipline

**Score 9–10:** All colors, spacing, and radii defined as CSS custom properties; 3+ perceptible surface depth levels; spacing from a visible scale (e.g., 4/8/16/24/32px); components styled consistently.

**Score 7–8:** Design tokens exist but some hardcoded values leak; depth ladder present with 2 levels; spacing mostly consistent.

**Score 5–6:** Some tokens; mixed hardcoded and token values; one surface level (flat).

**Score 3–4:** No CSS custom properties; all values hardcoded; no apparent spacing system.

**Score 0–2:** Chaotic styling; contradictory values; no system visible.

**Key measurements from css-scan.js:**
- `tokenAudit.customPropertiesCount === 0` → cap at 5/10
- `tokenAudit.emptyTokensCount > 5` → -1
- `surfaceAudit.depthLadderCount < 2` → -1 (flat design)
- `surfaceAudit.depthLadderCount > 6` → -1 (over-engineered)

---

## Framework 3: emil-design-eng — Animation Engineering

**Score 9–10:** `prefers-reduced-motion` media query present; `@media (hover: hover)` guards hover effects; all durations ≤ 800ms; custom cubic-bezier curves (not `ease` or `linear`); `:active` state has scale transform.

**Score 7–8:** `prefers-reduced-motion` present; mostly custom easings; `:active` state missing but other animation quality good.

**Score 5–6:** Some animation present but uses only `ease` or `linear`; no `prefers-reduced-motion`; durations reasonable.

**Score 3–4:** Animations present but no accessibility consideration; infinite animations on non-decorative elements; `transition: all` patterns.

**Score 0–2:** Infinite animations on primary UI; no reduced-motion; jarring or broken animations.

**Key measurements from css-scan.js:**
- `animationAudit.hasReducedMotion === false` → cap at 6/10 (accessibility blocker)
- `animationAudit.hasHoverHover === false` → -1
- `activeStates.hasActiveScaleTransform === false` → -1
- `animationAudit.infiniteAnimationCount > 3` → -1
- All easings are `ease` or `linear` (no custom cubic-bezier) → -1

---

## Framework 4: design-taste-frontend — Taste & Variance

**Score 9–10:** DESIGN_VARIANCE ≥ 7 (layout shifts between sections, not uniform cards); MOTION_INTENSITY appropriate for product type; no center-bias; loading/empty/error states exist; no AI tells.

**Score 7–8:** Good variance in layout; motion well-calibrated; minor center-bias in one section.

**Score 5–6:** Visible pattern but all sections follow same template; center-bias in multiple sections; one state missing (e.g., no empty state).

**Score 3–4:** Same card template repeated; everything centered; no differentiation between sections.

**Score 0–2:** Dashboard-by-numbers; sidebar + cards + charts with no editorial point of view.

**Key measurements from css-scan.js:**
- `layoutAudit.centerBiasRisk === true` → -2
- `layoutAudit.textAlignCenterCount > 30` → -1 additional
- No visible loading/empty states found in screenshots → -1

---

## Framework 5: impeccable — Nielsen Heuristics + Cognitive Load

Nielsen's 10 heuristics, each scored 0–4:

| # | Heuristic | 0 = Broken | 4 = Excellent |
|---|-----------|-----------|---------------|
| 1 | Visibility of system status | No feedback on actions | Instant, clear feedback |
| 2 | Match between system and real world | Jargon-heavy | Plain language matching user mental model |
| 3 | User control and freedom | No undo/cancel | Easy escape from every state |
| 4 | Consistency and standards | Inconsistent UI patterns | Platform conventions followed |
| 5 | Error prevention | No validation | Proactive error prevention |
| 6 | Recognition over recall | Everything requires memory | All choices visible |
| 7 | Flexibility and efficiency | No shortcuts | Expert shortcuts available |
| 8 | Aesthetic and minimalist design | Visual clutter | Only essential information shown |
| 9 | Help users recognize errors | Cryptic error messages | Clear, solution-oriented errors |
| 10 | Help and documentation | No help | Contextual, accessible help |

**Score = (sum of heuristic scores / 40) × 10** — round to nearest 0.5.

**Cognitive load deductions (each -0.5, max -2):**
- Primary action not obvious at first glance
- More than 5 navigation items without grouping
- Information density too high for the page's purpose
- No visual hierarchy between content blocks

---

## Composite Score

| Weight | Framework | Condition |
|--------|-----------|-----------|
| 20% (or ~16.7% if 6 active) | gpt-taste | Always |
| 20% (or ~16.7%) | applying-design-systems | Always |
| 20% (or ~16.7%) | emil-design-eng | Always |
| 20% (or ~16.7%) | design-taste-frontend | Always |
| 20% (or ~16.7%) | impeccable | Always |
| ~16.7% | page-cro | Landing pages only |

**Composite = sum of active framework scores ÷ count (5 or 6). Round to nearest 0.5.**

| Composite | Verdict |
|-----------|---------|
| 8.5–10 | Ship-ready at a design-focused company |
| 7–8.4 | Strong foundation; polish phase needed |
| 5.5–6.9 | Functional but generic; redesign specific sections |
| 3–5.4 | Template-looking; significant work needed |
| 0–2.9 | Rebuild with design system foundation |

---

## Framework 6: page-cro — Conversion Rate Optimization *(landing pages only)*

**Score formula: (sum of 7 dimension scores / 28) × 10. Round to nearest 0.5.**

| Dimension | Max | 4 = Excellent | 0 = Broken |
|-----------|-----|---------------|-----------|
| Above-fold clarity | 4 | Value prop clear in < 5s, hierarchy obvious | Headline unclear, no value prop visible |
| CTA quality | 4 | Outcome-focused copy, high contrast, single primary action | "Submit" / "Click Here" / no CTA above fold |
| Social proof | 4 | Testimonials + metrics within first 2 scrolls, photos + names | No social proof anywhere |
| Objection handling | 4 | Price/trust/risk/effort all addressed | No objection handling |
| Form optimization | 4 | ≤ 2 fields, inline validation, full-width submit, no CAPTCHA | 6+ fields, CAPTCHA, placeholder-as-label |
| Page speed | 4 | LCP GOOD (< 2500ms) | LCP POOR (> 4000ms) |
| Trust signals | 4 | Money-back guarantee + security badge + company logos | No trust signals |

**Deductions from perf-scan.js:**
- `cwvSummary.lcp.verdict === 'POOR'` → Dimension 6 = 0 (no partial credit)
- `cwvSummary.cls.verdict === 'POOR'` → -1 from Dimension 1 (layout shifts break trust)

**CTA copy scoring:**
- "Submit", "Click Here", "Learn More" (vague) → Dimension 2 max = 1/4
- "Get Started" (semi-generic) → Dimension 2 max = 2/4
- "Start My Free Trial", "Book My Demo" → full 4/4 possible
