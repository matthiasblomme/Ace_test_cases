# demo-server - the files behind the demo integration server

The demo server `ACEMCPDEMO` needs more than the CustomerApi application's own server keys:
both MCP servers, the listener ports, and two scripts that keep the vault key and the
database password off camera. Those files lived only on the recording machine; these are
their copies, with the machine paths taken out.

| File | Goes to | What |
|---|---|---|
| `overrides/server.conf.yaml` | `<work_dir>/overrides/server.conf.yaml` | Complete demo overrides: ports 7620 / 7820 / 7860, `remoteDefaultQueueManager`, the admin MCP server (7650, `readOnly: true`) and the runtime MCP server (7750). Superset of `../CustomerApi.server-overrides/server.conf.yaml`. |
| `overrides/admin-mcp-tools.json` | `<work_dir>/overrides/admin-mcp-tools.json` | The six admin tools, all enabled and read-only. Same tools as `../../AdminMcpTopologies/docker/admin-mcp-tools.json`, pretty-printed. |
| `start-demo-server.cmd` | `<demo_root>/` | Starts the server from `<demo_root>/MCPDEMO` with the vault key as its only argument. |
| `demo-env.cmd` | `<demo_root>/` | `call` it once per terminal: loads `%VK%` and `%PW%` from `<demo_root>/demo-credentials.txt` without printing them, plus the ACE profile. |

`CustomerApi/mcptools.json` comes from `../CustomerApi.server-overrides/` and goes to
`<work_dir>/overrides/CustomerApi/mcptools.json`; without it the runtime MCP server on 7750
never starts.

## Demo root

`<demo_root>` is one folder outside every git repository (`D:\tmp\ace-mcp-demo` on the
recording machine, which is the path `../DEMO.md` and `../RUNSHEET.md` use):

```
<demo_root>/
  MCPDEMO/                  work dir (mqsicreateworkdir), overrides as above
  vault/                    external directory vault (ibmint create vault)
  demo-credentials.txt      "Vault key: <vaultKey>" and "Password: <password>", never committed
  start-demo-server.cmd
  demo-env.cmd
```

Creating the work dir, the vault and the ODBC credential is in
`../CustomerApi/DEPLOY.md`, steps 1 to 3. Then uncomment the `Credentials` block in
`overrides/server.conf.yaml` with the vault directory. Both scripts find everything relative
to their own folder, and take the ACE install from `%ACE_INSTALL%` when it is set
(default `C:\Program Files\IBM\ACE\13.0.9.0`).
