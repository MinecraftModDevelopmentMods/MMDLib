#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 1 ]]; then
  echo "usage: $0 <destination-maven-repository>" >&2
  exit 2
fi
python3 gradle/stage-legacy-dependencies.py "$1"
