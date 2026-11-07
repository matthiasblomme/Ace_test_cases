# Deploying CustomerLookup

Target runtime: IBM App Connect Enterprise 13.0.9.0 on Windows, standalone (independent)
integration server `ACEMCPDEMO`. Install root `C:\Program Files\IBM\ACE\13.0.9.0`. The
server that runs this demo today has its work directory at
`D:\tmp\ace-mcp-demo\MCPDEMO`; everything below writes `<work_dir>` so another location
works the same way.

## Why this application exists next to CustomerApi

`CustomerApi` is a REST API, and a REST API has exactly one message flow: the generated
entry flow `gen.CustomerApi`. Every operation lives in a subflow, and the admin MCP tool
`describe_message_flow` answers "Cant find flow" for a subflow name - so a Compute node's
Data source property inside an operation subflow is invisible to it.

`CustomerLookup` is a plain application with one **top-level** `.msgflow`, so the Compute
node carrying `dataSource="ACE_SQL_TEST"` sits where `describe_message_flow` can read it.
That is the only reason this project exists; the business logic is the same lookup.

The two applications can run on the same server at the same time:

| | CustomerApi | CustomerLookup |
|---|---|---|
| Project type | REST API | application |
| Broker schema | `com.demo.customers` | `com.demo.lookup` |
| Endpoint | `/customerapi/customers/{customerId}` | `/customerlookup/customers?customerId=<id>` |
| Audit queue | `DEV.MCP.AUDIT` | `DEV.MCP.AUDIT` |
| ODBC DSN | `ACE_SQL_TEST` | `ACE_SQL_TEST` |

The broker schemas differ on purpose: ESQL module names are resolved per schema, so two
applications holding a module of the same name in the same schema would collide at load.

## 0. What this application needs

Nothing new. Everything it depends on is already in place for `CustomerApi`:

- **IBM MQ** queue manager `QM1` at `localhost:1414` over channel `INSECURE.APP.SVRCONN`
  (plain client connection, no TLS, no credential), with queue `DEV.MCP.AUDIT` defined.
- **The `CustomerApiPolicies` policy project** staged under `<work_dir>\run\`, applying
  its `DEMO_MQ` MQEndpoint policy server-wide. `CustomerLookup` ships **no policy project
  of its own** - the MQOutput node carries only `queueName` and picks up the queue manager
  from that server-wide default.
- **The ODBC data source** `ACE_SQL_TEST` (Windows system DSN) pointing at the SQL Server
  database that holds `dbo.DEMO_CUSTOMERS`, with a credential of the same name in the
  vault.
- **HTTP on port 7820.** The demo server overrides the default 7800 because 7800 belongs
  to a different integration server on the same machine.

Confirm the one key that binds the MQ node is present in `<work_dir>\overrides\server.conf.yaml`:

```yaml
remoteDefaultQueueManager: '{CustomerApiPolicies}:DEMO_MQ'
```

`CustomerLookup` adds no override keys, so there is no `CustomerLookup.server-overrides`
folder. The generated `<work_dir>\server.conf.yaml` is never edited - it is read-only
reference material that documents every key with its default. Every change goes in
`overrides\*.yaml`.

## 1. Package

Stage only this application so nothing else in the repository is dragged into the BAR.

```bat
mkdir C:\aceWork\stage-lookup
mklink /J C:\aceWork\stage-lookup\CustomerLookup <repo>\AceMcpDemo\CustomerLookup

ibmint package --input-path C:\aceWork\stage-lookup ^
    --output-bar-file C:\aceWork\CustomerLookup.bar
```

`mklink /J` needs no administrator rights and the junction references rather than copies.

Packaging both applications in one BAR works too - point `--input-path` at a stage folder
holding junctions to `CustomerApi` and `CustomerLookup`. One BAR per application is easier
to redeploy independently during a recording.

## 2. Deploy

```bat
copy /Y C:\aceWork\CustomerLookup.bar <work_dir>\run\
```

A standalone server picks up a BAR dropped in `run\` at start. For a running server,
deploy over the admin REST API or restart it. The policy project is **not** in the BAR -
a BAR containing an MQ policy project fails with `BIP1388E`.

Expect `BIP9332I` for `CustomerLookup` in the console log, and the application in state
`started` even when the ODBC credential is wrong (see section 5).

## 3. Verify

```bat
REM 200 - a row that exists
curl -i "http://localhost:7820/customerlookup/customers?customerId=1001"

REM 404 - a row that does not
curl -i "http://localhost:7820/customerlookup/customers?customerId=9999"

REM 400 - no id, or not a number
curl -i "http://localhost:7820/customerlookup/customers"
curl -i "http://localhost:7820/customerlookup/customers?customerId=abc"

REM The audit records those requests put on the queue
docker exec <mqContainer> bash -c "/opt/mqm/samp/bin/amqsbcg DEV.MCP.AUDIT QM1" | more
```

| Request | Status | Body |
|---|---|---|
| `?customerId=1001` | 200 | `{customerId, customerName, city, status}` |
| `?customerId=9999` | 404 | `{status: "not found", description, trackingId}` |
| no id / `?customerId=abc` | 400 | `{status: "bad request", description, receivedCustomerId, trackingId}` |
| any other failure | 500 | `{status, description, bipCode, source, trackingId}` |

The 200 and 404 requests each leave one audit record on `DEV.MCP.AUDIT` carrying the same
`customerId` and a `trackingId`. The 400 requests leave **none**: the id is screened in the
first Compute, before the queue and before the DSN, so a bad request never looks like a
database problem.

## 4. The flow's shape, and why it is that shape

```
HTTP Input /customerlookup/customers
  -> Screen id and build audit record        (Compute, out1 -> 400 reply)
  -> Audit to DEV.MCP.AUDIT                  (MQOutput, queueName only)
  -> Read customer and build reply           (Compute, dataSource ACE_SQL_TEST)
  -> Return reply                            (HTTP Reply)

HTTP Input catch + failure, and the database Compute's failure
  -> Build 500 reply from exception          (Compute)
  -> Log exception to system log             (Trace, localError)
  -> Return reply
```

Two node properties carry the demo:

- `dataSource="ACE_SQL_TEST"` is the literal DSN name on the Compute node, not a property
  reference. It is how an operator - or a tool reading the flow - knows which credential a
  failing read needs. It sits on a node of the top-level flow, never in a subflow.
- `connectDatasourceBeforeFlowStarts="false"` is the node's own default, written out
  because the demo depends on it. The node connects on the first message, so a wrong
  credential still deploys and still reaches state `started`.

## 5. When the database read fails

A wrong or missing ODBC credential does **not** stop the deployment. The failure arrives
on the first request:

- The audit record is on `DEV.MCP.AUDIT` - MQ worked.
- The request comes back `500`, with the ACE exception text in `description`, the BIP
  number in `bipCode`, and `failedOperation: "read from DEMO_CUSTOMERS"`.
- The same context plus the full exception list is in the integration server's local error
  log, written by the Trace node and prefixed `CustomerLookupFlow error`.

The `description` is assembled by walking **every** exception record that carries a
`Number`, siblings included, not just the last child. ACE emits one BIP2322 per ODBC
diagnostic record as siblings: for a rejected login the first carries SQL state `28000`,
native error `18456` and `Login failed for user ...`, while the last carries only the
driver manager's `IM006 SQLSetConnectAttr failed`. A last-child-only walk reports the
wrapper and hides the real cause, which is exactly the fact the demo turns on.

Fix the credential and restart the server:

```bat
ibmint set credential --external-directory-vault C:\aceWork\vault ^
    --external-directory-vault-key <vaultKey> ^
    --credential-type odbc --credential-name ACE_SQL_TEST ^
    --username <sqlUser> --password <sqlPassword>
```

A credential change is read at integration server startup, so restart the server.
Redeploying the application alone is not enough. The credential name must equal the DSN
name - that is how ACE matches it to the Compute node's Data source property. No
credential value belongs in this repository, in the BAR, or in any file under this
project; the vault key is the only secret handled by hand.

For vault creation itself - a vault must exist before a credential can go into it, or
`ibmint set credential` fails with `BIP10003E` - follow `../CustomerApi/DEPLOY.md`
section 3. This application reuses that same vault and that same credential.

## 6. The MCP side

`CustomerLookup` is reached through **MCP.Admin** (port 7650, read-only), not MCP.Runtime.
MCP.Runtime exposes REST API operations as tools (`toolType: RestApi`); a plain application
has no operations to expose, so there is no `mcptools.json` for this project.

The demo step is `describe_message_flow` against this application's one flow. Unlike a
REST API, where only the generated entry flow `gen.CustomerApi` is addressable, the flow
here is the top-level `CustomerLookupFlow`, so the Compute node and its Data source are in
the tool's answer.

*Not yet measured:* the exact flow identifier `describe_message_flow` expects for an
application flow (plain `CustomerLookupFlow`, or qualified with the application name).
Confirm it with `list_message_flows` - or whichever enumerating tool is enabled in
`overrides\admin-mcp-tools.json` - on the first run, and correct this line if it differs.
Everything else in this file follows from the project contents.

## Demo choices that are not production choices

1. **The 500 body carries the ACE exception text.** The team convention is an opaque 5xx
   body with the detail only in the log, keyed by a tracking id. This endpoint is driven by
   tools rather than by a person who can read a log, and the point of the demo is that the
   caller can see why the read failed. The tracking id is in the body too, so the log line
   can still be found.
2. **The listener is plain HTTP.** The convention is HTTPS on anything inbound. This runs
   on `localhost` for a recording. Crossing any trust boundary means turning it back on.
3. **No logging library is wired into the error path.** A logging subflow there can swallow
   the exception text that the 500 response is built from, and that text is the thing the
   demo is about.
