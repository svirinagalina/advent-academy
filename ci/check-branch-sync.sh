#!/usr/bin/env bash
set -euo pipefail

[[ ${CI_PIPELINE_SOURCE:-} == merge_request_event ]] || exit 0
target=${CI_MERGE_REQUEST_TARGET_BRANCH_NAME:?}
source=${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME:?}
git fetch --no-tags origin "$target"
target_sha=$(git rev-parse FETCH_HEAD)
source_sha=${CI_MERGE_REQUEST_SOURCE_BRANCH_SHA:-${CI_COMMIT_SHA:?}}
promotion=false
if [[ $source =~ ^(dev|develop)$ && $target =~ ^(main|master)$ ]]; then
  promotion=true
fi
if ! git merge-base --is-ancestor "$target_sha" "$source_sha"; then
  echo "Source branch is behind $target."
  if [[ $promotion == true ]]; then
    echo "Merge the target branch into $source and push the result."
  else
    echo "Rebase it onto the target branch and force-push the result."
  fi
  exit 1
fi
if [[ $promotion == false ]] && [[ -n $(git rev-list --merges "$target_sha..$source_sha") ]]; then
  echo "Source branch contains merge commits after $target."
  git log --oneline --merges "$target_sha..$source_sha"
  exit 1
fi
