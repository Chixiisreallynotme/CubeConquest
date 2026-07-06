"""
SkillOpt benchmark — visual-qa skill
Tests 12 TaskRecords covering the critical behaviors introduced in v3.2.0.
Run with: python benchmark.py [--backend claude|mock] [--nights 3]
"""
from __future__ import annotations
import sys
import pathlib
import json

sys.path.insert(0, "/tmp/skillopt")

from skillopt_sleep.types import TaskRecord

VISUAL_QA_SKILL_PATH = pathlib.Path(r"C:\Users\Chixi\.claude\skills\visual-qa\SKILL.md")

# ─── Tasks: 8 train, 4 val ────────────────────────────────────────────────────

TASKS: list = [

    # ── TRAIN ────────────────────────────────────────────────────────────────

    TaskRecord(
        id="vqa_css_scan_bundled",
        project="visual-qa",
        intent=(
            "You are running a visual QA audit — Phase 3: Automated CSS Scan. "
            "Describe exactly how you execute the CSS scan."
        ),
        reference_kind="exact",
        reference="scripts/css-scan.js",
        tags=["rule:use-bundled-css-scan"],
        split="train",
    ),

    TaskRecord(
        id="vqa_rubric_before_scoring",
        project="visual-qa",
        intent=(
            "You are about to score a web app against the 5 design frameworks (Phase 5). "
            "What file must you read before starting, and why?"
        ),
        reference_kind="exact",
        reference="scoring-rubric.md",
        tags=["rule:read-scoring-rubric"],
        split="train",
    ),

    TaskRecord(
        id="vqa_cap_tokens",
        project="visual-qa",
        intent=(
            "The css-scan.js returned tokenAudit.customPropertiesCount = 0. "
            "What score cap does this trigger and for which framework?"
        ),
        reference_kind="exact",
        reference="5/10 applying-design-systems",
        tags=["rule:token-cap-5"],
        split="train",
    ),

    TaskRecord(
        id="vqa_cap_reduced_motion",
        project="visual-qa",
        intent=(
            "The css-scan.js returned animationAudit.hasReducedMotion = false. "
            "What score cap does this trigger and for which framework?"
        ),
        reference_kind="exact",
        reference="6/10 emil-design-eng",
        tags=["rule:reduced-motion-cap-6"],
        split="train",
    ),

    TaskRecord(
        id="vqa_skill_file_fallback",
        project="visual-qa",
        intent=(
            "You are in Phase 5 and .agents/skills/gpt-taste/SKILL.md does not exist. "
            "What is the next path you try?"
        ),
        reference_kind="exact",
        reference="~/.claude/skills/gpt-taste/SKILL.md",
        tags=["rule:fallback-path"],
        split="train",
    ),

    TaskRecord(
        id="vqa_exa_conditional",
        project="visual-qa",
        intent=(
            "Phase 3 shows glowAudit.neonGlowCount = 8 and layoutAudit.centerBiasRisk = true. "
            "Which ADDITIONAL Exa queries should you run beyond the 5 mandatory ones?"
        ),
        reference_kind="exact",
        reference="neon glow anti-center-bias",
        tags=["rule:conditional-exa"],
        split="train",
    ),

    TaskRecord(
        id="vqa_phase_order",
        project="visual-qa",
        intent=(
            "A user asks you to jump straight to scoring (Phase 5) without running Playwright. "
            "What do you say?"
        ),
        reference_kind="exact",
        reference="phases cannot be skipped",
        tags=["rule:phase-order"],
        split="train",
    ),

    TaskRecord(
        id="vqa_css_scan_no_rewrite",
        project="visual-qa",
        intent=(
            "In Phase 3, can you simplify the css-scan.js script to only collect "
            "the metrics you actually need, or must you use it as-is?"
        ),
        reference_kind="exact",
        reference="paste it as-is",
        tags=["rule:use-bundled-css-scan"],
        split="train",
    ),

    # ── VAL (held-out gate) ────────────────────────────────────────────────

    TaskRecord(
        id="vqa_val_composite_score",
        project="visual-qa",
        intent=(
            "How is the composite score computed from the 5 framework scores?"
        ),
        reference_kind="exact",
        reference="average of all 5",
        tags=["rule:composite-score"],
        split="val",
    ),

    TaskRecord(
        id="vqa_val_output_files",
        project="visual-qa",
        intent=(
            "What are the two output files the visual-qa audit produces, and where are they saved?"
        ),
        reference_kind="exact",
        reference="auditdesign/audit-complet.md auditdesign/prompt-implementation.md",
        tags=["rule:output-files"],
        split="val",
    ),

    TaskRecord(
        id="vqa_val_evidence_required",
        project="visual-qa",
        intent=(
            "You want to give gpt-taste a score of 7/10 based on a general impression "
            "that the typography looks good. Is this acceptable?"
        ),
        reference_kind="exact",
        reference="screenshot css-scan evidence",
        tags=["rule:evidence-required"],
        split="val",
    ),

    TaskRecord(
        id="vqa_val_center_bias",
        project="visual-qa",
        intent=(
            "css-scan.js shows layoutAudit.centerBiasRisk = true. "
            "Which framework deducts score for this and by how much?"
        ),
        reference_kind="exact",
        reference="design-taste-frontend -2",
        tags=["rule:center-bias-deduction"],
        split="val",
    ),
]


def run(backend_name: str = "claude", nights: int = 3, edit_budget: int = 4) -> dict:
    """Run SkillOpt-Sleep on the visual-qa skill and return the consolidation report."""
    from skillopt_sleep.backend import ClaudeCliBackend, MockBackend
    from skillopt_sleep.dream import dream_consolidate

    skill = VISUAL_QA_SKILL_PATH.read_text(encoding="utf-8") if VISUAL_QA_SKILL_PATH.exists() else ""
    memory = ""  # no CLAUDE.md to evolve — only the skill

    if backend_name == "claude":
        backend = ClaudeCliBackend()
    else:
        backend = MockBackend()

    best_skill = skill
    trace = []
    holdout_baseline = None

    for night in range(1, nights + 1):
        result = dream_consolidate(
            backend=backend,
            tasks=TASKS,
            skill=best_skill,
            memory=memory,
            recall_k=5,
            dream_rollouts=2,
            edit_budget=edit_budget,
            gate_mode="on",
            evolve_skill=True,
            evolve_memory=False,
            night=night,
        )
        if holdout_baseline is None:
            holdout_baseline = result.holdout_baseline

        if result.accepted:
            best_skill = result.new_skill

        trace.append({
            "night": night,
            "baseline": result.baseline_score,
            "candidate": result.candidate_score,
            "holdout_before": result.holdout_baseline,
            "holdout_after": result.holdout_candidate,
            "accepted": result.accepted,
            "gate_action": result.gate_action,
            "n_edits": len(result.applied_edits),
            "edits": [
                {"op": e.op, "content": e.content[:120], "rationale": e.rationale[:120]}
                for e in result.applied_edits
            ],
        })
        print(
            f"Night {night}: holdout {result.holdout_baseline:.3f} -> {result.holdout_candidate:.3f}  "
            f"{'ACCEPTED' if result.accepted else 'REJECTED'}  "
            f"(+{len(result.applied_edits)} edits)",
            flush=True,
        )

    if best_skill != skill:
        VISUAL_QA_SKILL_PATH.write_text(best_skill, encoding="utf-8")
        print(f"\nBest skill written to {VISUAL_QA_SKILL_PATH}")
    else:
        print("\nNo net improvement beyond the gate threshold — skill unchanged")

    report = {
        "nights": nights,
        "backend": backend_name,
        "holdout_baseline": holdout_baseline,
        "holdout_final": trace[-1]["holdout_after"] if trace else holdout_baseline,
        "lift": (trace[-1]["holdout_after"] - holdout_baseline) if trace and holdout_baseline is not None else 0.0,
        "trace": trace,
    }

    out = VISUAL_QA_SKILL_PATH.parent / ".skillopt" / "report.json"
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Report saved to {out}")
    return report


if __name__ == "__main__":
    import argparse

    ap = argparse.ArgumentParser(description="SkillOpt benchmark for visual-qa skill")
    ap.add_argument("--backend", default="claude", choices=["claude", "mock"])
    ap.add_argument("--nights", type=int, default=3)
    ap.add_argument("--edit-budget", type=int, default=4)
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    res = run(args.backend, args.nights, args.edit_budget)

    if args.json:
        print(json.dumps(res, indent=2))
    else:
        print(f"\n=== RESULT ===")
        print(f"Backend : {res['backend']}")
        print(f"Nights  : {res['nights']}")
        print(f"Held-out: {res['holdout_baseline']:.3f} -> {res['holdout_final']:.3f}  lift={res['lift']:+.4f}")
