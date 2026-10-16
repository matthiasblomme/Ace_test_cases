# AdminMcpTopologies - ACE 13.0.9 MCP Admin Server on three topologies

Tests whether the MCP Admin Server (`MCP.Admin`) can be enabled and used on a stand-alone
integration server, an integration node, and a plain Docker container, with two
apps deployed: one that runs and one in state `failed`.

Run on 2026-10-16, ACE 13.0.9.0 (Windows) and image `cp.icr.io/cp/appc/ace:13.0.9.0-r1`.

## Apps

- `HelloWorld` (REST API, this repo) - starts; its main flow is `gen.HelloWorld`.
- An application with MQ nodes and no queue manager configured (here `HelloWorldWithMQ` from this
  repo works the same way) - deploys with `BIP1361E`, state `failed`.

Package each with `ibmint package --input-path <repo> --project <name> --output-bar-file <name>.bar`.
Include its shared library and policy project with extra `--project` flags when it has them.

## Client

`node ace-mcp.mjs --url https://localhost:<port>/mcp --insecure tools | call <tool> '<json>'`
(the `ace-admin-mcp` skill's script; any MCP client over Streamable HTTP works).

## 1. Stand-alone integration server

1. `mqsicreateworkdir <dir>`; `ibmint deploy --input-bar-file <bar> --output-work-directory <dir>`.
2. Copy `sis/overrides/server.conf.yaml` to `<dir>/overrides/` (ports moved off the defaults).
3. `IntegrationServer --work-dir <dir> --name sis`; wait for `BIP3132I ... '7651' for 'MCP Streamable https'`.
4. `tools` -> `-32601 Method not found` (no tools enabled yet).
5. Enable all tools: `GET https://localhost:7611/apiv2/admin-mcp/mcp-tools?depth=2`, build
   `{"<identifier>":{"enabled":true},...}` with a serialiser, `POST` it to
   `/apiv2/admin-mcp/bulk-configure-tools` -> `OK`, file `<dir>/overrides/admin-mcp-tools.json`.
6. Restart the server (stop by `--work-dir` match), `tools` -> six tools.

Result: six tools; `list_integrations` shows the MQ app `failed` and HelloWorld `started`;
`describe_message_flow` works on `gen.HelloWorld` and fails on the failed app (it has no flows).

## 2. Integration node

1. `mqsicreatebroker <node>`; append `node/node.conf.yaml.append` to
   `<MQSI_WORKPATH>/components/<node>/overrides/node.conf.yaml` (do not replace it - it holds the
   node UUID and the admin port `mqsicreatebroker` picked).
2. `mqsistart <node>`; `mqsicreateexecutiongroup <node> -e srv1`.
3. MCP listens on 7650 (no `port` set). The node admin listener is HTTPS.
4. Enable all tools via the node admin port as in 1.5 (eight tools offered), deploy both bars
   with `ibmint deploy ... --output-integration-node <node> --output-integration-server srv1`,
   `mqsistop` / `mqsistart`.

Result: six tools. Per-server tools need `serverName`; `info` describes the node.
`list_integration_servers` and `describe_integration_server` read `enabled: false` after every
restart although `admin-mcp-tools.json` says `true` - reproduced three times; they cannot be
called. Server list workaround: `GET /apiv2/servers` on the node admin port.

## 3. Docker container

1. Put both bars beside `docker/Dockerfile`, plus `admin-mcp-tools.json` (the six-tool file
   from step 1.5 - a copy is in `docker/`).
2. `docker build -t admcp-ace docker`; `docker run -d -p 7652:7650 admcp-ace`.
3. Wait for `BIP1991I` in `docker logs` (about 9 minutes on Docker Desktop on the test box).

Result: six tools on the first start, no REST step, no restart. Without the call-home line the
server stops with `BIP2203E` + `BIP15366W` (company name required).

## Cleanup

Stop the server by `--work-dir`; `mqsistop <node>` and `mqsideletebroker <node>`;
`docker rm -f` the container and `docker rmi` the image.
