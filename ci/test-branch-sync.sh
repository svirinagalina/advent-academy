#!/usr/bin/env bash
set -euo pipefail
checker=$(cd "$(dirname "$0")" && pwd)/check-branch-sync.sh
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
git init -q "$fixture/origin"
cd "$fixture/origin"
git config user.name 'CI fixture'
git config user.email 'ci@example.invalid'
git commit -q --allow-empty -m base
git branch -M main
base=$(git rev-parse HEAD)
git checkout -qb feature
git commit -q --allow-empty -m feature
linear=$(git rev-parse HEAD)
git checkout -qb side "$base"
git commit -q --allow-empty -m side
git checkout -q feature
git merge -q --no-ff side -m merge
merged=$(git rev-parse HEAD)
git checkout -q main
git commit -q --allow-empty -m target
advanced=$(git rev-parse HEAD)
git branch dev "$merged"
git checkout -q dev
git merge -q --no-ff main -m sync
synced=$(git rev-parse HEAD)
git clone -q "$fixture/origin" "$fixture/clone"
cd "$fixture/clone"
check() {
  local expected=$1 label=$2 source=$3 target=$4 sha=$5
  git -C "$fixture/origin" branch -f "$target" "$6"
  local actual=0
  CI_PIPELINE_SOURCE=merge_request_event CI_MERGE_REQUEST_SOURCE_BRANCH_NAME="$source" \
    CI_MERGE_REQUEST_TARGET_BRANCH_NAME="$target" CI_COMMIT_SHA="$sha" \
    CI_MERGE_REQUEST_SOURCE_BRANCH_SHA="${7:-}" bash "$checker" > "$fixture/output" 2>&1 || actual=$?
  if [[ $actual != "$expected" ]]; then
    cat "$fixture/output"
    echo "FAIL: $label (exit $actual, expected $expected)"
    exit 1
  fi
  echo "PASS: $label"
}
check 0 'linear feature' feature main "$linear" "$base"
check 1 'stale feature' feature main "$linear" "$advanced"
check 1 'feature merge rejected' feature main "$merged" "$base"
check 1 'stale dev promotion' dev main "$merged" "$advanced"
check 0 'synced dev promotion' dev main "$synced" "$advanced"
check 1 'merged pipeline checks actual source SHA' dev main "$synced" "$advanced" "$merged"
check 0 'develop to master promotion' develop master "$synced" "$advanced"
check 1 'dev to non-release target remains strict' dev develop "$synced" "$advanced"
CI_PIPELINE_SOURCE=push bash "$checker"
echo 'PASS: push skips MR guard'
