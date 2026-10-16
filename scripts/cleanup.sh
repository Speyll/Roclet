#!/bin/sh
cd ~/Projects/roc-mic

git switch -c "safety/pre-opencode-$(date +%Y%m%d-%H%M%S)"

# Update staged files to their current working-tree contents.
git add -u

# Add the wrapper and project scripts explicitly.
git add -- gradle/ gradlew gradlew.bat scripts/

# Validate the staged changes.
git diff --cached --check
git diff --cached --stat
git diff --cached --name-only
