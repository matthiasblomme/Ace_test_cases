# CallableRestApi - TESTING

## What's under test

A REST API (`CallableRestApi`) that exposes one operation, `GET /hello`, whose
implementation invokes the existing callable flow `CallableApp` /
`com.mbl.test.CallableFlow` (callable input endpoint `/cf1`) and returns its reply.
The point is to give the callable flow a REST front door so it can be exposed as an
MCP tool via `MCP.Runtime` on ACE 13.0.7.

## Components

- `gen/CallableRestApi.msgflow` - generated REST entry flow (WSInput -> RouteToLabel -> getHello -> WSReply, plus the three handler subflows).
- `getHello.subflow` - `Input -> CallableFlowInvoke -> Output`. The Invoke node has
  `targetApplication="CallableApp"` and `targetEndpointName="/cf1"`.
- `rest-api.yaml`, `restapi.descriptor` - OpenAPI 3 + descriptor.
- Depends at runtime on `CallableApp` being deployed in the same integration server.

## Build + deploy

- Build: `ibmint package --input-path CallableRestApi --output-bar-file CallableRestApi.bar --project CallableRestApi` -> `BIP8071I` (clean).
- Deploy: staged `CallableRestApi.bar` into `C:\temp\MCP_SERVER\run\` and started the
  standalone server (`IntegrationServer --work-dir C:\temp\MCP_SERVER`).
- Server is ACE 13.0.7.0. Admin Web UI 7777 (https), HTTP listener 7800, MCP.Admin 7650, MCP.Runtime 7750 (starts on first exposed tool, `mcpStartMode: automatic`).

## Test scenario (S2 - full pipeline, verified)

```
curl.exe -i -s http://localhost:7800/callableRest/v1/hello
```

Result:

```
HTTP/1.1 200 OK
Content-Type: application/json;charset=utf-8
Server: IBM App Connect Enterprise

{"Hello":"World"}
```

`{"Hello":"World"}` is the callable flow's own output (`SET OutputRoot.JSON.Data.Hello = 'World'`),
so the REST -> CallableFlowInvoke -> callable flow -> reply path is confirmed end to end.

## What to watch for

- Startup log: `BIP2269I: Deployed resource 'gen.CallableRestApi' ... started successfully`
  and `Listening on HTTP URL '/callableRest/v1*'`.
- If `CallableApp` is not deployed in the same server, the Invoke fails (`failure` terminal).

## Things this test cannot prove

- The MCP side. Exposing `getHello` as an MCP tool (`MCP.Runtime`, port 7750) and calling
  it from an MCP client is the next step, done from the Web UI and verified with MCP Inspector / curl.

## Cleanup

- Stop the server; remove `C:\temp\MCP_SERVER\run\CallableRestApi.bar` to undeploy.
