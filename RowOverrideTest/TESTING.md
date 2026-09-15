# Local end-to-end test - RowOverrideTest

How RowOverrideTest is exercised against a local standalone integration server. No production endpoints, no MQ, no credentials are involved - the flow is a self-contained ESQL diagnostic.

## What's under test

The full HTTPInput -> Compute -> HTTPReply path. HTTPInput listens on `/rowoverridetest` (JSON domain), the compute node `com.mbl.test.RowOverrideTest_BuildResponse.Main` ignores the request body and runs three independent ROW assignment cases, each with a before/after snapshot returned as JSON. All three start from the same first assignment, `SET <case>.parent.child = ROW(v1 AS attr1, ..., v5 AS attr5)`; they differ only in the second assignment:

- Case 1 `case1_rowOntoParent`: `SET <case>.parent = ROW(v6 AS attr6, v7 AS attr7)` - constructor onto the parent of the assigned child.
- Case 2 `case2_rowOntoSameChild`: `SET <case>.parent.child = ROW(v6 AS attr6, v7 AS attr7)` - constructor onto the same child again.
- Case 3 `case3_rowVariableOntoParent`: `DECLARE rowVar ROW; SET rowVar = ROW(v6 AS attr6, v7 AS attr7); SET <case>.parent = rowVar` - the same two attrs staged through a declared ROW variable, assigned onto the parent.

The response answers, per case, whether the second assignment merges into the existing tree or replaces it. Nothing is redirected or mocked - there is no downstream.

## Components

| Component | Detail |
|---|---|
| Standalone integration server `RowOverrideTestSrv` | ACE 13.0.8.1, work dir `C:\temp/RowOverrideTest_sis`, admin REST 7602, HTTP listener 7800. |
| `RowOverrideTest.bar` | Packaged from `d:/GIT/Ace_test_cases/RowOverrideTest`, deployed into the work dir with `ibmint deploy --output-work-directory` before server start. |

No MQ, no PolicyProject, no mock, no `_LOCAL.properties` overrides, no vault - the flow has no external dependencies and no credentials. No `overrides/server.conf.yaml` was needed either: both ports come from the `IntegrationServer` command line, and the generated base `server.conf.yaml` is left untouched (verified byte-identical to a fresh `mqsicreateworkdir` output).

## Build + deploy

Run from a shell with the ACE 13.0.8.1 profile sourced (`"C:\Program Files\IBM\ACE\13.0.8.1\server\bin\mqsiprofile.cmd"`).

```bat
REM 1. Create the work directory (first run only)
mqsicreateworkdir C:\temp\RowOverrideTest_sis

REM 2. Package
ibmint package --input-path d:\GIT\Ace_test_cases\RowOverrideTest --output-bar-file d:\GIT\Ace_test_cases\RowOverrideTest\RowOverrideTest.bar

REM 3. Deploy the BAR into the work directory (server stopped)
ibmint deploy --input-bar-file d:\GIT\Ace_test_cases\RowOverrideTest\RowOverrideTest.bar --output-work-directory C:\temp\RowOverrideTest_sis

REM 4. Start the standalone integration server (chcp first - see codepage note)
chcp 1252 && IntegrationServer --work-dir C:\temp\RowOverrideTest_sis --name RowOverrideTestSrv --admin-rest-api 7602 --http-port-number 7800 --console-log
```

Codepage note: the default Windows console codepage 65001 makes ACE fail every flow at startup with BIP2132E; the `chcp 1252` prefix is mandatory.

Wait for `BIP1991I: Integration server has finished initialization.` before sending requests.

## Test scenarios

### S1 - Row-override diagnostic request

```bat
curl -s -X POST http://localhost:7800/rowoverridetest
curl -s -X GET  http://localhost:7800/rowoverridetest
```

Expected for both: HTTP 200, `Content-Type: application/json`, and this exact body (the request body and method are ignored, all test data is hardcoded in the compute):

```json
{
  "case1_rowOntoParent": {
    "afterFirstAssign":  { "parent": { "child": {"attr1": "v1", "attr2": "v2", "attr3": "v3", "attr4": "v4", "attr5": "v5"} } },
    "afterSecondAssign": { "parent": { "child": {"attr1": "v1", "attr2": "v2", "attr3": "v3", "attr4": "v4", "attr5": "v5"}, "attr6": "v6", "attr7": "v7" } }
  },
  "case2_rowOntoSameChild": {
    "afterFirstAssign":  { "parent": { "child": {"attr1": "v1", "attr2": "v2", "attr3": "v3", "attr4": "v4", "attr5": "v5"} } },
    "afterSecondAssign": { "parent": { "child": {"attr1": "v1", "attr2": "v2", "attr3": "v3", "attr4": "v4", "attr5": "v5", "attr6": "v6", "attr7": "v7"} } }
  },
  "case3_rowVariableOntoParent": {
    "afterFirstAssign":  { "parent": { "child": {"attr1": "v1", "attr2": "v2", "attr3": "v3", "attr4": "v4", "attr5": "v5"} } },
    "afterSecondAssign": { "parent": { "attr6": "v6", "attr7": "v7" } }
  }
}
```

Result observed 2026-09-03 on ACE 13.0.8.1: exactly the body above. Answers per case:

- Case 1: `SET parent = ROW(...)` MERGES - `child` survives, attr6/attr7 are appended beside it (existing children keep first position).
- Case 2: `SET parent.child = ROW(...)` onto the same child MERGES too - all seven attrs end up inside `child`.
- Case 3: `SET parent = rowVar` (declared ROW variable) REPLACES - `child` is wiped, only attr6/attr7 remain. The ROW constructor appends; a ROW variable right-hand side overwrites the target's children (a plain field-reference right-hand side is expected to behave like the variable, but was not separately exercised in this run).

### S2 / S3 - not applicable

The flow has no downstream call (no mock scenario) and error handling is explicitly omitted from the spec (no failure wiring, no bad-input branches). There is nothing to exercise beyond S1; scenarios are not invented to fill the template.

## What to watch for

Startup (console log):

- `BIP3132I` - HTTP listener on port 7800
- `BIP1996I` - listening on HTTP URL `/rowoverridetest`
- `BIP2269I` - deployed resource `com.mbl.test.RowOverrideTest` started successfully
- `BIP1991I` - integration server finished initialization

Per request: no BIP error codes are expected; the flow logs nothing itself.

## Cleanup

```powershell
# Stop the server: Ctrl-C in its console, or from another shell, port-scoped:
$conn = netstat -ano | Select-String ':7602\s.*LISTENING'
taskkill /PID (($conn -split '\s+')[-1]) /F
```

Do not use `taskkill /IM IntegrationServer.exe` - that kills every integration server on the box, including node-managed ones. The port-scoped kill above only stops the server that owns admin port 7602.

The work directory is left on disk for re-runs; restart with step 4 of Build + deploy (steps 1-3 only needed after a code change: re-package and re-deploy first).

## Things this test cannot prove

- Behaviour on other ACE versions - the merge-not-wipe result is verified on 13.0.8.1 only
- Anything about the request payload path - the flow ignores its input by design
- Concurrency, performance, or error handling - none are in scope for this diagnostic
