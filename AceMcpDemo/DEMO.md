# ACE MCP demo - runbook

Two demos on one stand-alone integration server running IBM App Connect Enterprise
13.0.9.0:

- **Demo 1** - a wrong database credential, found with the MCP Admin Server's tools.
- **Demo 2** - a deployed REST API operation exposed as an MCP tool and called by an agent.

A third MCP flavour exists (connector actions as MCP tools) but it cannot run here; see
"Demo 3" at the end.

Everything below was executed on this machine before it was written down. Where a tool
behaves differently from the IBM documentation, the observed behaviour is what is
recorded, and the difference is called out.

---

## What the audience is meant to take away

A deployed integration fails on the first message. The agent never reads the flow source,
never opens the Toolkit, and never gets a shell on the box. It asks the runtime about
itself over MCP, works out which of two credentials the failing node actually uses, and
produces the fix. The point is not that an LLM can read an error message. The point is
that the runtime can now answer questions about itself.

---

## The setup

| Piece | Where |
|---|---|
| Integration server `ACEMCPDEMO` | work dir `D:\tmp\ace-mcp-demo\MCPDEMO` |
| Application `CustomerLookup` | one top-level flow, HTTP in, MQ audit, ODBC read |
| REST API `CustomerApi` | `GET /customers/{customerId}`, same database |
| Policy project `CustomerApiPolicies` | `DEMO_MQ` MQEndpoint policy |
| Database | SQL Server 2022 in container `ace-sql`, database `AceTest` |
| Queue manager | `QM1` in a container, queue `DEV.MCP.AUDIT` |
| Vault | external directory vault, `D:\tmp\ace-mcp-demo\vault` |

Ports: **7620** admin REST (https), **7820** HTTP, **7860** HTTPS, **7650** MCP.Admin,
**7750** MCP.Runtime.

The vault key and the correct database password are in
`D:\tmp\ace-mcp-demo\demo-credentials.txt`, deliberately outside every git repository.
Nothing in this repository contains a working credential. The only password committed
here is `WrongPassword1`, which is safe precisely because it is invalid.

### Both flows do the same three things

```
HTTP in  ->  build audit record  ->  MQ put to DEV.MCP.AUDIT  ->  ODBC read  ->  reply
```

The MQ write happens **before** the database read on purpose. When the credential is
wrong, the audit record still lands on the queue, so "MQ is fine, the database is not" is
visible rather than argued.

---

## Before you record

### 1. Containers

```bash
docker start ace-sql QM1
```

`ace-sql` takes 30 to 60 seconds to accept connections. Check it:

```bash
docker exec QM1 bash -lc 'printf "DISPLAY QLOCAL(DEV.MCP.AUDIT) CURDEPTH\n" | runmqsc QM1'
```

### 2. Put the demo in its broken starting state

Set the credential while the server is still down - step 3 starts it. If it is already
running, the write takes effect without a restart (`odbc` is a dynamic credential type),
but read "Resetting between takes" first: breaking a working credential has a one-request
lag.

```bat
ibmint set credential --external-directory-vault D:\tmp\ace-mcp-demo\vault ^
    --external-directory-vault-key <vaultKey> ^
    --credential-type odbc --credential-name ACE_SQL_TEST ^
    --username ace_demo --password WrongPassword1
```

### 3. Start the server

```bat
D:\tmp\ace-mcp-demo\start-demo-server.cmd <vaultKey>
```

It is ready when the console shows `BIP1991I`. On a warm machine that is about 12
seconds. Two more lines confirm both MCP servers:

```
BIP3132I: ... port '7650' for 'MCP Streamable https' connections.
BIP3132I: ... port '7750' for 'MCP Runtime Streamable https' connections.
```

If 7750 is missing, `overrides\CustomerApi\mcptools.json` is absent. `mcpStartMode:
automatic` only starts the runtime listener once a tool is deployed.

### 4. Drain the audit queue so the depth is a clean zero on camera

```bash
docker exec QM1 bash -lc 'printf "CLEAR QLOCAL(DEV.MCP.AUDIT)\n" | runmqsc QM1'
```

---

## Demo 1 - finding a wrong database credential

### Step 1. Show it failing

```bash
curl -s "http://localhost:7820/customerlookup/customers?customerId=1001"
```

```json
{
  "status": "internal server error",
  "description": "com.demo.lookup.CustomerLookupFlow.Read customer over ODBC DSN ACE_SQL_TEST | ACE_SQL_TEST | ODBC | ace_demo | -1 | odbc32.dll | 28000 | 18456 | [Microsoft][ODBC SQL Server Driver][SQL Server]Login failed for user 'ace_demo'. | IM006 | 0 | [Microsoft][ODBC Driver Manager] Driver's SQLSetConnectAttr failed",
  "bipCode": 2322,
  "source": "exception path",
  "trackingId": "..."
}
```

Then show the queue, which proves the message got that far:

```bash
docker exec QM1 bash -lc 'printf "DISPLAY QLOCAL(DEV.MCP.AUDIT) CURDEPTH\n" | runmqsc QM1'
```

`CURDEPTH(1)`. MQ worked. The database did not.

### Step 2. Hand it to the agent

Ask the agent something close to:

> The CustomerLookup application on the ACE server is returning 500 on every request.
> Use the admin MCP tools to work out what is wrong and give me the commands to fix it.

What follows is the chain it should walk. Each tool is worth pausing on.

**`info`** - which runtime am I even talking to.

```json
{ "name": "ACEMCPDEMO", "type": "integrationServer",
  "metadata": { "version": "13.0.9.0", "isRunning": true, "restAdminListenerPort": 7620, ... } }
```

**`list_integrations`** - is this a deployment problem?

`CustomerLookup` is `"state": "started"`. It deployed fine. That rules out a broken BAR
and points at something the flow reaches only when a message arrives. For an application
this tool also lists the message flows and even the ESQL files, so it hands you the flow
name for the next step: `com.demo.lookup.CustomerLookupFlow`.

**`describe_message_flow`** - what does the flow actually touch?

```
Receive customer lookup request
Screen id and build audit record
Audit to DEV.MCP.AUDIT
Read customer over ODBC DSN ACE_SQL_TEST
Return reply
Build 500 reply from exception
Log exception to system log
```

Two external systems, named. The audit node names its queue; the failing node names its
DSN.

**`list_application_needs`** - what does the application declare?

```json
{ "credentials": [], "policies": ["{CustomerApiPolicies}:DEMO_MQ"] }
```

Worth saying out loud: the MQ policy is declared, the database credential is **not**. ACE
does not report an ODBC DSN credential as an application need. An empty `credentials` list
does not mean the application needs no credentials, and an agent that stops here gets the
wrong answer.

**`list_policies`** with `details: true` - is the MQ side configured correctly?

Returns the full `DEMO_MQ` policy: `QM1`, `localhost`, `1414`,
`INSECURE.APP.SVRCONN`, empty `securityIdentity`. It is fine, which matches the audit
record already sitting on the queue.

**`list_credentials`** - what does the vault hold?

```json
{ "odbc-credentials": ["ACE_SQL_TEST", "LEGACY_SQL_TEST"] }
```

Two ODBC credentials. This is the moment the demo turns on: the tool returns names and
types but never values, so nothing here says which one is wrong or even which one is
used. Only `describe_message_flow` said the failing node reads over `ACE_SQL_TEST`.
`LEGACY_SQL_TEST` is a decoy and should be ignored.

### Step 3. The conclusion the agent should reach

The application is started, so this is not a deployment fault. MQ succeeded, so the
network and the queue manager are fine. The failing node reads over DSN `ACE_SQL_TEST`, a
credential of that exact name exists in the vault, and the database rejected the login for
user `ace_demo` with SQL state 28000 and native error 18456. The credential exists and
names the right user, so its **password** is wrong.

### Step 4. The fix

```bat
ibmint set credential --external-directory-vault D:\tmp\ace-mcp-demo\vault ^
    --external-directory-vault-key <vaultKey> ^
    --credential-type odbc --credential-name ACE_SQL_TEST ^
    --username ace_demo --password <the real password>
```

Answers `BIP15119I: The 'UPDATE' action was successful for credential name
'ACE_SQL_TEST' of type 'odbc'.`

That is the whole fix. No restart and no redeploy: `odbc` is a dynamic credential type, so
the running server resolves the new password on its next connection attempt and the very
next request answers `200` (measured on 13.0.9.0, 2026-11-08). If an agent tells you to
restart here, it is repeating something it read, not something the runtime requires -
`ibmint display credential-types` is the check.

### Step 5. Show it working

```bash
curl -s "http://localhost:7820/customerlookup/customers?customerId=1001"
```

```json
{"customerId":1001,"customerName":"Northwind Traders","city":"Brussels","status":"ACTIVE"}
```

And a row that does not exist returns a clean 404 rather than an error:

```bash
curl -s "http://localhost:7820/customerlookup/customers?customerId=9999"
```

---

## Demo 2 - a REST API operation as an MCP tool

The same database, reached through `CustomerApi` instead, exposed on MCP.Runtime.

### What makes it a tool

One file, plus a restart:

```
D:\tmp\ace-mcp-demo\MCPDEMO\overrides\CustomerApi\mcptools.json
```

```json
{
    "CustomerApi_getCustomer": {
        "description": "Looks up one customer in the demo database by numeric id ...",
        "enabled": true,
        "title": "Returns one customer by id",
        "toolType": "RestApi"
    }
}
```

The tool name is `<ApiName>_<operationId>`. The title and description are what an agent
reads when it decides whether to call the tool, so they are worth writing properly. The
web UI wizard writes this same file; a tool **cannot** be enabled through the admin REST
API, which answers `BIP2788E` to a `PATCH` and `405` to `PUT` and `POST`. The file is the
scriptable route, which is the CI/CD angle worth making on camera.

### Running it

Listing the tools on 7750 returns:

```
CustomerApi_getCustomer  (path*)  Looks up one customer in the demo database ...
```

Calling it, with the credential fixed:

```json
{"message":{"customerId":1001,"customerName":"Northwind Traders","city":"Brussels","status":"ACTIVE"}}
```

A missing row comes back as `"httpStatus": 404` with the body, so the agent sees the
status as data rather than as a transport failure.

**The argument shape is the thing people get wrong.** Parameters are grouped by OpenAPI
location, so it is:

```json
{"path": {"customerId": 1001}}
```

not `{"customerId": 1001}`. The wrong shape returns `Input validation error: ... path:
Invalid input: expected object, received undefined`.

### Tying the two demos together

With the broken credential in place, calling the MCP tool returns a 500 whose body carries
the whole ODBC diagnostic, including `Login failed for user 'ace_demo'`. So the agent can
trigger the failure through one MCP server and diagnose it through the other, without
touching the machine. That is the strongest single moment in the recording.

---

## Demo 3 - connector actions as MCP tools

Not in this setup, and the reason is a documented platform restriction rather than a
missing switch. Exposing **connector actions** (Salesforce, Slack, and so on) as MCP tools
is driven from the App Connect Dashboard, needs App Connect Operator 12.20.0 or later with
Dashboard 13.0.6.1-r1 or later, and every MCP server it creates is a whole new integration
runtime in its own pod, licensed like any other. None of it exists on a stand-alone
integration server.

The restriction that decides it is the hosting platform. The IBM documentation is explicit:
MCP servers are hosted in integration runtimes and are supported only in environments
"which allow the automatic creation of routes or ingresses for integration runtimes" - Red
Hat OpenShift (integration runtimes at 13.0.6.1-r1 or later) and IBM Cloud Kubernetes
Service (13.0.6.2-r1 or later). Plain Kubernetes is not on that list, and the same
restriction covers the Dashboard's other MCP server type, the one that exposes REST API
operations as MCP tools (Operator 13.3.0 or later with Dashboard 13.0.8.0-r1 or later).

That is not a theoretical limit. On a local minikube cluster the wizard runs, the
integration runtime comes up, the connector OAuth handshake with the SaaS side succeeds,
and then the Dashboard cannot register the account: it calls the integration runtime's
connector service on port 3001, which is configured with `SERVER_MTLS_CA_PATH` pointing at
the `adminssl` CA and expects a client certificate the Dashboard does not present, so the
TLS handshake fails before any HTTP response. The operator wires that up automatically on
OpenShift and on IBM Cloud Kubernetes Service, where it has Routes or IKS ingress to hang
it on. On plain Kubernetes it does not. The failure surfaces as `#authorizeConnection -
Failed to complete connection for <connector>`.

So a live Demo 3 needs one of:

- **An OpenShift cluster.** A TechZone reservation is the cheap route, and
  `demo3-openshift/` in this folder holds the whole path: what to reserve, the catalog
  source and subscription for operator 13.6.0, a Dashboard CR at operand 13.0.9.0-r1, the
  wizard walkthrough, and what to put on camera. Note that the older manifests in
  `D:\GIT\ace-minikube\agent-install\` carry license id `L-KPRV-AUG9NC`, which is correct
  for operator 12.1x and wrong from 13.4.0 onwards.
- **IBM Cloud Kubernetes Service**, with integration runtimes at 13.0.6.2-r1 or later and
  an `AppConnectEnterprise*` style license, plus the extra ingress step the documentation
  calls out for IKS.

Either way it also needs a SaaS connector account that can be authenticated on camera,
because the wizard does not finish without one.

For reference, the local minikube cluster currently runs its Dashboard and integration
runtime at 13.0.5.0-r1, below the 13.0.6.1-r1 floor, so the MCP servers page would not
appear there even before the platform restriction bites.

One thing to avoid blurring: the IBM documentation page titled "Exposing Connector APIs as
MCP tools in the web UI" is **not** this feature. That page is on-prem, uses port 7750, and
describes MCP.Runtime over deployed REST APIs, which is Demo 2.

---

## MCP client configuration

For 13.0.9.0, both servers speak **Streamable HTTP over https**:

```json
{
    "mcpServers": {
        "ace-admin": {
            "url": "https://localhost:7650/mcp",
            "type": "streamable-http"
        },
        "ace-runtime": {
            "url": "https://localhost:7750/mcp",
            "type": "streamable-http",
            "timeout": 30
        }
    }
}
```

Note for anyone following the older write-ups: on 13.0.7 the admin MCP server spoke SSE at
`/mcp/sse`. On 13.0.9.0 it logs `MCP Streamable https` and serves `/mcp`. The certificate
is self-signed, so a client needs to accept it.

A dependency-free command-line client is at
`ibm/ace/ace-admin-mcp/scripts/ace-mcp.mjs` in the bob_modes repository, useful for
rehearsing without an agent in the loop:

```bash
node ace-mcp.mjs --url https://localhost:7650/mcp --insecure tools
node ace-mcp.mjs --url https://localhost:7650/mcp --insecure call list_credentials '{}'
```

---

## Gotchas that will bite on camera

- **A credential change needs no restart - but breaking one looks like it did not work.**
  `odbc` is dynamic, so the fix is live on the next request. In the other direction the
  pooled ODBC connection is handed out exactly once more: after writing the wrong password
  back, the next request still answers `200` and only the one after it answers `500`.
  Twenty seconds of idling does not clear it, so this is the connection, not a timed
  refresh. Burn one request and confirm the `500` before the camera rolls.
- **7750 is absent until a tool is deployed.** `mcpStartMode: automatic` is doing its job,
  not failing.
- **`list_application_needs` requires `applicationName`** on a stand-alone server. The IBM
  documentation says it takes no parameters; the live tool declares it required and the
  call fails without it.
- **`describe_message_flow` returns node names, types and wiring - not node properties.**
  A Compute node's `metadata` comes back as `{}`, so its Data source attribute is
  invisible. The DSN is discoverable here only because the node is *named* for it. If you
  demo this on your own flows, name nodes after the external resources they touch, or the
  agent has nothing to go on.
- **It cannot describe a REST API's operation subflow.** Only the generated entry flow
  (`gen.CustomerApi`) is addressable; every operation-subflow name returns "Cant find
  flow". That is why this demo carries an application as well as a REST API.
- **An empty `credentials` list is not an answer.** ODBC DSN credentials are not reported
  as application needs.
- **Use ids 1001 to 1005.** A non-numeric id is screened out with a 400 before the
  database is touched, which is correct behaviour but looks like a different bug mid-demo.
- **Another integration server may hold port 7800.** This demo uses 7820 and says so in
  `openapi.json`; those two must move together.

---

## Resetting between takes

```bat
REM 1. break the credential again
ibmint set credential --external-directory-vault D:\tmp\ace-mcp-demo\vault ^
    --external-directory-vault-key <vaultKey> ^
    --credential-type odbc --credential-name ACE_SQL_TEST ^
    --username ace_demo --password WrongPassword1

REM 2. burn the pooled connection - this request still answers 200
curl.exe -s -i "http://localhost:7820/customerlookup/customers?customerId=1001" | findstr /b /c:"HTTP/"

REM 3. this one must answer 500 before you record
curl.exe -s -i "http://localhost:7820/customerlookup/customers?customerId=1001" | findstr /b /c:"HTTP/"
```

No restart is needed between takes. If you prefer one anyway, it is
`D:\tmp\ace-mcp-demo\start-demo-server.cmd <vaultKey>` after stopping the old process, and
it resets the MCP sessions of every connected client as well.

```bash
# 4. empty the audit queue
docker exec QM1 bash -lc 'printf "CLEAR QLOCAL(DEV.MCP.AUDIT)\n" | runmqsc QM1'
```

Stopping the server: it is a normal process, so stop it by its work directory rather than
by process name, because other integration servers may be running on the same machine.

---

## What this demo deliberately does not do

- **The 500 response carries the ACE exception text.** The team convention is an opaque
  5xx body with the detail in the log behind a tracking id. Here the caller is a tool
  rather than a person who can read a log, and the whole demo rests on the caller seeing
  the cause. The tracking id is in the body too, so the log line is still findable.
- **The inbound listener is plain HTTP.** Convention is HTTPS on anything inbound. This
  runs on localhost for a recording.
- **No logging library is wired into the error path**, because a logging subflow there can
  swallow the exception text the 500 is built from.
- **`MCP.Admin.readOnly` stays `true`.** On 13.0.9.0 every admin tool is a read anyway, but
  the switch is the guard for the day IBM ships write tools, and an unauthenticated admin
  MCP server hands an agent a full read of the runtime.
