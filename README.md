# log-triage-mcp

An MCP server for Claude Code that answers questions about Kubernetes pod
logs, triages errors across services, and hands real code defects to a
spec-driven fix workflow. It's measured with evals, redacts personal data,
and defends against prompt injection.

- [`DESIGN.md`](DESIGN.md): what it does, the decisions, the AI
  engineering approach and the milestone plan.
- [`demo/`](demo/README.md): **M0**, a local kind cluster with four
  Spring Boot services and six injectable faults. It's the ground truth for
  the evals.

Status: M0 done. The MCP server (M1) is next.
