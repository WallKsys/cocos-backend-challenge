#!/usr/bin/env bash
# Checks the size of a pull request against the 400-line limit (Constitution Principle X, TEC-30).
#
# Usage:
#   scripts/check-pr-size.sh [BASE_REF] [--body-file FILE]
#
#   BASE_REF     Branch the PR targets. Default: origin/main if it exists, otherwise main.
#   --body-file  File with the PR description. When the size exceeds the limit, the check passes only
#                if the description contains a non-empty "## Size justification" section.
#
# Counted: added + deleted lines of every changed file, except the exclusions below.
# Exit codes: 0 = within limit, or over limit with justification; 1 = over limit without justification;
#             2 = usage error.
set -euo pipefail

LIMIT=400
BASE_REF=""
BODY_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --body-file) BODY_FILE="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) BASE_REF="$1"; shift ;;
  esac
done

if [[ -z "$BASE_REF" ]]; then
  if git rev-parse --verify --quiet origin/main >/dev/null; then BASE_REF="origin/main"; else BASE_REF="main"; fi
fi
if ! git rev-parse --verify --quiet "$BASE_REF" >/dev/null; then
  echo "ERROR: base ref '$BASE_REF' not found." >&2
  exit 2
fi

# Paths that are not reviewed line by line (TEC-30):
#  - tool-generated or vendored files: Maven Wrapper, Spec Kit, OpenCode
#  - V1 migration: verbatim copy of the database.sql provided by the challenge
#  - spec artifacts written before implementation: specs/, Gherkin features, docs, Markdown
EXCLUDES=(
  ':(exclude)mvnw'
  ':(exclude)mvnw.cmd'
  ':(exclude).mvn/**'
  ':(exclude).specify/**'
  ':(exclude).opencode/**'
  ':(exclude)specs/**'
  ':(exclude)docs/**'
  ':(exclude)src/test/resources/features/**'
  ':(exclude)src/main/resources/db/migration/V1__*'
  ':(exclude)*.md'
)

MERGE_BASE=$(git merge-base "$BASE_REF" HEAD)

TOTAL=0
DETAIL=""
while IFS=$'\t' read -r added deleted path; do
  [[ -z "${path:-}" ]] && continue
  # Binary files are reported as "-": they count as 0 lines.
  [[ "$added" == "-" ]] && added=0
  [[ "$deleted" == "-" ]] && deleted=0
  lines=$((added + deleted))
  TOTAL=$((TOTAL + lines))
  DETAIL+=$(printf '%6d  %s' "$lines" "$path")$'\n'
done < <(git diff --numstat "$MERGE_BASE" HEAD -- . "${EXCLUDES[@]}")

echo "PR size check (limit: $LIMIT lines, base: $BASE_REF)"
echo "--------------------------------------------------------"
printf '%s' "$DETAIL"
echo "--------------------------------------------------------"
echo "Total counted lines: $TOTAL"

if (( TOTAL <= LIMIT )); then
  echo "OK: within the limit."
  exit 0
fi

echo "Over the limit by $((TOTAL - LIMIT)) lines."

if [[ -n "$BODY_FILE" && -f "$BODY_FILE" ]]; then
  # Text of the "## Size justification" section: from its heading to the next "## " heading,
  # ignoring HTML comments (including multi-line ones), blank lines and placeholders such as "N/A".
  JUSTIFICATION=$(awk '
    /^## Size justification/ { inside=1; next }
    inside && /^## / { inside=0 }
    inside { print }
  ' "$BODY_FILE" | perl -0777 -pe 's/<!--.*?-->//gs' | grep -v -E '^[[:space:]]*$' | grep -v -i -E '^[[:space:]]*(n/?a|none|-)[[:space:]]*$' || true)
  if [[ -n "$JUSTIFICATION" ]]; then
    echo "ACCEPTED FOR REVIEW: the PR includes a size justification. The reviewer decides whether it is valid."
    exit 0
  fi
fi

echo "FAILED: split the change into smaller PRs with a logical separation, or add a"
echo "'## Size justification' section to the PR description explaining why it cannot be smaller."
exit 1
