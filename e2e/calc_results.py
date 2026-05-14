#!/usr/bin/env python3
"""Build e2e/results summary and plots. Requires: numpy, matplotlib."""

from __future__ import annotations

import math
import re
import sys
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path

try:
    import matplotlib.pyplot as plt
    import numpy as np
except ImportError:
    print("pip install numpy matplotlib", file=sys.stderr)
    raise SystemExit(1) from None

SCRIPT_DIR = Path(__file__).resolve().parent
RESULTS_DIR = SCRIPT_DIR / "results"
PLOTS_DIR = RESULTS_DIR / "plots"
REPORT_PATH = RESULTS_DIR / "analysis_summary.md"

RESULT_HEADER_RE = re.compile(r"^(\d+),(\d+)\s*$")
RESULT_LINE_RE = re.compile(r"^\[[^\]]+\],(\d+)\s*$")
PLAIN_MS_RE = re.compile(r"^(\d+)\s*$")

TEST_SPECS: dict[str, str] = {
    "5RPS": "result_5RPS_t1_c1_*.txt",
    "burst": "result_burst_t1_c1_*.txt",
    "load": "result_load_t1_c1_*.txt",
}


@dataclass(frozen=True)
class TestTypeStats:
    label: str
    run_count: int
    runs_with_samples: int
    total_samples: int
    files_with_wrk_header: int
    total_wrk_requests: int
    total_applied_log_lines: int
    request_loss_pct: float
    mean_p50: float
    mean_p90: float
    mean_p99: float
    ci95_p50_lo: float
    ci95_p50_hi: float
    ci95_p90_lo: float
    ci95_p90_hi: float
    ci95_p99_lo: float
    ci95_p99_hi: float
    pooled_min: float
    pooled_max: float
    per_run_p50: list[float]
    per_run_p90: list[float]
    per_run_p99: list[float]


def parse_result_file(path: Path) -> tuple[list[int], int | None, int | None]:
    raw = path.read_text(encoding="utf-8").splitlines()
    wrk_h: int | None = None
    log_h: int | None = None
    start = 0
    if raw:
        hm = RESULT_HEADER_RE.match(raw[0].strip())
        if hm:
            wrk_h, log_h = int(hm.group(1)), int(hm.group(2))
            start = 1
    vals: list[int] = []
    for line in raw[start:]:
        line = line.strip()
        if not line:
            continue
        m = RESULT_LINE_RE.match(line)
        if m:
            vals.append(int(m.group(1)))
        elif (m2 := PLAIN_MS_RE.match(line)):
            vals.append(int(m2.group(1)))
    return vals, wrk_h, log_h


def percentile_linear(sorted_vals: Sequence[int], q: float) -> float:
    if not sorted_vals:
        return float("nan")
    if len(sorted_vals) == 1:
        return float(sorted_vals[0])
    pos = (len(sorted_vals) - 1) * (q / 100.0)
    lo, hi = int(math.floor(pos)), int(math.ceil(pos))
    if lo == hi:
        return float(sorted_vals[lo])
    w = pos - lo
    return float(sorted_vals[lo]) * (1.0 - w) + float(sorted_vals[hi]) * w


def percentiles_for_run(ms: list[int]) -> tuple[float, float, float]:
    s = sorted(ms)
    return percentile_linear(s, 50), percentile_linear(s, 90), percentile_linear(s, 99)


def mean_ci95(values: Sequence[float]) -> tuple[float, float]:
    n = len(values)
    if n < 2:
        return float("nan"), float("nan")
    arr = np.asarray(values, dtype=np.float64)
    mean = float(np.mean(arr))
    std = float(np.std(arr, ddof=1))
    margin = 1.96 * std / math.sqrt(n)
    # Latency cannot be negative, so we clip the lower bound at 0.0
    return max(0.0, mean - margin), mean + margin


def analyze_test_type(label: str, pattern: str) -> TestTypeStats:
    paths = sorted(RESULTS_DIR.glob(pattern), key=lambda p: p.name)
    p50s: list[float] = []
    p90s: list[float] = []
    p99s: list[float] = []
    pooled: list[int] = []
    n_hdr = 0
    sum_wrk = sum_log = 0

    for p in paths:
        ms, hw, hl = parse_result_file(p)
        if hw is not None and hl is not None:
            n_hdr += 1
            sum_wrk += hw
            sum_log += hl
        if not ms:
            continue
        a, b, c = percentiles_for_run(ms)
        p50s.append(a)
        p90s.append(b)
        p99s.append(c)
        pooled.extend(ms)

    n_ok = len(p50s)
    loss = (
        max(0.0, (sum_wrk - sum_log) / sum_wrk * 100.0)
        if sum_wrk > 0
        else float("nan")
    )

    if n_ok == 0:
        return TestTypeStats(
            label, len(paths), 0, 0, n_hdr, sum_wrk, sum_log, loss,
            float("nan"), float("nan"), float("nan"),
            float("nan"), float("nan"), float("nan"), float("nan"), float("nan"), float("nan"),
            float("nan"), float("nan"),
            [], [], [],
        )
    ci_p50 = mean_ci95(p50s)
    ci_p90 = mean_ci95(p90s)
    ci_p99 = mean_ci95(p99s)
    return TestTypeStats(
        label,
        len(paths),
        n_ok,
        len(pooled),
        n_hdr,
        sum_wrk,
        sum_log,
        loss,
        float(np.mean(p50s)),
        float(np.mean(p90s)),
        float(np.mean(p99s)),
        ci_p50[0],
        ci_p50[1],
        ci_p90[0],
        ci_p90[1],
        ci_p99[0],
        ci_p99[1],
        float(min(pooled)),
        float(max(pooled)),
        p50s,
        p90s,
        p99s,
    )


def collect_all_series() -> dict[str, list[int]]:
    out: dict[str, list[int]] = {}
    for label, pattern in TEST_SPECS.items():
        acc: list[int] = []
        for p in sorted(RESULTS_DIR.glob(pattern)):
            acc.extend(parse_result_file(p)[0])
        out[label] = acc
    return out


def _fmt(x: float) -> str:
    return "n/a" if math.isnan(x) else f"{x:.2f}"


def plot_histograms(series: dict[str, list[int]], out: Path) -> None:
    fig, axes = plt.subplots(1, 3, figsize=(14, 4))
    for ax, (label, vals) in zip(axes, series.items(), strict=True):
        if vals:
            ax.hist(vals, bins=50, color="steelblue", edgecolor="white", alpha=0.85)
            for q, c in ((50, "darkorange"), (90, "green"), (99, "red")):
                ax.axvline(np.percentile(vals, q), color=c, linestyle="--", label=f"p{q}")
            ax.legend(fontsize=8)
        ax.set_title(label)
        ax.set_xlabel("Latency (ms)")
        ax.set_ylabel("Count")
    fig.suptitle("Latency by test type (all runs)")
    fig.tight_layout()
    fig.savefig(out, dpi=150)
    plt.close(fig)


def plot_ecdf(series: dict[str, list[int]], out: Path) -> None:
    fig, ax = plt.subplots(figsize=(8, 5))
    for i, (label, vals) in enumerate(series.items()):
        if not vals:
            continue
        s = np.sort(np.asarray(vals, dtype=np.float64))
        ax.step(s, np.arange(1, len(s) + 1) / len(s), where="post", label=f"{label} (n={len(s)})", color=f"C{i}")
    ax.set_xlabel("Latency (ms)")
    ax.set_ylabel("CDF")
    ax.set_title("Latency ECDF")
    ax.legend()
    ax.grid(True, alpha=0.3)
    fig.tight_layout()
    fig.savefig(out, dpi=150)
    plt.close(fig)


def plot_per_run(stats: dict[str, TestTypeStats], out: Path) -> None:
    fig, axes = plt.subplots(3, 1, figsize=(10, 9))
    for ax, (label, st) in zip(axes, stats.items(), strict=True):
        if not st.per_run_p50:
            ax.set_title(f"{label}: no data")
            continue
        x = np.arange(1, len(st.per_run_p50) + 1)
        ax.plot(x, st.per_run_p50, "o-", ms=3, label="p50")
        ax.plot(x, st.per_run_p90, "o-", ms=3, label="p90")
        ax.plot(x, st.per_run_p99, "o-", ms=3, label="p99")
        ax.axhline(st.mean_p50, color="C0", ls=":", alpha=0.7)
        ax.axhline(st.mean_p90, color="C1", ls=":", alpha=0.7)
        ax.axhline(st.mean_p99, color="C2", ls=":", alpha=0.7)
        ax.set_ylabel("ms")
        ax.set_title(label)
        ax.legend(fontsize=7, ncol=2)
        ax.grid(True, alpha=0.3)
        ax.set_xlabel("run index")
    fig.suptitle("Per-run percentiles")
    fig.tight_layout()
    fig.savefig(out, dpi=150)
    plt.close(fig)


def write_report(stats: dict[str, TestTypeStats], plots: list[str]) -> None:
    lines = [
        "# e2e latency summary (ms)",
        "",
        "Header line: `wrk_requests,applied_log_lines`. Loss ≈ max(0, (Σwrk−Σapplied)/Σwrk)×100%.",
        "",
    ]
    for label, st in stats.items():
        loss = (
            "- Loss: **n/a** (no header rows)"
            if st.files_with_wrk_header == 0
            else f"- Loss: **{_fmt(st.request_loss_pct)}%** (wrk {st.total_wrk_requests}, applied {st.total_applied_log_lines})"
        )
        if st.files_with_wrk_header and math.isnan(st.request_loss_pct):
            loss = "- Loss: **n/a**"
        lines += [
            f"## {label}",
            loss,
            f"- Files {st.run_count}, samples {st.total_samples}, mean p50/p90/p99: "
            f"{_fmt(st.mean_p50)} / {_fmt(st.mean_p90)} / {_fmt(st.mean_p99)} ms, "
            f"min/max {_fmt(st.pooled_min)} / {_fmt(st.pooled_max)}",
            f"- 95% CI (mean per-run): p50 [{_fmt(st.ci95_p50_lo)}, {_fmt(st.ci95_p50_hi)}], "
            f"p90 [{_fmt(st.ci95_p90_lo)}, {_fmt(st.ci95_p90_hi)}], "
            f"p99 [{_fmt(st.ci95_p99_lo)}, {_fmt(st.ci95_p99_hi)}] ms",
            "",
        ]
    lines += ["## Plots", ""]
    lines += [f"![{p}]({p})" for p in plots]
    lines.append("")
    REPORT_PATH.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    if not RESULTS_DIR.is_dir():
        print(f"Missing: {RESULTS_DIR}", file=sys.stderr)
        return 1

    PLOTS_DIR.mkdir(parents=True, exist_ok=True)
    by_label = {lb: analyze_test_type(lb, pat) for lb, pat in TEST_SPECS.items()}
    series = collect_all_series()

    h = PLOTS_DIR / "latency_histograms_by_type.png"
    e = PLOTS_DIR / "latency_ecdf_by_type.png"
    p = PLOTS_DIR / "per_run_percentiles.png"
    plot_histograms(series, h)
    plot_ecdf(series, e)
    plot_per_run(by_label, p)

    rel = [x.relative_to(RESULTS_DIR).as_posix() for x in (h, e, p)]
    write_report(by_label, rel)
    print(f"{REPORT_PATH}\n{PLOTS_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
