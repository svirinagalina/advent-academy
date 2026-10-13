#!/usr/bin/env bash
set -euo pipefail

# Capture first: a selector/Git error must fail the job, not silently skip tests.
selected_modules=$(bash ci/select-modules.sh --artifact)
if [[ -z $selected_modules ]]; then
  echo "No affected backend modules."
  exit 0
fi

tasks=()
while IFS= read -r module; do
  echo "Checking $module (including its standard integration/security tests)."
  tasks+=(":$module:check" ":$module:assemble")
done <<< "$selected_modules"

# Root Checkstyle is cheap; SpotBugs belongs to full-check. Module :check owns its own checks.
tasks+=(:checkstyleMain :checkstyleTest)
./gradlew "${tasks[@]}" --no-daemon --stacktrace
