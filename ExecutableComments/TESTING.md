# ExecutableComments - what the ESQL parsers do with `/*!{ }!*/`, `--!{` and `--@!{`

One tiny application per comment variant, all deployed to one standalone integration
server, plus a headless Toolkit build (`mqsicreatebar`) of each app in its own workspace.
The runtime answers "does the server execute it"; the Toolkit build answers "does the
Toolkit still validate the project". One app per variant because a runtime parse failure
takes the whole application down, so it has to isolate.

Results and the write-up: `D:\GIT\notes\notes\general\2026-09-15-esql-executable-comments.md`.
Everything below was run on ACE 13.0.8.1 on 2026-09-15.

## Files

| File | Role |
|---|---|
| `gen.py` | the variants (ESQL text per app) and the generator; `python gen.py` = round 1, `python gen.py 2` / `3` = rounds 2 and 3. Writes `work/ws/<App>` for ibmint, `work/tk/<App>/<App>` for mqsicreatebar, `work/apps.txt` with the round's app names |
| `msgflow.template.xml`, `project.template.xml` | HTTPInput (`/<app lowercased>`, JSON) -> Compute -> HTTPReply; copied from RowOverrideTest |
| `build.cmd` | `ibmint package` every app in `work/apps.txt`, `mqsicreateworkdir work\sis` once, `ibmint deploy --output-work-directory` each BAR. Server must be stopped |
| `server.cmd` | `chcp 1252` + `IntegrationServer --work-dir work\sis --admin-rest-api 7603 --http-port-number 7801 --console-log` |
| `toolkit.cmd` | `mqsicreatebar -data work\tk\<App> -b work\tkbars\<App>.bar -a <App> -cleanBuild`, log per app in `work\tkbars\<App>.log` (~45 s each) |
| `work/` | generated, git-ignored |

## Run

```bat
python gen.py            REM or: python gen.py 2   /   python gen.py 3
build.cmd  > work\build.log
start "" cmd /c "server.cmd > work\server.log 2>&1"
toolkit.cmd > work\toolkit.log
```

Wait for `BIP1991I` in `work\server.log`, then per app:

```bat
curl -s http://127.0.0.1:7801/execblock
```

Every variant returns `{"plain":"executed", ...,"after":"executed"}`; the keys between
`plain` and `after` are the ones the variant under test sets. A key that is missing means
the runtime treated the text as a comment; an app missing from the server altogether has a
`BIP4125E` / `BIP2401E` / `BIP2432E` / `BIP4127E` in `work\server.log` naming it.

Toolkit verdict per app: `work\tkbars\<App>.log` ends in `BIP0986I` (clean) or `BIP0965E`
followed by a `Problem markers list` (rejected).

Stop the server port-scoped (never `taskkill /IM IntegrationServer.exe`, that kills every
integration server on the box):

```powershell
$conn = netstat -ano | Select-String ':7603\s.*LISTENING'
taskkill /PID (($conn -split '\s+')[-1]) /F
```

## Variants

Round 1: `ExecBlock` (block form one-line and multi-line next to normal comments as
controls), `ExecLineOpen` (`--!{ code`), `ExecLineClosed` (`--!{ code }!`),
`ExecFirstLineSchema` (`/*!{ BROKER SCHEMA }!*/` on line 1), `ExecFirstLineDeclare`
(exec `DECLARE` on line 1 before the schema), `ExecAfterSchemaDeclare` (same after the
schema), `ExecHiddenRoutine` (a `CREATE FUNCTION` hidden in a block form, called from
Main), `ExecGarbage` (non-ESQL inside the markers), `NestedBlockComment`
(`/* a /* b */ code */`), `ExecInExpression` (`1 /*!{ + 41 }!*/`).

Round 2: `ExecLineContinued` (`--!{ SET x =` / next line `'v';`), `ExecSchemaLine2`,
`ExecSchemaLine2Blank`, `ExecNoSchemaFirstLine`, `ExecNoSchemaLine2` (placement rule),
`ToolkitCanaryGarbage` (the garbage in the open - the Toolkit must reject this or its
verdict on `ExecGarbage` means nothing).

Round 3: `ExecAtLine` (`--@!{ code`), `ExecAtBanner` (the DatabaseEvent generator's
`--@!{ *** ... ***` / code / `--@!} *** ... ***` shape), `ExecAtCloser` (`--@!} code`).

## Scars

- `mqsicreateworkdir` is a batch file: `call` it or the calling script ends there.
- `%ERRORLEVEL%` inside a `for` body is expanded once at parse time; the per-app verdict
  is the BIP message in the log, never the echoed exit code.
- `ibmint package` and `ibmint deploy --output-work-directory` do not parse ESQL at all;
  the first parser to see the file is the integration server at start.
- `chcp 1252` before `IntegrationServer` or every flow fails at startup with `BIP2132E`.
- Run mqsicreatebar per app in its own `-data` workspace; it checks the whole workspace
  for error markers and refuses the BAR on any of them, so one bad app poisons a shared
  workspace.
