# Deploying CustomerApi

Target runtime: IBM App Connect Enterprise 13.0.9.0 on Windows, standalone (independent)
integration server. Install root `C:\Program Files\IBM\ACE\13.0.9.0`.

Three projects make up this demo and they are deployed in three different ways. Getting
that wrong is the usual reason the first request fails:

| Project | How it reaches the server |
|---|---|
| `CustomerApi` | packaged into a BAR |
| `CustomerApiPolicies` | copied as a **folder** under `<work_dir>/run/`, never into the BAR |
| `CustomerApi.server-overrides` | its keys are merged into `<work_dir>/overrides/server.conf.yaml` |

## 0. What the application needs

- **IBM MQ** queue manager `QM1` reachable at `localhost:1414` over channel
  `INSECURE.APP.SVRCONN`, with queue `DEV.MCP.AUDIT` defined. The channel is a plain
  client connection with no TLS and no credential, so the `DEMO_MQ` policy carries an
  empty `securityIdentity`.
- **An ODBC data source** named `ACE_SQL_TEST` (Windows system DSN) pointing at the
  SQL Server database that holds `dbo.DEMO_CUSTOMERS`, plus a credential for it in the
  vault under the same name.
- **HTTP**. The default listener port is 7800, but the demo server overrides it to **7820**
  in `overrides/server.conf.yaml`, because 7800 was already taken by another integration
  server on the same machine. `openapi.json` carries the same 7820 in its `servers` entry.
  Those two always move together: change one and the published spec stops matching reality.

## 1. Work directory

```bat
"C:\Program Files\IBM\ACE\13.0.9.0\server\bin\mqsicreateworkdir" C:\aceWork\acemcpdemo
```

## 2. Server overrides

Copy the active keys from `../CustomerApi.server-overrides/server.conf.yaml` into

```
C:\aceWork\acemcpdemo\overrides\server.conf.yaml
```

The one key the application cannot run without is

```yaml
remoteDefaultQueueManager: '{CustomerApiPolicies}:DEMO_MQ'
```

That is what binds the MQOutput node, which carries only `queueName`, to the queue
manager. The generated `C:\aceWork\acemcpdemo\server.conf.yaml` is never edited: it is
read-only reference material that documents every key with its default, and operations
expect to find it intact. Every change goes in `overrides\*.yaml`.

## 3. Vault and the ODBC credential

A vault must exist before a credential can be put in it, and before the server that
reads it starts. Order: create the vault, add the credential, start the server with the
vault key. An `ibmint set credential` against a missing vault fails with `BIP10003E`.

The external directory vault is the default kind. It lives outside the work directory
and the server is pointed at it from `overrides\server.conf.yaml`.

```bat
REM 3a. Create the vault (once per machine; pick your own directory and key)
ibmint create vault C:\aceWork\vault --external-directory-vault-key <vaultKey>

REM 3b. Store the credential for the ODBC DSN. The credential NAME is the DSN name -
REM     that is how ACE matches it to the Compute node's Data source property.
ibmint set credential --external-directory-vault C:\aceWork\vault ^
    --external-directory-vault-key <vaultKey> ^
    --credential-type odbc ^
    --credential-name ACE_SQL_TEST ^
    --username <sqlUser> --password <sqlPassword>

REM 3c. Check what is stored (values are masked)
ibmint display credentials --external-directory-vault C:\aceWork\vault ^
    --external-directory-vault-key <vaultKey>
```

Then uncomment the `Credentials` block in `overrides\server.conf.yaml` and set the
directory:

```yaml
Credentials:
  ExternalDirectoryVault:
    directory: 'C:\aceWork\vault'
```

Notes:

- `set credential` is singular, `display credentials` is plural, and removing one is
  `unset credential`. IBM's choice, easy to get wrong.
- `--credential-type odbc` with `--username` / `--password` is **verified on 13.0.9.0**:
  both the create and the update answer `BIP15119I`, and `ibmint display credentials`
  then reports the entry as type `odbc` from provider `extdirvault`. The credential name
  must equal the DSN name; that is how ACE matches it to the Compute node's Data source.
  To see what other credential types accept, `ibmint display credential-types
  --show-auth-types` lists them.
- `mqsisetdbparms` is the IIB-era legacy mechanism. Do not use it here.
- If the customer standard asks for a per-server vault instead, create that one using
  `mqsivault --work-dir C:\aceWork\acemcpdemo --create --vault-key <vaultKey>` (there is
  no `ibmint` equivalent for creating it), credentials go in with `--work-directory`
  instead of `--external-directory-vault`, no `Credentials` block is needed, and the
  server takes `--vault-key` rather than `--ext-vault-key`.
- The vault key is the only secret anyone handles by hand. No credential value belongs
  in this repository, in the BAR, or in any file under these projects.

## 4. Stage the policy project

The `DEMO_MQ` MQEndpoint policy is needed while the server starts its MQ connection, so
it goes in as a folder, not inside the BAR. A BAR that contains an MQ policy project
fails with `BIP1388E`.

```bat
xcopy /E /I /Y ..\CustomerApiPolicies C:\aceWork\acemcpdemo\run\CustomerApiPolicies
```

## 5. Package and deploy the application

Stage only the application so nothing else in the repository is dragged in.

```bat
mkdir C:\aceWork\stage
mklink /J C:\aceWork\stage\CustomerApi <repo>\AceMcpDemo\CustomerApi

ibmint package --input-path C:\aceWork\stage --output-bar-file C:\aceWork\CustomerApi.bar

copy /Y C:\aceWork\CustomerApi.bar C:\aceWork\acemcpdemo\run\
```

`mklink /J` needs no administrator rights and the junction references rather than copies.

## 6. Start the server

Start it in its own window, or detached with its output redirected to a log. It never
exits on its own.

```bat
IntegrationServer --work-dir C:\aceWork\acemcpdemo --name ACEMCPDEMO ^
    --ext-vault-key <vaultKey> --console-log
```

Wait for `BIP1991I` (the server is listening) and `BIP9332I` (the application loaded).

## 7. Verify

```bat
REM 200 - a row that exists
curl -i http://localhost:7820/customerapi/customers/1001

REM 404 - a row that does not
curl -i http://localhost:7820/customerapi/customers/9999

REM The audit record that each of those requests put on the queue
docker exec <mqContainer> bash -c "/opt/mqm/samp/bin/amqsbcg DEV.MCP.AUDIT QM1" | more
```

A successful read answers `200` with `{"customerId", "customerName", "city", "status"}`,
and an audit record with the same `customerId` and a `trackingId` is on
`DEV.MCP.AUDIT`. The audit write happens before the read, so an audit record on the
queue with no matching HTTP 200 means MQ is fine and the database is not.

## 8. When the database read fails

A wrong or missing ODBC credential does **not** stop the deployment. The Compute node
does not connect before the flow starts, so the application still reaches state
`started` and the failure arrives on the first request:

- The audit record is on `DEV.MCP.AUDIT`.
- The request comes back `500` with the ACE exception text in `description` and the BIP
  number in `bipCode`.
- The same text, with the SQL state and the ODBC message, is in the integration server's
  local error log, written by the Trace node in the catch handler and prefixed
  `CustomerApi catch handler:`.
- The Compute node's Data source property names the DSN literally (`ACE_SQL_TEST`), so
  the credential to look at is readable straight off the flow.

Fix it by replacing the credential and restarting the server:

```bat
ibmint set credential --external-directory-vault C:\aceWork\vault ^
    --external-directory-vault-key <vaultKey> ^
    --credential-type odbc --credential-name ACE_SQL_TEST ^
    --username <sqlUser> --password <sqlPassword>
```

A credential change is picked up by the integration server at startup, so restart the
server after changing it. Redeploying the application alone is not enough.

## 9. Exposing this server and this API over MCP

Two separate MCP servers, both switched on in `overrides\server.conf.yaml`, both needing a
restart to take effect:

```yaml
MCP:
  Admin:
    enabled: true
    port: 7650
    readOnly: true
  Runtime:
    mcpStartMode: 'automatic'
    port: 7750
```

**MCP.Admin (7650)** exposes the integration server's own administration as read-only
tools. Which tools are exposed is controlled by `overrides\admin-mcp-tools.json`; with no
tools enabled, `tools/list` answers `-32601 Method not found`.

**MCP.Runtime (7750)** exposes deployed REST API operations as tools. `automatic` means the
listener starts once a tool is deployed and stops when the last one is removed, so 7750 is
absent until the file below exists. A tool is enabled per API:

```
<work_dir>\overrides\CustomerApi\mcptools.json
```

```json
{
    "CustomerApi_getCustomer": {
        "description": "Looks up one customer ...",
        "enabled": true,
        "title": "Returns one customer by id",
        "toolType": "RestApi"
    }
}
```

The tool name is `<ApiName>_<operationId>`. A tool cannot be enabled through the admin REST
API - `PATCH` answers `BIP2788E` and `PUT`/`POST` answer `405` - so it is either the web UI
wizard or this file plus a restart. The file is what scripts.

Verified on 13.0.9.0 against this application:

- Both listeners log `BIP3132I`, as `MCP Streamable https` on 7650 and `MCP Runtime
  Streamable https` on 7750. Both are **https and Streamable HTTP**, not SSE.
- Runtime tool arguments are grouped by OpenAPI parameter location, so the call takes
  `{"path": {"customerId": 1001}}` and not `{"customerId": 1001}`.
- `describe_message_flow` can only describe a REST API's generated entry flow
  (`gen.CustomerApi`). An operation subflow is not addressable, so a node property that
  lives inside one - such as a Compute node's Data source - is invisible to that tool.

## Demo choices that are not production choices

Two things in this application are deliberate and would not ship as they are:

1. **The 500 body carries the ACE exception text.** The team convention is an opaque 5xx
   body with the detail only in the log, keyed by a tracking id. This API is driven by
   tools rather than by a person who can read a log, and the whole point of the demo is
   that the caller can see why the read failed, so the detail goes in the body. The
   tracking id is in the body as well, so the log line can still be found.
2. **The listener is plain HTTP.** The convention is HTTPS on anything inbound. This
   runs on `localhost` for a recording and `https="false"` in `restapi.descriptor` keeps
   the setup to one step. Crossing any trust boundary means turning that back on.

No logging library is wired into the error path on purpose. A logging subflow there can
swallow the exception text that this application's 500 response is built from, and that
text is the thing the demo is about.
