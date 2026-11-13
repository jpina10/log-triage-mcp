# Shared settings for the demo scripts. Sourced, not run.
# Every kubectl call is pinned to the kind cluster's context, so these scripts
# can never touch whatever cluster your current context points at.

CLUSTER=log-triage
KCTX="kind-${CLUSTER}"
NS=demo
SERVICES=(orders-api inventory-service payment-service fraud-check)
ORDERS_URL="${ORDERS_URL:-http://localhost:18080}"
DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

k() {
    kubectl --context "$KCTX" -n "$NS" "$@"
}

die() {
    echo "error: $*" >&2
    exit 1
}
