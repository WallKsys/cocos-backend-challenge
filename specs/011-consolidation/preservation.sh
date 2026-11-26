#!/usr/bin/env bash
# Preservation evidence of spec 011 (plan.md § Preservation evidence).
#
# Usage, from the repository root, right after a green `./mvnw clean verify`:
#   specs/011-consolidation/preservation.sh            compare the build with the baseline
#   specs/011-consolidation/preservation.sh --record   write the baseline (spec author only)
#
# It compares two things with the baseline recorded from v1.0.0:
#   1. SQL texts: every String constant of the persistence adapters (javap -constants on target/classes).
#      Constant names may change; texts may not.
#   2. Executed tests: the names of every test case in the Surefire and Failsafe reports, with repetitions.
#      Test classes may be renamed or merged; the test methods that run may not change.
# Without `clean`, target/ keeps classes and reports of deleted sources and the comparison is wrong.
#
# Exit codes: 0 = identical to the baseline; 1 = differences (printed); 2 = usage error or missing build.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
BASELINE="$DIR/baseline"
CLASSES="$ROOT/target/classes/com/cocos/challenge/infrastructure/adapter/out/persistence"

if [[ ! -d "$CLASSES" ]] || ! ls "$ROOT"/target/failsafe-reports/TEST-*.xml >/dev/null 2>&1; then
  echo "ERROR: no build found. Run ./mvnw clean verify first." >&2
  exit 2
fi

sql_texts() {
  find "$CLASSES" -name '*.class' | LC_ALL=C sort | while read -r class; do
    javap -p -constants "$class"
  done | sed -n -E 's/^ *((private|protected|public) )?static final java\.lang\.String [A-Za-z0-9_$]+ = (.*);$/\3/p' \
       | LC_ALL=C sort -u
}

test_names() {
  cat "$ROOT"/target/surefire-reports/TEST-*.xml "$ROOT"/target/failsafe-reports/TEST-*.xml \
    | grep -o '<testcase name="[^"]*"' \
    | sed -E 's/^<testcase name="(.*)"$/\1/' \
    | LC_ALL=C sort
}

case "${1:-}" in
  --record)
    mkdir -p "$BASELINE"
    sql_texts > "$BASELINE/sql-texts.txt"
    test_names > "$BASELINE/test-names.txt"
    echo "Baseline written: $(wc -l < "$BASELINE/sql-texts.txt" | tr -d ' ') SQL texts," \
         "$(wc -l < "$BASELINE/test-names.txt" | tr -d ' ') test cases."
    exit 0
    ;;
  "") ;;
  *) sed -n '2,15p' "$0"; exit 2 ;;
esac

STATUS=0
echo "Spec 011 preservation check (baseline: specs/011-consolidation/baseline/)"
echo "--------------------------------------------------------"
if SQL_DIFF=$(diff "$BASELINE/sql-texts.txt" <(sql_texts)); then
  echo "SQL texts:  identical ($(wc -l < "$BASELINE/sql-texts.txt" | tr -d ' ') texts)"
else
  echo "SQL texts:  DIFFERENT (< baseline, > this build)"
  echo "$SQL_DIFF"
  STATUS=1
fi
if TEST_DIFF=$(diff "$BASELINE/test-names.txt" <(test_names)); then
  echo "Test cases: identical ($(wc -l < "$BASELINE/test-names.txt" | tr -d ' ') test cases)"
else
  echo "Test cases: DIFFERENT (< baseline, > this build)"
  echo "$TEST_DIFF"
  STATUS=1
fi
echo "--------------------------------------------------------"
if (( STATUS == 0 )); then
  echo "OK: no difference with the baseline."
else
  echo "FAILED: the slice changed SQL or the set of executed tests. Stop and report."
fi
exit "$STATUS"
