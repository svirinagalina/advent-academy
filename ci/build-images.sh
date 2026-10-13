#!/usr/bin/env bash
set -euo pipefail

selected_modules=$(bash ci/select-modules.sh --artifact)
if [[ -z $selected_modules ]]; then
  echo "No affected backend images."
  exit 0
fi

while IFS= read -r module; do
  dockerfile="$module/Dockerfile"
  if [[ ! -f $dockerfile ]]; then
    echo "Missing Dockerfile: $dockerfile" >&2
    exit 1
  fi
  image_name="${CI_PROJECT_PATH_SLUG:?CI_PROJECT_PATH_SLUG is required}-$module:${CI_COMMIT_SHORT_SHA:?CI_COMMIT_SHORT_SHA is required}"
  echo "Building $image_name"
  docker build -f "$dockerfile" -t "$image_name" .
done <<< "$selected_modules"
