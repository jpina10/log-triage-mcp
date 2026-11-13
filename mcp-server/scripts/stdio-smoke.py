#!/usr/bin/env python3
"""Talk to the MCP server over stdio the way an MCP client does.

    python3 -I scripts/stdio-smoke.py                    # list the tools
    python3 -I scripts/stdio-smoke.py query_logs '{"service":"orders-api","since":"10m"}'

Starts the jar, runs initialize -> tools/list (-> tools/call), prints the
results and checks that nothing but JSON-RPC appears on stdout.
"""
import json
import os
import subprocess
import sys

JAR = os.path.join(os.path.dirname(__file__), "..", "target", "log-triage-mcp.jar")


def main():
    java = os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "java") if os.environ.get("JAVA_HOME") else "java"
    proc = subprocess.Popen([java, "-jar", JAR], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, text=True, bufsize=1)
    next_id = [0]

    def request(method, params=None):
        next_id[0] += 1
        send({"jsonrpc": "2.0", "id": next_id[0], "method": method, "params": params or {}})
        while True:
            line = proc.stdout.readline()
            if not line:
                raise SystemExit("server closed stdout; stderr:\n" + proc.stderr.read())
            msg = json.loads(line)  # fails loudly if anything but JSON-RPC reaches stdout
            if msg.get("id") == next_id[0]:
                return msg

    def send(msg):
        proc.stdin.write(json.dumps(msg) + "\n")
        proc.stdin.flush()

    try:
        init = request("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                                      "clientInfo": {"name": "stdio-smoke", "version": "0"}})
        info = init["result"]["serverInfo"]
        print(f"server: {info['name']} {info['version']}")
        send({"jsonrpc": "2.0", "method": "notifications/initialized"})

        tools = request("tools/list")["result"]["tools"]
        for t in tools:
            ro = t.get("annotations", {}).get("readOnlyHint")
            params = ", ".join(t["inputSchema"].get("properties", {}).keys())
            print(f"tool: {t['name']}({params})  readOnly={ro}")

        if len(sys.argv) > 1:
            args = json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}
            result = request("tools/call", {"name": sys.argv[1], "arguments": args})
            if "error" in result:
                print("ERROR:", result["error"])
            else:
                r = result["result"]
                print(f"--- {sys.argv[1]} (isError={r.get('isError', False)})")
                for c in r["content"]:
                    print(c.get("text", c))
    finally:
        proc.stdin.close()
        proc.terminate()


if __name__ == "__main__":
    main()
