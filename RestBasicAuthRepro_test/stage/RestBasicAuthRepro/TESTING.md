# Local end-to-end test — RestBasicAuthRepro

How the REST Request node's HTTP Basic-auth behaviour is exercised against a local
header-capturing mock, on both ACE v12 (TEST_V12) and v13 (TEST_V13), without
touching a real endpoint.

## What's under test

The flow `HTTP Input /sap → Compute (PrepareRequest) → REST Request (postToken) →
HTTP Reply`. The REST Request node's outbound call is redirected (via `baseURL`)
to a header-capturing mock on `http://localhost:7801`. The question under test is
**how the REST Request node attaches HTTP Basic credentials** to that call — and
specifically why a Security Profile alone can result in *no* `Authorization`
header with no error. The mock records every inbound header, and can run with or
without a `401` challenge to expose reactive vs pre-emptive behaviour.

## Components

| Component | Detail |
|---|---|
| **TEST_V13 / IS1** | ACE 13.0.7.0 integration node, admin `https://Rocinante:4418`. HTTP listener `7800` → flow at `http://localhost:7800/sap`. |
| **TEST_V12 / IS1** | ACE 12.0.12.17 integration node, admin `http://Rocinante:4416`. HTTP listener moved to `7802` (so both servers can bind at once) → `http://localhost:7802/sap`. |
| **`RestBasicAuthRepro`** application | HTTP Input `/sap` (JSON) → `PrepareRequest` Compute (copies headers, stamps `X-Ace-Version`) → `ComIbmRESTRequest` (`definitionFile=openapi.json`, `operationName=postToken`, `baseURL=http://localhost:7801`) → HTTP Reply; error/failure/catch → HandleException. |
| **`openapi.json`** | OpenAPI 3 with `POST /token` declaring `securitySchemes.basicAuth {type: http, scheme: basic}` and `security: [{basicAuth: []}]` — the security requirement the node maps a `rest::` identity onto. |
| **`SecurityRegistry`** policy project | `SAP.policyxml` (`SecurityProfiles`: `propagation=true`, `idToPropagateToTransport=Static ID`, `transportPropagationConfig=<securityId>`). |
| **Security identities** | Set with `mqsisetdbparms` per node: plain `<securityId>` (for the profile path) and `rest::<securityId>` (for the node Security-identity path). **Values are not recorded here** — set your own. |
| **Mock** (`../RestBasicAuthRepro_test/mock_server.py`) | Logs every request's headers to `mock_requests.log` and echoes them back as JSON. Default = **challenge** mode (`401 WWW-Authenticate: Basic` until creds arrive); `--no-challenge` = always `200`. |

> Note: this setup uses **`mqsisetdbparms`** (node-managed), not the ACE vault — matching the target environment. `mqsisetdbparms` changes take effect only after the integration *server* is restarted (`mqsireload`), not on app redeploy.

## Build + deploy

```bat
REM 1. Source the per-version environment first, e.g.
REM    "C:\Program Files\IBM\ACE\13.0.7.0\server\bin\mqsiprofile.cmd"   (v13)
REM    "C:\Program Files\IBM\ACE\12.0.12.17\server\bin\mqsiprofile.cmd" (v12)

REM 2. Stage the app + policy project under one folder (junctions, no copy)
mkdir stage
mklink /J stage\RestBasicAuthRepro D:\GIT\Ace_test_cases\RestBasicAuthRepro
mklink /J stage\SecurityRegistry   D:\GIT\Ace_test_cases\SecurityRegistry

REM 3. Package (run once per version with that version's ibmint)
ibmint package --input-path stage --output-bar-file RestBasicAuthRepro.bar

REM 4. Set the credentials (plain name for the profile path; rest:: for node identity)
mqsisetdbparms TEST_V13 -n <securityId>       -u <user> --password <pass>
mqsisetdbparms TEST_V13 -n rest::<securityId> -u <user> --password <pass>

REM 5. Deploy to the node-managed server (node name is positional / --integration-node)
mqsideploy --integration-node TEST_V13 --integration-server IS1 --bar-file RestBasicAuthRepro.bar --timeout-seconds 120

REM 6. Restart the server so the credentials activate
mqsireload TEST_V13 -e IS1

REM 7. (Config 3 only) make the profile path pre-emptive (server-wide)
mqsichangeproperties TEST_V13 -e IS1 -o ComIbmSocketConnectionManager -n preemptiveAuthType -v Basic
mqsireload TEST_V13 -e IS1
```

Three node configurations are compared (set on the REST Request node):

- **A — node Security identity:** `securityIdentity="<securityId>"`, no `securityProfileName`.
- **B — Security Profile:** `securityProfileName="{SecurityRegistry}:SAP"`, no `securityIdentity`.
- **C — Security Profile + pre-emptive:** config B plus step 7 above.

## Test scenarios

### S1 — Mock smoke test

```bash
# challenge mode: no creds -> 401, with creds -> 200
python mock_server.py 7801
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:7801/token            # -> 401
curl -s -o /dev/null -w "%{http_code}\n" -u user:pass -X POST http://localhost:7801/token # -> 200

# no-challenge mode: always 200
python mock_server.py 7801 --no-challenge
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:7801/token            # -> 200
```

### S2 — Full pipeline: POST /sap → REST Request → mock

```bash
curl.exe -s -X POST http://localhost:7800/sap -d "{}"          # v13 (use :7802 for v12)
# inspect what the mock received:
python -c "import json;[print(r['method'],r['path'],'auth=',r['authorization_present'],r.get('authorization_decoded')) for r in (json.loads(l) for l in open('../RestBasicAuthRepro_test/mock_requests.log'))]"
```

Observed (credential value masked as `<user:pass>`):

| Config | Mock mode | Mock log | Verdict |
|---|---|---|---|
| **A** node Security identity | either | `1. POST /token auth=True <user:pass>` | Pre-emptive ✔ |
| **B** Security Profile | challenge | `1. auth=False` → `401`; `2. auth=True <user:pass>` → `200` | Reactive ✔ |
| **B** Security Profile | no-challenge | `1. auth=False` → `200` (nothing sent, **no error**) | The silent-failure trap |
| **C** Profile + `preemptiveAuthType=Basic` | no-challenge | `1. POST /token auth=True <user:pass>` | Pre-emptive ✔ |

### S3 — Exception paths

- Point `transportPropagationConfig` (or the node Security identity) at a
  credential name that doesn't exist → the flow raises an error (proves the
  lookup is wired; an *existing* credential producing no header is the reactive
  case above, not an error).
- Send a request while the credential is set but the server has **not** been
  restarted since → no header (credential not yet active) — restart with
  `mqsireload` and retry.

## What to watch for

- Deploy success: `BIP1092I: The deployment request was processed successfully.`
- Runtime introspection: `mqsireportproperties <node> -e IS1 -o ComIbmSocketConnectionManager -r` —
  `preemptiveAuthType` (empty = reactive, `Basic` = pre-emptive), and under
  `active/detailed` the socket shows `requiredAuthType='Basic'` once a challenge
  has been seen.
- The decisive signal is the **mock log**: `authorization_present` on the *first*
  hit (pre-emptive) vs a `False` then `True` pair (reactive).

## Cleanup

```powershell
# Stop the mock (python listening on 7801)
(Get-NetTCPConnection -LocalPort 7801 -State Listen).OwningProcess | ForEach-Object { Stop-Process -Id $_ -Force }
# Apps can be left deployed; to remove: mqsideploy ... --delete RestBasicAuthRepro
# (Optional) restore TEST_V12 IS1 HTTP port: mqsichangeproperties TEST_V12 -e IS1 -o HTTPConnector -n ListenerPort -v 7800
```

## Things this test cannot prove

- The real SAP/SWIFT token endpoint's auth flow (real challenge behaviour, token
  expiry, OAuth client_credentials).
- Production TLS / certificate / cipher configuration (the mock is plain HTTP).
- Whether the real downstream challenges with `401` (the determining factor for
  whether the Security Profile alone suffices) — the mock simulates both.
- Behaviour under network partition / retry policy.

These remain integration-test scope, not unit-validation scope.
