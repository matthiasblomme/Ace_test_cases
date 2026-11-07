# Local end-to-end test - AceMcpDemo

How `CustomerApi` is exercised on a local box: one REST operation that audits to IBM MQ
and then reads one row over ODBC, with both dependencies running in containers on
`localhost`. No production endpoint is involved anywhere.

**Status: nothing in this file has been run yet.** The projects were written first; the
build, the deploy and the scenarios below are the plan, not a record. Fill in the result
lines as they are executed.

## What's under test

The full `GET /customers/{customerId}` path: HTTP Input in the generated entry flow,
route to the `getCustomer` label, build an audit record, put it on `DEV.MCP.AUDIT`, read
`dbo.DEMO_CUSTOMERS` through the `ACE_SQL_TEST` ODBC DSN, and reply. Plus the three
error paths the REST API declares (catch, failure, timeout), which all answer `500` with
the ACE exception text in the body.

The audit write deliberately runs **before** the database read. That ordering is the
thing under test as much as the happy path: with a wrong database credential in place,
MQ must still succeed and only the read must fail.

## Components

| Component | Detail |
|---|---|
| **QM1** | IBM MQ in a container, listener on 1414, `CHLAUTH(DISABLED)`. Connects over channel `INSECURE.APP.SVRCONN`, which has `MCAUSER(app)` and a blank `SSLCIPH`: a plain client connection, no TLS, no credential. Queue `DEV.MCP.AUDIT` must exist. It is in the `DEV.**` namespace on purpose, because the MQ developer image grants user `app` authority there. |
| **ace-sql** | SQL Server 2022 in a container on port 1433, database `AceTest`. Table `dbo.DEMO_CUSTOMERS` (`CUSTOMER_ID` int primary key, `CUSTOMER_NAME`, `CITY`, `STATUS`) holds 5 rows with ids 1001 to 1005. The SQL login used by the DSN needs `db_datareader` on `AceTest` and nothing more. |
| **ACE_SQL_TEST** | Windows system DSN (driver `SQLSRV32.dll`) pointing at that database. The Compute node names this DSN literally in its Data source property. |
| **CustomerApiPolicies** | Supplies the `DEMO_MQ` MQEndpoint policy that the server uses to reach QM1 in CLIENT mode. Staged as a folder under `<work_dir>/run/`, never inside the BAR. |
| **CustomerApi.server-overrides** | Holds the one server-level key the application needs, `remoteDefaultQueueManager`, plus the commented vault block. Its keys are merged into `<work_dir>/overrides/server.conf.yaml`. |
| Vault | External directory vault (`ibmint create vault <dir> --external-directory-vault-key <key>`), pointed at from `overrides/server.conf.yaml` and unlocked at server start with `--ext-vault-key`. Holds one `odbc` credential named `ACE_SQL_TEST`. |

## Build + deploy

Full steps, including the vault and the credential, are in `CustomerApi/DEPLOY.md`. The
short form:

```bat
REM 1. Dependencies up (skip whichever is already running)
docker start <mqContainer>
docker start ace-sql

REM 2. Queue definition, if it is not there yet
docker exec -i <mqContainer> bash -c "echo 'DEFINE QLOCAL(DEV.MCP.AUDIT) REPLACE' | runmqsc QM1"

REM 3. Work directory
"C:\Program Files\IBM\ACE\13.0.9.0\server\bin\mqsicreateworkdir" C:\aceWork\acemcpdemo

REM 4. Server overrides - copy the keys from CustomerApi.server-overrides\server.conf.yaml
REM    into C:\aceWork\acemcpdemo\overrides\server.conf.yaml.
REM    NEVER edit the generated C:\aceWork\acemcpdemo\server.conf.yaml.

REM 5. Vault + ODBC credential (DEPLOY.md section 3)

REM 6. Policy project as a folder under run\, not in the BAR (BIP1388E otherwise)
xcopy /E /I /Y CustomerApiPolicies C:\aceWork\acemcpdemo\run\CustomerApiPolicies

REM 7. Package the application on its own and drop the BAR in run\
mkdir C:\aceWork\stage
mklink /J C:\aceWork\stage\CustomerApi <repo>\AceMcpDemo\CustomerApi
ibmint package --input-path C:\aceWork\stage --output-bar-file C:\aceWork\CustomerApi.bar
copy /Y C:\aceWork\CustomerApi.bar C:\aceWork\acemcpdemo\run\

REM 8. Start the server in its own window - it never exits
IntegrationServer --work-dir C:\aceWork\acemcpdemo --name ACEMCPDEMO ^
    --ext-vault-key <vaultKey> --console-log
```

## Test scenarios

No driver scripts ship with this demo: four curl calls and one queue browse cover it.
Record the outcome of each as `[S<n>] PASS|FAIL - <evidence>`.

### S1 - Dependencies answer (no ACE involved)

```bat
REM The queue manager is up and the audit queue exists
docker exec <mqContainer> bash -c "echo 'DISPLAY QLOCAL(DEV.MCP.AUDIT) CURDEPTH' | runmqsc QM1"

REM The DSN resolves and the table has its 5 rows. Any ODBC client will do; this is
REM the same path ACE takes.
REM Expect ids 1001 to 1005.
```

Result: _not yet run_

### S2 - Happy path: HTTP -> MQ -> ODBC -> HTTP

```bat
curl -i http://localhost:7820/customerapi/customers/1001
```

Expect `200` and a body with exactly four fields:

```json
{"customerId":1001,"customerName":"...","city":"...","status":"..."}
```

Then browse the audit record the same request produced:

```bat
docker exec <mqContainer> bash -c "/opt/mqm/samp/bin/amqsbcg DEV.MCP.AUDIT QM1" | more
```

Expect one JSON message carrying `trackingId`, `operation: getCustomer`,
`customerId: 1001` and `requestedAtUtc`. The message is plain JSON with no MQRFH2: the
flow adds none, and the MQOutput node builds the MQMD itself.

Result: **PASS** on 13.0.9.0, server `ACEMCPDEMO`. `200` with
`{"customerId":1001,"customerName":"Northwind Traders","city":"Brussels","status":"ACTIVE"}`;
1003 returned Contoso Ltd / Ghent / SUSPENDED. `DEV.MCP.AUDIT` depth rose by one per
request. Note the port is 7820, not the 7800 default: another integration server holds
7800 on this machine, and `openapi.json` carries the same 7820.

### S3 - Error paths

| Case | Drive it with | Expect |
|---|---|---|
| No such row | `curl -i http://localhost:7820/customerapi/customers/9999` | `404`, body `{"status":"not found","description":"No customer matches customerId 9999","trackingId":"..."}`. An audit record for 9999 is still on the queue: the audit is of the request, not of the result. |
| Non-numeric id | `curl -i http://localhost:7820/customerapi/customers/abc` | `500`. The id has to be cast to an integer for the key column, and a non-numeric segment throws. The cast exception text comes back in `description`. |
| Broken database credential | Replace the `odbc` credential named `ACE_SQL_TEST` with a wrong password, restart the server, then `curl -i http://localhost:7820/customerapi/customers/1001` | The application still reaches state `started` and the deploy is clean. The request answers `500` with the ODBC login failure text in `description` and the BIP number in `bipCode`; the audit record for 1001 is on the queue. |

Result: **PASS**, with one defect found and fixed. The 404 and the broken-credential case
behave as described: the application reaches `started`, the request answers `500`, and the
audit record is on the queue.

The defect was in the error extraction, not in the flow. The handlers originally reported
the *deepest* exception record, and for an ODBC failure that is the driver manager's
`IM006 Driver's SQLSetConnectAttr failed` - useless. ACE emits one `BIP2322` per ODBC
diagnostic record as **siblings**, so the record naming the real cause (`28000`, `18456`,
`Login failed for user 'ace_demo'`) is never on the `LASTCHILD` path. All three handler
modules now scan every numbered child at each level, and the accumulator compares
delimiter-wrapped values so that a short insert such as the native error `0` is not
discarded as a substring of `28000`. Verified: the `500` body now carries the DSN, the
user, the SQL state, the native error and the login failure text.

## What to watch for

In the server console or log:

- `BIP1991I` - the server is listening. Nothing before this proves anything.
- `BIP9332I` - the `CustomerApi` application loaded.
- `BIP2087E` / `BIP2688E` around MQ - the `DEMO_MQ` policy is not being applied. Check
  that `remoteDefaultQueueManager` is in `overrides\server.conf.yaml` (not in the base
  `server.conf.yaml`) and that `CustomerApiPolicies` is a folder under `run\`.
- `BIP10003E` from `ibmint set credential` - the vault does not exist yet. Create it
  first.
- `BIP1388E` from `ibmint package` - an MQ policy project ended up inside the BAR.
- Lines prefixed `CustomerApi catch handler:` - the Trace node in the catch handler.
  They carry the full exception list and the `Environment` tree, which holds the SQL
  state, the native error, the ODBC error text and the tracking id.

On the three error paths, the tracking id in the `500` body and the tracking id in that
log line are the same value, so one grep ties a caller's complaint to a log entry.

## Cleanup

```bat
REM Stop the integration server: Ctrl-C in its console, or by the PID on THIS run's
REM admin port:
REM   netstat -ano | findstr LISTENING | findstr :<adminPort>   -> PID in the last column
REM   taskkill /PID <pid> /F
REM Never taskkill /IM IntegrationServer.exe /F - that kills every integration server
REM on the machine.

REM The containers and the DSN are shared local infrastructure. Leave them alone unless
REM you created them for this test.
```

The work directory, the staging junction and the BAR are the only artifacts this test
creates outside the repository. Nothing in this repository is modified by a test run.

## Things this test cannot prove

- That the `DEMO_MQ` policy values are right for anything other than this box. Host,
  port and channel are the local container's.
- Anything about TLS. The listener is plain HTTP and the MQ channel has a blank
  `SSLCIPH` on purpose, so no certificate path is exercised.
- Behaviour under concurrency. One request at a time says nothing about the ODBC
  connection pool or MQ connection reuse.
- Whether the audit put and the database read are in one unit of work. They are not, by
  design: the audit record survives a failed read, which is exactly what the demo needs
  to show.
- Anything the Toolkit checks that the runtime does not. A clean deploy does not prove
  the `nsURI` / `nsPrefix` forms or the uniqueness of node labels; those surface only on
  import into the Toolkit.
