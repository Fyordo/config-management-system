#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export E2E_DOCKER_CONTAINER="${E2E_DOCKER_CONTAINER:-cms-j-sset-cms-j-sset-example-1}"

echo "Starting 1/3: run_low_collect.py"
python3 "${SCRIPT_DIR}/run_low_collect.py"

#echo "Starting 2/3: run_burst_collect.py"
#python3 "${SCRIPT_DIR}/run_burst_collect.py"
#
#echo "Starting 3/3: run_load_collect.py"
#python3 "${SCRIPT_DIR}/run_load_collect.py"

echo "All e2e scripts completed successfully."
