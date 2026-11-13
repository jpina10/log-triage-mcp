# Design

An MCP server, used from Claude Code, that answers questions about
Kubernetes pod logs, triages errors across services, and hands code
defects to a spec-driven fix workflow.

The goal isn't just a tool that works. It's a tool **proven** to give
correct, safe and affordable answers, with evals, redaction,
prompt-injection defence and cost measurements to show for it.

---

## 1. What it does

Claude Code does the reasoning; the server provides well-designed,
**read-only** tools.

### L1: Ask (natural-language log queries)

Examples:
- *"Give me the request body of the POST to /orders with transaction id
  tx-1234."*
- *"10 minutes ago an error was reported in orders-api. What errors are
  logged?"*

The output is friendly. Errors are grouped and deduplicated, each with a
count, first seen and last seen, a likely cause, the trace id and a
suggested next step:

```
service: orders-api · 14:02–14:12 UTC · 3 error groups (247 lines)

1. ✖ HttpTimeoutException → payment-service      212×  first 14:03:41  last 14:11:58
   Likely downstream: payment-service answers in ~8s, client gives up at 3s
2. ✖ BadGateway from payment-service              31×  first 14:05:10
   Root cause deeper: NullPointerException in fraud-check (country "MT")
3. ⚠ Inventory reservation returned 503, retried   4×  14:07–14:08
   Absorbed by retry; all affected orders confirmed

Suggested next: triage #2 as a code defect? · send #1 evidence to payment owners?
```

### L2: Triage (diagnosis + classification)

Input: a description of the error, as free text, a transaction id, the
service where it happened, or a mix.

1. **Locate:** find the candidate services. If none is named, use a
   service map built from k8s labels, API specs and traces.
2. **Retrieve:** pull logs around the error window, correlate them by
   transaction or trace id **across services**, and read pod status and
   previous-container logs when a pod is crashing.
3. **Classify:** give a verdict, with the evidence lines behind it.

| Verdict | Example | Outcome |
|---|---|---|
| Code defect | NPE, mapping error, wrong validation | → L3 spec |
| Downstream / dependency | Timeout or 5xx from service Y | Follow the trace into Y; evidence for Y's owners |
| Infra | OOMKilled, failing probes | Ops recommendation |
| Config | Missing secret or env var, bad value | Ops / deployment recommendation |
| Transient / expected | Retry succeeded, known blip | No action |
| Resilience gap | Downstream timeout with no breaker or fallback | *Possibly* a spec; a human decides |

### L3: Action (hand-off, v2)

- **A code change is needed** → generate a **fix spec** for a
  spec-driven delivery workflow, **after human approval**. The spec
  follows a canonical schema: identity, objective, numbered requirements,
  acceptance criteria, constraints, out of scope and open questions,
  plus an evidence appendix (log groups, trace ids, time window). It
  contains no design decisions; designing the fix is the workflow's job.
- **Otherwise** → information only: what can be done, **who** should act
  (the owning team), and a draft of the evidence to send them. "No action
  needed" is a valid outcome.

---

## 2. Decisions

| # | Decision |
|---|---|
| D1 | **v1 scope = L1 + L2.** L3 is v2, once triage is trustworthy |
| D2 | **Stack:** Java + Spring AI MCP server, stdio transport, used from Claude Code |
| D3 | **Dev and prod are separate:** one tool interface, with a swappable `LogSource` adapter per environment profile |
| D4 | **Dev source = `kubectl logs`.** It's the MVP adapter |
| D5 | **Prod source = a centralised log backend** (Elastic / OpenSearch / CloudWatch / Loki), read-only and masked at source. Added later |
| D6 | **Prod masking principle:** the agent sees what a human with the same role would see. A masked field is reported as masked, never guessed or reconstructed |
| D7 | **Demo environment:** a local kind cluster with Spring Boot services calling each other, plus fault injection (see [`demo/`](demo/README.md)) |
| D8 | **Evals:** deterministic checks wherever possible; LLM-as-judge only for output readability |
| D9 | **Prod adapter developed against [Floci](https://github.com/floci-io/floci)** (a local AWS emulator) when the backend is CloudWatch Logs or OpenSearch |

### Dev vs prod

| | Dev | Prod |
|---|---|---|
| Source | `kubectl logs` | Centralised backend |
| Data | Everything logged | Masked at source |
| Access | Developer's kube context | Read-only role, scoped indexes |
| Limits | Current and previous container only; merge replicas via label selectors | Full history, retention-bound |

### Sketch of tools and prompts

- Tools: `list_services`, `query_logs`, `find_by_transaction_id`,
  `error_groups`, `trace_across_services`, `pod_status`
- MCP prompts: `triage`, `draft_fix_spec` (v2)

---

## 3. AI engineering

1. **Evals with ground truth.** Faults are injected on purpose, so the
   right answer is known for every scenario
   ([`demo/scenarios/scenarios.json`](demo/scenarios/scenarios.json)).
   Measured:
   - L1: query correctness (results match the expected set)
   - L2: right service and right verdict category
   - L3: spec completeness against the schema
2. **Redaction of PII and secrets.**
   - Defence in depth: the server's own redaction runs even when the
     source already masks, because stack traces and exception messages
     leak values.
   - Redaction is **on by default**, with an explicit opt-out. Dev data
     isn't always fake.
3. **Prompt-injection defence.** Log content, such as request bodies, is
   untrusted, so data is kept separate from instructions. The server is
   read-only by design. The demo includes an injection scenario.
4. **Context and cost budget.** Logs are aggregated, deduplicated and
   clustered before the model sees them; raw dumps are never sent. Tool
   output is capped.
5. **Tool design as an experiment.** Raw log JSON vs grouped plain text,
   measured with the evals.
6. **Observability of the agent.** Cost, tokens and latency per question,
   via OpenTelemetry (GenAI semantic conventions).

---

## 4. Prerequisites and risks

- **Correlation ids:** cross-service tracing needs a transaction or trace
  id propagated and logged by every service. The demo services propagate
  `X-Transaction-Id` through the MDC.
- **Request bodies** are only searchable if services log them. That's
  typical in dev; in prod they're masked, which is acceptable (D6).
- **`kubectl` history** is lost on pod restart, which limits dev triage
  to recent events.
- **Using it on real environments:** sending logs to an LLM API is a
  data-governance decision for the environment's owner. The demo cluster
  holds only fake data.
- **Floci fidelity:** an emulator isn't AWS. Verify query support (e.g.
  CloudWatch Logs Insights) and re-run the evals against real AWS before
  claiming anything about prod.

---

## 5. Milestones

| Milestone | What | Status |
|---|---|---|
| M0 | Demo cluster: kind, 4 Spring Boot services, transaction-id propagation, 6 fault scenarios with ground truth | ✅ Done |
| M1 | MCP server skeleton + kubectl adapter + `query_logs` + `pod_status` | Next |
| M2 | Grouping/dedup + redaction + friendly output | |
| M3 | Eval harness for L1 | |
| M4 | L2 triage: service map, cross-service trace, classification | |
| M5 | L2 evals + prompt-injection test suite → **v1** | |
| M6 | L3: fix-spec generation with a human approval gate (v2) | |
| Later | Prod adapter (Fluent Bit → Floci), remote HTTP transport with auth | |

### What M0 taught us

- **Logs alone aren't enough.** For an OOM-killed pod the evidence is the
  pod status (`OOMKilled`, exit 137); the logs show nothing. For a config
  crash the evidence is only in the *previous* container's log. Triage
  needs `pod_status` and `--previous` support.
- **Logs mix formats.** JSON lines sit next to plain-text JVM warnings, so
  the parser must tolerate both.
- **The symptom is rarely the cause.** One bad input produces three
  ERRORs in three services, and only the deepest one is the cause.
- **ERROR ≠ incident.** Errors absorbed by a retry need a "transient"
  verdict, not an alarm.
