---
name: applying-design-systems
description: >-
  Design system discipline reviewer: token usage, surface depth ladder, spacing scale,
  component consistency. Use when evaluating whether a UI applies a coherent design system
  vs. hardcoding values. Distilled from "Applying Design Systems" principles.
---

# Applying Design Systems — Review Framework

## Core Question

Does this interface apply a design system, or is it a collection of one-off values?

---

## Token Discipline

A well-applied design system uses CSS custom properties (or equivalent tokens) for every
repeated design decision: colors, spacing, radius, shadow, typography, z-index, duration.

**Signals of strong token discipline:**
- All colors reference `--color-*` variables, not raw hex/rgb
- Spacing uses a visible scale (e.g. 4/8/16/24/32/48px) — not arbitrary values
- Border-radius is consistent across similar component types
- Shadows/elevation come from a small set of named levels
- Durations reference `--duration-fast`, `--duration-normal`, etc.

**Score cap trigger:** If `tokenAudit.customPropertiesCount === 0` (from css-scan.js),
the maximum possible score is 5/10 — no token discipline exists.

---

## Surface Depth Ladder

Surfaces should form a perceivable hierarchy of depth (not just flat cards everywhere).
A good depth ladder has 2–5 distinct background levels:

| Level | Example | CSS |
|-------|---------|-----|
| Ground | Page background | `--color-background` |
| Raised | Cards, panels | `--color-surface` |
| Floating | Modals, popovers | `--color-overlay` |
| Inverse | Dark-on-light elements | `--color-inverse` |

**Score signals:**
- `surfaceAudit.depthLadderCount < 2` → -1 (flat, no perceived depth)
- `surfaceAudit.depthLadderCount > 6` → -1 (over-engineered, incoherent)
- `surfaceAudit.depthLadderCount` between 2–5 → good

---

## Spacing Scale Consistency

Margins and paddings should derive from a scale, not random values.
Common scales: 4px base (4/8/12/16/24/32/48/64/96), 8px base, or a modular ratio.

Look for: are the spacing values in screenshots forming a visible rhythm?
Do elements feel "snapped to a grid" or do they float at arbitrary distances?

---

## Component Consistency

Similar elements should be styled the same way throughout the interface.
- All primary buttons: same height, radius, typography, padding?
- All cards: same shadow, radius, padding structure?
- All form inputs: same height, border, focus style?

Inconsistency signals: different border-radii on similar components, 
mixed font weights on same-level headings, irregular shadow intensities.

---

## Scoring Anchors

| Score | Description |
|-------|-------------|
| 9–10 | All values tokenized; 3+ surface levels; visible spacing scale; fully consistent components |
| 7–8 | Tokens exist but some hardcoded values leak; 2 surface levels; mostly consistent |
| 5–6 | Some tokens; mixed hardcoded values; 1–2 surface levels; inconsistencies visible |
| 3–4 | No custom properties; all values hardcoded; no apparent spacing system |
| 0–2 | Chaotic styling; contradictory values; no coherent system visible |

**Score cap:** `tokenAudit.customPropertiesCount === 0` → max 5/10.

---

## Key Measurements (from css-scan.js)

| Metric | Good | Deduct |
|--------|------|--------|
| `tokenAudit.customPropertiesCount` | > 10 | === 0 → cap at 5/10 |
| `tokenAudit.emptyTokensCount` | 0 | > 5 → -1 |
| `surfaceAudit.depthLadderCount` | 2–5 | < 2 → -1 (flat); > 6 → -1 (chaotic) |
| Border-radius distribution | ≤ 3 distinct values | > 5 distinct values → -1 |
