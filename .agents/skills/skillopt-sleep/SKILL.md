---
name: skillopt-sleep
description: Use when the user wants their Claude agent to self-improve from past usage, asks about a nightly or offline sleep or dream cycle, memory or skill consolidation, or says things like make my agent better the more I use it, review my past sessions, learn my preferences, consolidate what you learned, run the sleep cycle, or wants to schedule offline self-optimization. Drives the real skillopt_sleep engine: harvest past sessions → mine recurring tasks → replay offline → consolidate validated CLAUDE.md and SKILL.md behind a held-out gate.
---

# SkillOpt-Sleep — Offline self-evolution for Claude Code

Drives the **real `skillopt_sleep` engine** (ships with microsoft/skillopt).
Nightly: harvest past sessions → mine recurring tasks → replay offline →
consolidate into validated `CLAUDE.md` / `SKILL.md` behind a held-out gate.
**Nothing live is modified until you run `adopt`.**

**Repo**: `C:\Users\Chixi\AppData\Local\Temp\skillopt`
**Runner**: `C:\Users\Chixi\AppData\Local\Temp\skillopt\plugins\run-sleep.sh`

---

## Commands

```bash
# Status — what's happened, latest staged proposal (READ-ONLY)
python -m skillopt_sleep status --project "$(pwd)"

# Dry run — safe preview: what it would learn, no staging
python -m skillopt_sleep dry-run --project "$(pwd)" --backend mock

# Full cycle — harvest → mine → replay → gate → stage proposal
python -m skillopt_sleep run --project "$(pwd)" --backend claude

# Adopt — apply staged proposal to CLAUDE.md / SKILL.md (with backup)
python -m skillopt_sleep adopt --project "$(pwd)"

# Debug — just print the recurring tasks it found
python -m skillopt_sleep harvest --project "$(pwd)"

# Schedule nightly (prints crontab line, does NOT install)
python -m skillopt_sleep schedule --project "$(pwd)" --backend claude
```

**Always run from the SkillOpt repo root:**
```bash
cd "C:\Users\Chixi\AppData\Local\Temp\skillopt"
```

---

## The sleep cycle (6 stages)

| Stage | What happens |
|-------|-------------|
| 1. Harvest | Read `~/.claude/projects/*/` transcripts (READ-ONLY) → session digests |
| 2. Mine | Digests → recurring `TaskRecord`s with outcome labels |
| 3. Replay | Re-run tasks under current skill+memory → (hard, soft) scores |
| 4. Consolidate | Reflect on failures → propose bounded edits → **gate on held-out slice** |
| 5. Stage | Write `proposed_CLAUDE.md`, `proposed_SKILL.md`, diff, `report.md` to `.skillopt-sleep/staging/<date>/` |
| 6. Adopt | You explicitly copy staged files to live ones (with backup) |

---

## Backend choice

| Backend | Cost | Use when |
|---------|------|----------|
| `mock` | Free | Try the plumbing, no improvement |
| `claude` | API spend | Real improvement via Claude API |
| `codex` | API spend | Real improvement via Codex CLI |

Default: `mock`. Switch to `--backend claude` for genuine lift.

---

## After a run — what to show the user

1. Read the `report.md` from the staging dir it prints
2. Show: **held-out score baseline → candidate** (proof it helped)
3. Show: gate decision (accept/reject) and exact proposed edits
4. If accepted: tell the user nothing live changed yet, offer `adopt`
5. After `adopt`: confirm which files were updated and backup location

---

## Validation — deterministic proof (no API key needed)

```bash
cd "C:\Users\Chixi\AppData\Local\Temp\skillopt"
python -m skillopt_sleep.experiments.run_experiment --persona researcher --assert-improves
```

---

## Safety rules

- **Never** hand-edit `CLAUDE.md` or `SKILL.md` — only `adopt` does that, with backup
- Harvest is read-only over `~/.claude`
- `mock` replay has no side effects
- Always show baseline → candidate score before suggesting adoption
- Gate (`evaluation.use_gate: true`) is ON by default — keeps worst case bounded

---

## Schedule nightly (optional)

```bash
cd "C:\Users\Chixi\AppData\Local\Temp\skillopt"
python -m skillopt_sleep schedule --project "/c/Users/Chixi/Documents/Projects/Candidat/V1"
```

Prints a crontab line to copy into `crontab -e`. Does not install anything without confirmation.

## Learn more

Full guide: https://microsoft.github.io/SkillOpt/docs/guideline.html#sleep
Design doc: `C:\Users\Chixi\AppData\Local\Temp\skillopt\docs\sleep\README.md`
