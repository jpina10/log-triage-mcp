# log-triage MCP server (M1)

A stdio MCP server, built with Java 25, Spring Boot 4.1 and Spring AI 2.0,
that gives Claude Code **read-only** access to one Kubernetes namespace's
pod logs and pod status.

## Tools

| Tool | What it returns |
|---|---|
| `list_services` | Deployments with ready/desired replicas; flags the ones not ready |
| `query_logs` | One service's logs over a window (default 15m, WARN+), filtered by level, text, transaction id; `previous=true` reads the crashed container |
| `find_by_transaction_id` | One transaction id across every service, all levels, in time order |
| `pod_status` | Phase, readiness, restarts, state, last termination (e.g. `OOMKilled`, exit 137), memory limit, recent events |

Example questions in Claude Code:
- *"What errors did orders-api log in the last 10 minutes?"*
- *"Follow transaction tx-e99e9756-8ff0 and tell me where it failed."*
- *"inventory-service is down. Why?"*

## Safety by construction

- **Read-only.** The only kubectl verbs it can run are `get` and `logs`.
- **Pinned scope.** Every call carries the configured `--context` and
  `--namespace`; no tool argument can change them.
- **No shell.** kubectl runs from an argument list, and service names,
  durations and transaction ids are validated against strict patterns
  before they get near it. Free-text search only filters in memory.
- **Capped output.** Entries are limited (default 50, max 200, newest
  kept), messages and stack traces are shortened, and every cut is stated.
- **Logs are untrusted.** The server instructions and tool descriptions
  tell the model that log text is data, never instructions.

Not in M1: grouping, redaction of personal data and secrets, and output
designed for people. Those come in M2.

## Run

```bash
mvn -B package                      # builds target/log-triage-mcp.jar and runs the tests
python3 -I scripts/stdio-smoke.py   # starts it and lists the tools, like an MCP client
python3 -I scripts/stdio-smoke.py query_logs '{"service":"orders-api","since":"10m"}'
```

### In Claude Code

The repo's [`.mcp.json`](../.mcp.json) registers it as `log-triage`.
Build the jar, start the demo cluster (`demo/scripts/up.sh`), then open
Claude Code in the repo root and approve the server when asked.

| Setting | Env var | Default |
|---|---|---|
| kube context | `LOGTRIAGE_CONTEXT` | `kind-log-triage` |
| namespace | `LOGTRIAGE_NAMESPACE` | `demo` |

The server's own logs go to `$TMPDIR/log-triage-mcp.log`, because stdout
carries the protocol.

## Layout

```
src/main/java/dev/logtriage/mcp/
├── LogTriageProperties.java   context, namespace, label, limits
├── kube/                      Kubectl (the only commands), CommandRunner, ClusterReader (services, pods, events)
├── logs/                      LogSource (adapter seam: kubectl now, a prod backend later), parser for mixed JSON/plain lines
└── tools/                     the MCP tools, argument validation, output format
```
