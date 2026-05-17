#!/usr/bin/env python3

from applied_collect_common import (
    DEFAULT_ITERATIONS,
    E2E_RESULTS,
    WRK_LUA,
    WRK_URL,
    collect_iterations,
    run_cli,
    run_wrk,
)

_PHASES = (
    ("10s", "5", "1"),
    ("5s", "150", "100"),
    ("15s", "5", "1"),
)


def _burst_wrk() -> str:
    parts: list[str] = []
    for duration, rate, conn in _PHASES:
        cmd = [
            "wrk2", "-t1", "-c", conn, "-d", duration, "-R", rate,
            "-s", str(WRK_LUA), WRK_URL,
        ]
        print(f"  phase {duration} {rate}RPS c={conn}")
        parts.append(run_wrk(cmd))
    return "\n".join(parts)


def main() -> int:
    return collect_iterations(
        DEFAULT_ITERATIONS,
        _burst_wrk,
        lambda i: E2E_RESULTS / f"result_burst_t1_c1_{i}.txt",
        "burst (multi-phase wrk2)",
    )


if __name__ == "__main__":
    run_cli(main)
