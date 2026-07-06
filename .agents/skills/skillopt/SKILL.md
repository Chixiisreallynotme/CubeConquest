---
name: skillopt
description: Run the real SkillOpt training loop (microsoft/skillopt) to optimize a Claude Code skill file using scored rollouts, validation gating, and bounded text edits — no weight training. Use when the user says /skillopt, wants to train or optimize a skill with real data, create a benchmark for a skill, run eval-only on an existing skill, or asks to improve a skill scientifically.
---

# SkillOpt — Real Training Loop

Drives the **real SkillOpt package** (`pip install skillopt`, already installed) to
train a Claude Code skill file with the same discipline as neural network training —
epochs, batch size, validation gates — but entirely in text space, zero model-weight changes.

**SkillOpt repo**: `C:\Users\Chixi\AppData\Local\Temp\skillopt`
**Python**: `python` (venv with skillopt installed)

---

## Mode 1 — Eval an existing skill (no data needed)

Check how well a skill currently performs, without training:

```bash
cd "C:\Users\Chixi\AppData\Local\Temp\skillopt"
python scripts/eval_only.py \
  --config configs/<env>/default.yaml \
  --skill ~/.claude/skills/<skill-name>/SKILL.md \
  --split_dir ~/.claude/skillopt/benchmarks/<skill-name>/split \
  --out_root ~/.claude/skillopt/runs/<skill-name>/eval_$(date +%Y%m%d)
```

## Mode 2 — Full training loop

Train the skill for real, producing `best_skill.md`:

```bash
cd "C:\Users\Chixi\AppData\Local\Temp\skillopt"
python scripts/train.py \
  --config ~/.claude/skillopt/benchmarks/<skill-name>/config.yaml \
  --optimizer_model claude-sonnet-4-6 \
  --target_model claude-sonnet-4-6
```

Output → `ckpt/<run-name>/best_skill.md`

---

## Workflow — from scratch

### Step 1 — Create a benchmark

Create `~/.claude/skillopt/benchmarks/<skill-name>/data.jsonl`:

```jsonl
{"id": "001", "input": "<task description>", "expected": "<correct behavior>", "task_type": "main"}
{"id": "002", "input": "<task description>", "expected": "<correct behavior>", "task_type": "main"}
```

Minimum: **20 items** for training, 10 for validation, 10 for test.
Recommended: 50-100 items for meaningful lift.

### Step 2 — Create the YAML config

Create `~/.claude/skillopt/benchmarks/<skill-name>/config.yaml`:

```yaml
_base_: C:\Users\Chixi\AppData\Local\Temp\skillopt\configs\_base_\default.yaml

env:
  name: generic_claude_code   # use the built-in generic env
  skill_init: ~/.claude/skills/<skill-name>/SKILL.md
  data_path: ~/.claude/skillopt/benchmarks/<skill-name>/data.jsonl
  split_mode: ratio
  split_ratio: "5:2:3"       # 50% train / 20% val / 30% test
  max_completion_tokens: 4096

train:
  num_epochs: 4
  batch_size: 16
  seed: 42

optimizer:
  learning_rate: 4
  lr_scheduler: cosine
  use_slow_update: true

evaluation:
  use_gate: true

model:
  optimizer_backend: claude_chat
  target_backend: claude_code_exec   # Claude Code CLI is the harness
  claude_code_exec_effort: medium
```

### Step 3 — Run training

```bash
cd "C:\Users\Chixi\AppData\Local\Temp\skillopt"
python scripts/train.py --config ~/.claude/skillopt/benchmarks/<skill-name>/config.yaml
```

### Step 4 — Deploy the best skill

```bash
cp ckpt/<run-name>/best_skill.md ~/.claude/skills/<skill-name>/SKILL.md
```

---

## Quick reference — backends

| Backend | Value | Use for |
|---------|-------|---------|
| Claude Code CLI (harness) | `claude_code_exec` | target_backend — runs the agent loop |
| Claude chat (optimizer) | `claude_chat` | optimizer_backend — edits the skill |
| Mock (no API) | `mock` | free testing of the plumbing |

## Options — important YAML knobs

| Knob | Default | Effect |
|------|---------|--------|
| `train.num_epochs` | 4 | Training rounds |
| `optimizer.learning_rate` | 4 | Max edits per step |
| `optimizer.lr_scheduler` | cosine | `cosine/linear/constant/autonomous` |
| `optimizer.use_slow_update` | true | Epoch-boundary momentum |
| `optimizer.use_meta_skill` | true | Cross-epoch optimizer memory |
| `evaluation.use_gate` | true | Keep only edits that improve val score |
| `env.split_ratio` | 2:1:7 | train:val:test split |

## Expected results (from paper, Claude Code harness)

- GPT-5.5: **+19.1 pts** avg accuracy lift
- Skills transfer across model scales and harnesses
- `best_skill.md` is 300–2000 tokens

## Notes

- Always keep `evaluation.use_gate: true` — this is what prevents regressions
- API key needed: set `ANTHROPIC_API_KEY` in env for `claude_chat` + `claude_code_exec`
- Costs ~1-4h for a full run (4 epochs × 40 batch)
- See [docs](https://microsoft.github.io/SkillOpt/docs/guideline.html) for full reference
