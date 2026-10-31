#!/usr/bin/env bash
# Usage: ship.sh "<PR title>"
# Pusht den aktuellen Branch, öffnet einen PR, wartet auf die CI und mergt nur bei grünem Lauf.
set -euo pipefail

title="$1"
branch="$(git rev-parse --abbrev-ref HEAD)"

git push -q -u origin "$branch"
gh pr view "$branch" >/dev/null 2>&1 || gh pr create --title "$title" --body "Automated phase PR. CI must be green before merge." --base main --head "$branch" >/dev/null

sleep 15
head_sha="$(git rev-parse HEAD)"
run_id="$(gh run list --branch "$branch" --commit "$head_sha" --limit 1 --json databaseId -q '.[0].databaseId')"
if [ -z "$run_id" ]; then echo "no CI run found for $head_sha" >&2; exit 1; fi

if gh run watch "$run_id" --exit-status >/dev/null 2>&1; then
  gh run view "$run_id" --json jobs -q '.jobs[] | .name + ": " + .conclusion'
  gh pr merge "$branch" --squash --delete-branch >/dev/null
  git checkout -q main
  git pull -q
  echo "merged $branch"
else
  gh run view "$run_id" --json jobs -q '.jobs[] | .name + ": " + .conclusion'
  gh run view "$run_id" --log-failed | tail -60
  exit 1
fi
