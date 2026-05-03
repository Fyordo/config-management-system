CMS_URL ?= http://127.0.0.1:8888
PYTHON ?= python3

.PHONY: run-e2e analyze kill-e2e cleanup

run-e2e:
	bash run_all_e2e.sh

analyze:
	$(PYTHON) calc_results.py

kill-e2e:
	pkill -f wrk2 || true
	pkill -f 'e2e/run_low_collect\.py' || true
	pkill -f 'e2e/run_5rps_collect\.py' || true
	pkill -f 'e2e/run_burst_collect\.py' || true
	pkill -f 'e2e/run_load_collect\.py' || true

cleanup:
	i=1; \
	while [ $$i -le 500 ]; do \
	  curl -sS -X POST '$(CMS_URL)/v1/property/modify/delete' \
	    -H 'Content-Type: application/json' \
	    -d "{\"version\":1,\"namespace\":\"dev\",\"service\":\"cms-j-sset\",\"appId\":\"cms-j-sset-0\",\"key\":\"app.e2e.p$$i\"}" \
	    -o /dev/null || true; \
	  i=$$((i + 1)); \
	done
