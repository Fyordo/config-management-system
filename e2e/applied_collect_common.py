"""Shared helpers for e2e load collection and log parsing."""

from __future__ import annotations

import datetime as dt
import os
import re
import subprocess
import sys
import time
from collections.abc import Callable
from pathlib import Path

E2E_DIR = Path(__file__).resolve().parent
E2E_RESULTS = E2E_DIR / "results"
WRK_LUA = E2E_DIR / "wrk2" / "property_modify_put.lua"
WRK_URL = "http://127.0.0.1:8888"
DEFAULT_ITERATIONS = 50
COOLDOWN_SECONDS_AFTER_ITERATION = 30

TARGET_PHRASE = "Applied property [app.e2e.p"
APPLIED_LINE_RE = re.compile(
    r"Applied property \[app\.e2e\.p\d+\] for.*?(\d+(?:\.\d+)?)\s*ms\b",
    re.IGNORECASE,
)
WRK_REQUESTS_RE = re.compile(r"^\s*(\d+)\s+requests\s+in\s+", re.MULTILINE)

_K8S_MARKERS = (" stdout F ", " stderr F ")
_LINE_TS_START = re.compile(
    r"^(?P<y>\d{4})-(?P<mo>\d{2})-(?P<d>\d{2})T(?P<H>\d{2}):(?P<M>\d{2}):(?P<S>\d{2})"
    r"(?:\.\d+)?Z?\b"
)
_LOG_TS = re.compile(
    r"(?P<y>\d{4})-(?P<mo>\d{2})-(?P<d>\d{2})"
    r"[T ](?P<H>\d{2}):(?P<M>\d{2}):(?P<S>\d{2})"
    r"(?:[.,]\d+)?(?:Z|[+-]\d{2}(?::?\d{2})?)?"
)

DEFAULT_E2E_DOCKER_CONTAINER = os.environ.get(
    "E2E_DOCKER_CONTAINER",
    "cms-j-sset-cms-j-sset-example-1",
)


def now_rfc3339_utc() -> str:
    return dt.datetime.now(dt.UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def run_wrk(cmd: list[str]) -> str:
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(
            f"Command failed ({r.returncode}): {' '.join(cmd)}\n{r.stdout}\n{r.stderr}"
        )
    return f"{r.stdout or ''}\n{r.stderr or ''}"


def parse_wrk_total_requests(wrk_output: str) -> int:
    if not wrk_output:
        return 0
    return sum(int(m.group(1)) for m in WRK_REQUESTS_RE.finditer(wrk_output))


def format_results_txt_header(wrk_requests: int, log_lines_found: int) -> str:
    return f"{wrk_requests},{log_lines_found}\n"


def _payload_after_k8s_marker(line: str) -> str:
    for m in _K8S_MARKERS:
        if m in line:
            return line.split(m, 1)[1]
    return line


def parse_log_timestamp_display(line: str) -> str | None:
    stripped = line.strip()
    m0 = _LINE_TS_START.match(stripped)
    if m0:
        g = m0.groupdict()
        return f"{g['y']}-{g['mo']}-{g['d']}  {g['H']}:{g['M']}:{g['S']}"
    payload = _payload_after_k8s_marker(line)
    m = _LOG_TS.search(payload)
    if not m:
        return None
    g = m.groupdict()
    return f"{g['y']}-{g['mo']}-{g['d']}  {g['H']}:{g['M']}:{g['S']}"


def format_result_line(line: str, ms_raw: str) -> str | None:
    ts = parse_log_timestamp_display(line)
    if ts is None:
        return None
    return f"[{ts}],{int(round(float(ms_raw)))}"


def fetch_docker_logs_since(container: str, since_time: str) -> str:
    r = subprocess.run(
        ["docker", "logs", "--timestamps", "--since", since_time, container],
        capture_output=True,
        text=True,
    )
    if r.returncode != 0:
        raise RuntimeError(
            f"docker logs failed ({r.returncode}): {container} --since {since_time!r}\n"
            f"{r.stdout}\n{r.stderr}"
        )
    return r.stdout


def collect_formatted_lines_from_docker(
    since_time: str, container: str | None = None
) -> tuple[list[str], int, int, int]:
    c = container or DEFAULT_E2E_DOCKER_CONTAINER
    return collect_formatted_lines(fetch_docker_logs_since(c, since_time))


def collect_formatted_lines(log_text: str) -> tuple[list[str], int, int, int]:
    all_lines = log_text.splitlines()
    hits = [ln for ln in all_lines if TARGET_PHRASE in ln]
    out: list[str] = []
    parsed = 0
    for line in hits:
        m = APPLIED_LINE_RE.search(line)
        if not m:
            continue
        row = format_result_line(line, m.group(1))
        if row:
            out.append(row)
            parsed += 1
    return out, len(all_lines), len(hits), parsed


def collect_iterations(
    iterations: int,
    wrk_fn: Callable[[], str],
    out_file: Callable[[int], Path],
    line1: str,
) -> int:
    E2E_RESULTS.mkdir(parents=True, exist_ok=True)
    for i in range(1, iterations + 1):
        print(f"[{i}/{iterations}] {line1}")
        since = now_rfc3339_utc()
        text = wrk_fn()
        n_req = parse_wrk_total_requests(text)
        if n_req == 0:
            print(f"[{i}/{iterations}] WARN: wrk2 request count not parsed.")
        print(f"[{i}/{iterations}] docker logs {DEFAULT_E2E_DOCKER_CONTAINER!r}")
        rows, n_log, n_applied, _ = collect_formatted_lines_from_docker(since)
        path = out_file(i)
        body = "\n".join(rows) + ("\n" if rows else "")
        path.write_text(format_results_txt_header(n_req, n_applied) + body, encoding="utf-8")
        print(
            f"[{i}/{iterations}] wrk={n_req} applied={n_applied} rows={len(rows)} "
            f"log_lines={n_log} -> {path}"
        )
        if i < iterations:
            print(
                f"[{i}/{iterations}] cooling down {COOLDOWN_SECONDS_AFTER_ITERATION}s "
                "before next iteration..."
            )
            time.sleep(COOLDOWN_SECONDS_AFTER_ITERATION)
    print("Done.")
    return 0


def run_cli(main_fn: Callable[[], int]) -> None:
    try:
        raise SystemExit(main_fn())
    except KeyboardInterrupt:
        print("\nInterrupted.", file=sys.stderr)
        raise SystemExit(130) from None
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1) from None
