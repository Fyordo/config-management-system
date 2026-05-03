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
    cmd = ["wrk2", "-t1", "-c1", "-d120s", "-R5", "-s", str(WRK_LUA), WRK_URL]
    return collect_iterations(
        DEFAULT_ITERATIONS,
        lambda: run_wrk(cmd),
        lambda i: E2E_RESULTS / f"result_5RPS_t1_c1_{i}.txt",
        "wrk2 5 RPS",
    )


if __name__ == "__main__":
    run_cli(main)
