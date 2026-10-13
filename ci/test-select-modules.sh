#!/usr/bin/env bash
set -euo pipefail

unset FULL_CI CI_PIPELINE_SOURCE CI_COMMIT_TAG
all=$'user-service\ncontent-service\nmeeting-service\napi-gateway'

expect_modules() {
  local expected=$1
  shift
  local actual
  actual=$(printf '%s\0' "$@" | bash ci/select-modules.sh --paths)
  if [[ $actual != "$expected" ]]; then
    printf 'Unexpected selection for %s\nExpected:\n%s\nActual:\n%s\n' "$*" "$expected" "$actual" >&2
    exit 1
  fi
}

expect_modules '' README.md docs/meeting-service.md
expect_modules '' frontend/src/app/page.tsx frontend/Dockerfile
expect_modules user-service user-service/src/main/resources/db/migration/V12__add_durable_refresh_tokens.sql
expect_modules user-service user-service/src/test/java/com/example/AuthSecurityIT.java
expect_modules content-service content-service/src/main/java/com/example/application/service/PublishCourse.java
expect_modules meeting-service meeting-service/src/main/resources/static/js/app.js
expect_modules api-gateway api-gateway/src/main/resources/application.yml
expect_modules "$all" user-service/src/main/java/com/example/infrastructure/security/JjwtProvider.java
expect_modules "$all" user-service/src/main/java/com/example/adapter/rest/controller/AuthController.java
expect_modules "$all" user-service/src/main/java/com/example/application/dto/response/AuthResponse.java
expect_modules "$all" docs/security/authorization-matrix.csv
expect_modules "$all" platform/build.gradle
expect_modules "$all" gradle/libs.versions.toml
expect_modules "$all" user-service/build.gradle
expect_modules "$all" .gitlab-ci.yml
expect_modules "$all" docker-compose.yml
expect_modules user-service user-service/Dockerfile
expect_modules $'content-service\napi-gateway' content-service/src/main/java/com/example/adapter/rest/controller/CourseController.java
expect_modules $'user-service\nmeeting-service' user-service/src/test/java/com/example/AuthTest.java meeting-service/src/test/java/com/example/RoomTest.java
expect_modules user-service 'user-service/src/test/java/com/example/Test with spaces.java'

[[ $(FULL_CI=true bash ci/select-modules.sh --paths </dev/null) == "$all" ]]
[[ $(CI_PIPELINE_SOURCE=schedule bash ci/select-modules.sh --paths </dev/null) == "$all" ]]
[[ $(CI_PIPELINE_SOURCE=web bash ci/select-modules.sh --paths </dev/null) == "$all" ]]
[[ $(CI_COMMIT_TAG=v1.0.0 bash ci/select-modules.sh --paths </dev/null) == "$all" ]]
[[ $(CI_COMMIT_BEFORE_SHA=0000000000000000000000000000000000000000 bash ci/select-modules.sh) == "$all" ]]

echo "25 module-selection scenarios passed."
