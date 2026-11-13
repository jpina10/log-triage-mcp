#!/usr/bin/env bash
# Create the kind cluster (if missing), build the four services, load their
# images and deploy them. Safe to re-run: it rebuilds and restarts everything.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
cd "$DEMO_DIR"

if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    echo "==> creating kind cluster '$CLUSTER'"
    kind create cluster --config k8s/kind-config.yaml
fi

echo "==> building jars"
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 25)"
    export JAVA_HOME
fi
mvn -q -B package

for s in "${SERVICES[@]}"; do
    echo "==> image $s"
    docker build -q --build-arg SERVICE="$s" -t "log-triage/$s:dev" . >/dev/null
    kind load docker-image "log-triage/$s:dev" --name "$CLUSTER" >/dev/null
done

echo "==> deploying"
kubectl --context "$KCTX" apply -k k8s
k rollout restart deployment >/dev/null
for s in "${SERVICES[@]}"; do
    k rollout status "deployment/$s" --timeout=240s
done

echo
echo "Ready. orders-api is on $ORDERS_URL"
echo "Next: scripts/load.sh -n 20      (send traffic)"
echo "      scripts/fault.sh list      (the faults you can inject)"
