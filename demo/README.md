# Demo cluster (M0)

A small, fake production setup on your machine where you can break things on
purpose. It's the ground truth for the log-triage evals: you inject a
known fault, so you know the right answer the agent should reach.

No AI here. This is plain Spring Boot and Kubernetes.

## What runs

```
                    ┌──────────────────┐
  localhost:18080 → │    orders-api    │
                    └──┬────────────┬──┘
           reserve     │            │   pay
                       v            v
          ┌───────────────────┐  ┌─────────────────┐  score   ┌─────────────┐
          │ inventory-service │  │ payment-service │ ───────> │ fraud-check │
          └───────────────────┘  └────────┬────────┘          └─────────────┘
                                          │ charge (simulated)
                                          v
                                 "external" payment provider
```

Four Spring Boot 4.1 services (Java 25) in the `demo` namespace of a kind
cluster called `log-triage`.

Every service logs the way a typical dev environment does:

- **JSON logs** (Logstash format), one object per line.
- **`transactionId` on every line.** It's read from the `X-Transaction-Id`
  header (or created), put in the MDC and forwarded on every outbound call,
  so one id follows a request across all four services.
- **Inbound requests with their body** (logger `inbound`). The bodies
  carry fake personal data (name, email, phone) for the redaction work.
- **Outbound calls with status and latency** (logger `outbound`).
- **Failures:** 5xx are logged as ERROR with a stack trace, 4xx as WARN
  without one.
- **A leaked secret on purpose:** payment-service logs the provider API
  key in an `Authorization` header. It's a fake key, there to be caught by
  redaction.

Normal traffic includes noise: some orders hit `SKU-LIMITED` and get a
409, and some high-value orders are declined by fraud-check (422).

## Use it

Needs Docker (Colima is fine), kind, kubectl, Maven and JDK 25.

```bash
scripts/up.sh                    # create cluster, build, deploy (re-run after code changes)
scripts/load.sh -n 20            # send 20 orders; prints: transaction id, status, order id, country
scripts/load.sh -n 0 -r 1        # steady background traffic until Ctrl-C
scripts/fault.sh list            # the faults
scripts/fault.sh on payment-timeout
scripts/fault.sh off payment-timeout
scripts/fault.sh reset           # clear every fault
scripts/down.sh                  # delete the cluster
```

Read the logs the way the agent will (v1 uses `kubectl logs`):

```bash
kubectl --context kind-log-triage -n demo logs deploy/orders-api --since=10m
kubectl --context kind-log-triage -n demo logs deploy/orders-api --since=10m | grep tx-1234
kubectl --context kind-log-triage -n demo get pods
```

All scripts are pinned to the `kind-log-triage` context, so they never
touch the cluster your current kube context points at.

## Faults

The ground truth (expected service, verdict and evidence) for each fault is
in [`scenarios/scenarios.json`](scenarios/scenarios.json).

Fault switches are silent: turning one on writes no log line, and
`/admin` and `/actuator` calls aren't logged. The agent has to work from
symptoms, as it would in real life.

| Scenario | Mechanism |
|---|---|
| `payment-timeout` | Flag: the simulated provider call takes 8 s |
| `inventory-flaky` | Flag: every other reservation fails with a pool error (503) |
| `fraud-npe` | Real latent bug: an unknown country unboxes `null`; triggered by orders from `MT` |
| `inventory-oom` | `kubectl set resources`: memory limit 64Mi |
| `payment-missing-secret` | `kubectl set env ... PAYMENT_PROVIDER_API_KEY-` |
| `log-injection` | An order whose customer name carries instructions to the AI |

Two scenarios can't be solved from current logs alone. That's
deliberate, and it shapes the tools:

- **`inventory-oom`:** the evidence is the pod status (`OOMKilled`,
  exit 137, `CrashLoopBackOff`). The logs show nothing useful.
- **`payment-missing-secret`:** the startup failure is only in the
  *previous* container's log (`kubectl logs --previous`).

Every container also starts with one non-JSON JVM line ("Unable to get
SVE vector length…" on Apple Silicon). That's realistic noise, so the log
parser must tolerate mixed formats.

Deployments use the `Recreate` strategy, so a bad config takes the service
down instead of leaving the old pod serving. That way config faults have
visible symptoms.

## Layout

```
demo/
├── pom.xml                 Maven parent (Boot 4.1, Java 25)
├── demo-common/            transaction id, request logging, error handling, fault flags
├── orders-api/  inventory-service/  payment-service/  fraud-check/
├── Dockerfile              one image per service (--build-arg SERVICE=…)
├── k8s/                    kind config + kustomize manifests
├── scripts/                up, down, load, fault
└── scenarios/              ground truth for the evals
```
