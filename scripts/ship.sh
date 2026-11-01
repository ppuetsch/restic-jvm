#!/usr/bin/env bash
# Usage: ship.sh "<PR title>"
# Pusht den aktuellen Branch, öffnet einen PR, wartet auf alle Checks und mergt nur, wenn alle grün sind.
set -euo pipefail

title="$1"
branch="$(git rev-parse --abbrev-ref HEAD)"

git push -q -u origin "$branch"
gh pr view "$branch" >/dev/null 2>&1 || gh pr create --title "$title" --body "Automated phase PR. CI must be green before merge." --base main --head "$branch" >/dev/null

# Wartet auf alle Checks des PRs (CI, CodeQL, SonarCloud), denn main verlangt sie alle grün.
sleep 15
if gh pr checks "$branch" --watch --fail-fast >/dev/null 2>&1; then
  gh pr checks "$branch" | cut -f1,2
  gh pr merge "$branch" --squash --delete-branch >/dev/null
  git checkout -q main
  git pull -q
  echo "merged $branch"
else
  gh pr checks "$branch" | cut -f1,2
  run_id="$(gh run list --branch "$branch" --commit "$(git rev-parse HEAD)" --workflow ci.yml --limit 1 --json databaseId -q '.[0].databaseId')"
  [ -n "$run_id" ] && gh run view "$run_id" --log-failed | tail -60
  exit 1
fi
