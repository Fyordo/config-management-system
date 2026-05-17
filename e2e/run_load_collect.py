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


def main() -> int:
    cmd = ["wrk2", "-t5", "-c20", "-d60s", "-R50", "-s", str(WRK_LUA), WRK_URL]
    return collect_iterations(
        30,
        lambda: run_wrk(cmd),
        lambda i: E2E_RESULTS / f"result_load_t1_c1_{i}.txt",
        "load 60s @ 50 RPS",
    )


if __name__ == "__main__":
    run_cli(main)
