#!/usr/bin/env bash
# Delete the kind cluster and everything in it.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
kind delete cluster --name "$CLUSTER"
