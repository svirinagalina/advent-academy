#!/usr/bin/env bash
set -euo pipefail

# One selector for Gradle checks and Docker images. Never discovers unmerged/local modules.
modules=(user-service content-service meeting-service api-gateway)
declare -A selected=()

select_all() {
  printf '%s\n' "${modules[@]}"
}

# CI plans the diff in Alpine and passes it as an artifact to Java/Docker jobs.
if [[ ${1:-} == --artifact ]]; then
  if [[ -f .ci-state/modules.txt ]]; then
    cat .ci-state/modules.txt
    exit 0
  fi
  if [[ ${CI:-false} == true ]]; then
    echo "Missing module-selection artifact from branch-sync-check." >&2
    exit 1
  fi
fi

if [[ ${FULL_CI:-false} == true || ${CI_PIPELINE_SOURCE:-} == schedule ||
      ${CI_PIPELINE_SOURCE:-} == web || -n ${CI_COMMIT_TAG:-} ]]; then
  select_all
  exit 0
fi

if [[ ${1:-} == --paths ]]; then
  changed_paths_file=/dev/stdin
else
  if [[ ${CI_PIPELINE_SOURCE:-} == merge_request_event ]]; then
    diff_base=${CI_MERGE_REQUEST_DIFF_BASE_SHA:-}
  else
    diff_base=${CI_COMMIT_BEFORE_SHA:-}
  fi
  if [[ -z $diff_base || $diff_base =~ ^0+$ ]]; then
    select_all
    exit 0
  fi
  if ! git cat-file -e "$diff_base^{commit}" 2>/dev/null; then
    git fetch --no-tags --depth=1 origin "$diff_base"
  fi
  changed_paths_file=$(mktemp)
  trap 'rm -f "$changed_paths_file"' EXIT
  # Treat renames as delete+add so both the old and new module are checked.
  git diff --no-renames --name-only -z "$diff_base" "${CI_COMMIT_SHA:-HEAD}" > "$changed_paths_file"
fi

while IFS= read -r -d '' changed_path; do
  case "$changed_path" in
    .gitlab-ci.yml|ci/*|build.gradle|settings.gradle|gradle.properties|gradlew|gradlew.bat|gradle/*|platform/*|security-common/*|config/*|src/*|*/build.gradle|docker-compose*.yml|.dockerignore|docs/security/*|docs/adr/*)
      select_all
      exit 0
      ;;
    # Identity/token/API changes can affect every resource service and the gateway.
    user-service/src/main/java/*/adapter/rest/*|user-service/src/main/java/*/application/dto/*|user-service/src/main/java/*/application/port/JwtProvider.java|user-service/src/main/java/*/infrastructure/security/*|user-service/src/main/resources/application*.yml)
      select_all
      exit 0
      ;;
    content-service/src/main/java/*/adapter/rest/*|meeting-service/src/main/java/*/adapter/rest/*)
      selected[api-gateway]=1
      ;;
  esac
  for module in "${modules[@]}"; do
    if [[ $changed_path == "$module/"* ]]; then
      selected[$module]=1
    fi
  done
done < "$changed_paths_file"

for module in "${modules[@]}"; do
  if [[ ${selected[$module]:-0} == 1 ]]; then
    printf '%s\n' "$module"
  fi
done
