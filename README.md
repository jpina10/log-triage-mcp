# log-triage-mcp

An MCP server for Claude Code that answers questions about Kubernetes pod
logs, triages errors across services, and hands real code defects to a
spec-driven fix workflow. It's measured with evals, redacts personal data,
and defends against prompt injection.

- [`DESIGN.md`](DESIGN.md): what it does, the decisions, the AI
  engineering approach and the milestone plan.
- [`mcp-server/`](mcp-server/README.md): **M1**, the MCP server with four
  read-only tools over `kubectl`, registered for Claude Code in
  [`.mcp.json`](.mcp.json).
- [`demo/`](demo/README.md): **M0**, a local kind cluster with four
  Spring Boot services and six injectable faults. It's the ground truth for
  the evals.

Status: M0 and M1 done. Grouping, redaction and friendly output (M2) are
next.
