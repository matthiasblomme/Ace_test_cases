# ACE MCP demo - run sheet

The take in recording order. `DEMO.md` holds the detail, the expected output and the
gotchas; this is what to do and say.

---

## Before you hit record

**Done for you** (containers, broken credential, empty queue, a test request whose body says
`Login failed for user 'ace_demo'`). Nothing involving MQ happens on camera.

**Yours, off camera:**

1. IBM Bob open on **`D:\demo\CustomerLookup`**, never the repo copy. The repo folder sits
   under `AceMcpDemo\`, next to DEMO.md and this run sheet, and an agent opened there reads
   them: the dry run on 2026-11-20 cited `DEMO.md:106` as its evidence. `D:\demo\CustomerLookup`
   is a copy holding only the application code, `.bob` (MCP servers, the ace-diagnose mode,
   doc-kb disabled) and `.claude` (doc-kb denied). No git, no docs above it. If the repo
   source changes, copy it again.
   Mode **ace-diagnose**. The MCP panel shows `ace-admin` and `ace-runtime` as `Connected`.
2. Two Bob terminals. In **each** one, type `cmd` first (Bob's terminals open in PowerShell,
   and `call` and `%VAR%` are cmd syntax), then:

   ```bat
   call D:\tmp\ace-mcp-demo\demo-env.cmd
   ```

   It loads the vault key into `%VK%` and the database password into `%PW%` without printing
   either, and sets up the ACE environment. From here on you type `%VK%` and `%PW%` on camera,
   never the values.
3. Browser tab on `https://localhost:7620`, certificate warning already clicked through.

---

## The take

### 1. Start the IS

Terminal 1:

```bat
D:\tmp\ace-mcp-demo\start-demo-server.cmd %VK%
```

Ready at `BIP1991I`. Point at the two `BIP3132I` lines: port 7650 is the admin MCP server, and
7750 is the runtime MCP server. Leave this terminal running - it is the server's log.

Start it from a terminal like this, never as a background job of another tool: run that way
on 2026-11-20, it could not find the ODBC DSN (`IM002`), and the demo shows the wrong failure.

### 2. Walk through the setup

- **Web UI**, `https://localhost:7620`: `CustomerLookup` and `CustomerApi`, both `started`.
  Nothing red. *Everything the console can show you says this integration is healthy.*
- **`D:\tmp\ace-mcp-demo\MCPDEMO\overrides\server.conf.yaml`**: the `MCP` block - Admin
  enabled on 7650 with `readOnly: true`, Runtime on 7750 with `mcpStartMode: automatic`.
  Changes go in `overrides`, never in the base `server.conf.yaml`.
- **`overrides\admin-mcp-tools.json`**: the six admin tools, all read-only.
- **`overrides\CustomerApi\mcptools.json`**: what turns a REST operation into an MCP tool
  (`CustomerApi_getCustomer`) - for the optional demo 2.
- **Bob's side**, `CustomerLookup\.bob\mcp.json`: both servers, streamable-http on `/mcp`.
  Then the **Bob Settings** tab, MCP: **refresh** `ace-admin` and `ace-runtime` first. If Bob
  loaded before the IS was up they show as failed and do not reconnect on their own. Show
  both `Connected`. Then **Modes**, and open **ACE Diagnose**: description, scope, role
  definition, when to use. After the refresh, start a **new Bob task** before the prompt -
  a task keeps the tool set it was created with.

### 3. curl, see it fail

Terminal 2:

```bat
curl.exe -s "http://localhost:7820/customerlookup/customers?customerId=1001"
```

A 500, and read the body out: `Login failed for user 'ace_demo'`, SQL state `28000`, native
error `18456`. *The deployment says healthy; the first request says no.*

### 4. Bob diagnoses

Prompt, in the ace-diagnose mode:

> CustomerLookup returns 500 on every request on the ACE server. This is what the caller
> gets back: <paste the body from step 3>. Use the ace-diagnose skill. You have the
> application source in this folder and the ACE MCP servers. Investigate the runtime before
> you conclude: what is deployed and in which state, what the failing flow touches, what the
> application needs, and which policies and credentials the server holds. Call the MCP tools
> one at a time. The server keeps its credentials in an external directory vault. Give me
> the cause, the evidence for it, and the command to fix it.

What to expect, and what to say over it:

- **The source**: the flow names its DSN, `ACE_SQL_TEST`.
- **`list_integrations`**: `started` - not a deployment fault, as the web UI showed.
- **`describe_message_flow`**: the deployed flow matches the source; the failing node is
  named for the DSN.
- **`list_application_needs`**: `credentials` is **empty**. *The trap: ODBC credentials are
  never reported as needs. An agent that stops here gets it wrong.*
- **`list_policies`**: the MQ side is fine.
- **`list_credentials`**: two ODBC names, `ACE_SQL_TEST` and `LEGACY_SQL_TEST`, and **no
  values**. Only the flow says which one is used; the other is a decoy.
- **Verdict**: the credential `ACE_SQL_TEST` exists and names the right user, so its
  password is wrong. Bob hands over the command with the secrets as placeholders.

### 5. Fix the credential

**Not** the IS terminal - that one is busy running the server. Use the second terminal,
the one you ran curl in. If it is still PowerShell (no `demo-env` loaded), type `cmd` and
`call D:\tmp\ace-mcp-demo\demo-env.cmd` there first. Then take Bob's command and put `%VK%`
and `%PW%` where its placeholders are:

```bat
ibmint set credential --external-directory-vault D:\tmp\ace-mcp-demo\vault --external-directory-vault-key %VK% --credential-type odbc --credential-name ACE_SQL_TEST --username ace_demo --password %PW%
```

Expect `BIP15119I: The 'UPDATE' action was successful for credential name 'ACE_SQL_TEST' of
type 'odbc'.` *No restart, no redeploy: `odbc` is a dynamic credential type.*

### 6. curl again

```bat
curl.exe -s "http://localhost:7820/customerlookup/customers?customerId=1001"
```

### 7. Success

```json
{"customerId":1001,"customerName":"Northwind Traders","city":"Brussels","status":"ACTIVE"}
```

**Optional demo 2**: ask Bob to call `CustomerApi_getCustomer` on the runtime MCP server
with `{"path": {"customerId": 1001}}`. Same database, now reached as an MCP tool.

---

## Between takes

Ask Claude to reset it: credential back to the wrong password, one request to burn the
pooled connection, a second that must say `Login failed`, queue drained. No restart needed.

And the folder: delete `D:\demo\CustomerLookup\DIAGNOSIS.md`. The ace-diagnose skill writes
it, and the next agent reads the old one before writing its own - on camera that shows as
"Existing DIAGNOSIS.md". After the reset the folder holds only the code, `.bob`, `.claude`,
`.settings`, `.vscode`, `.project` and `application.descriptor`.
