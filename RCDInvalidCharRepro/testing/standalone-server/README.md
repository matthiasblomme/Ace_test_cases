# Standalone server harness

Deploys `RCDInvalidCharRepro` to a private standalone integration server and drives
all twenty-two HTTP requests, capturing status codes, response bodies and BIP codes.

## Prerequisites

- IBM ACE 13 installed (default `C:\Program Files\IBM\ACE\13.0.8.1`).
- `curl.exe` (ships with Windows 10+).
- Nothing else. No SupportPac, no third-party jars, no shared-classes, no policy
  projects, no queue manager.

## Run

```
run-test-with-ace-env.bat          reuse the existing work directory, redeploy
run-test-with-ace-env.bat -fresh   delete and rebuild the work directory
stop_server.bat                    stop the server this harness started
```

Always go through `run-test-with-ace-env.bat`. It sources `mqsiprofile.cmd`; without
that, `ibmint`, `mqsicreateworkdir` and `IntegrationServer` are not on PATH.

## Configuration

Every value is an overridable environment variable.

| Variable | Default | Notes |
|---|---|---|
| `ACE_VERSION` | `13.0.8.1` | resolves `C:\Program Files\IBM\ACE\<version>` |
| `SERVER_NAME` | `RCD_TEST` | names the work directory, which is how `stop_server.bat` finds the process |
| `BASE_DIR` | `C:\temp\rcdrepro` | holds the staged sources, work directory and console log |
| `HTTP_PORT` | `7801` | deliberately not 7800, to avoid colliding with an existing server |
| `ADMIN_PORT` | `7601` | |

The work directory lives outside the project tree on purpose, so the Toolkit does not
pick it up as a project.

## Lifecycle

1. Dependencies: none.
2. Provision: stop any previous server, stage the app project AND the sibling
   `RCDInvalidCharReproJava` project to `%BASE_DIR%\Sources`, `mqsicreateworkdir`,
   clear a stale `config\.lock`.
3. shared-classes: none.
4. Deploy: `ibmint deploy --input-path <stage> --output-work-directory <wd>
   --project RCDInvalidCharRepro --project RCDInvalidCharReproJava
   --compile-maps-and-schemas --java-version 17`.
5. Start: `IntegrationServer --work-dir <wd> --http-port-number <port>`, poll the
   console log for `BIP1991I`.
6. Verify the listener is `LISTENING` before sending traffic.
7. Drive the twenty-two requests with curl.
8. Copy the console log and extract `BIP*E`/`BIP*W` lines.
9. Report. The server is left running for inspection.

## Outputs

`results/` holds, per path, `<label>.body` and `<label>.code`, plus
`server.console.log` and `bip-errors.txt`. It is overwritten on every run.

## Expected results

| Path | Expected |
|---|---|
| `A_build` | 500, BIP5004 at `build reply` |
| `B_badschema` | 500, BIP5025 at `badschema reply` |
| `C_parse` | 500, BIP5004 at `parse in` |
| `D_noval` | 200, body contains raw byte `0x1A` |
| `E_roundtrip` | 200, body contains raw byte `0x1A` |
| `F_roundtripbad` | 200, body contains `<ord:Bogus>` |
| `G_reparse` | 500, BIP5025 attributed to the **RCD node itself** |
| `H_reparse_od` | 200, body contains `<ord:Bogus>` |
| `I_reparse_tch` | 200, body contains `<ord:Bogus>` |
| `H2_od_dirty` | 200, raw `0x1A` in the body (proves no parse ran) |
| `I2_tch_dirty` | 500, BIP5004 at `touch tree (forces parse)` (proves the parse ran) |
| `J_read_bogus` | 500, BIP5025 at `read bogus element` (deferred validation travels with the parse) |
| `K_imm_bad` | 500, BIP5025 at the RCD (Immediate validates at the node, like Complete) |
| `K2_imm_dirty` | 500, BIP5004 at the RCD (Immediate genuinely parses at the node) |
| `V1_valnode_1a` | 500, BIP5010+BIP5004 at the Validate node (it serialises internally, so it sees chars) |
| `V2_valnode_bad` | 500, BIP5010+BIP5026 at the Validate node |
| `W_blobround_1a` | 500, BIP5009+BIP5004 at the second RCD of the BLOB roundtrip |
| `W2_blobrnd_bad` | 500, BIP5009+BIP5025 at the second RCD |
| `X_asbits_1a` | 500, BIP5010+BIP5004 at the ASBITSTREAM Compute |
| `X2_asbits_bad` | 500, BIP5010+BIP5026 at the ASBITSTREAM Compute |
| `Y_jcn_1a` | 500, BIP5010+BIP5004 at the JavaCompute node (toBitstream write-validation) |
| `Y2_jcn_bad` | 500, BIP5010+BIP5026 at the JavaCompute node |

Anything else is a real change in behaviour, not a flaky test. There is no timing,
no external dependency and no shared state between paths.

## Troubleshooting

**`'ibmint' is not recognized`** - the harness was run directly instead of through
`run-test-with-ace-env.bat`.

**Harness stops after "Successful command completion" and exits 0** -
`mqsicreateworkdir` is a `.cmd` script. Calling it from a `.bat` without `call`
transfers control and never returns, so the run truncates while looking like a pass.
`ibmint` and `IntegrationServer` are `.exe` files and do not need `call`.

**Deploy reports `BIP15228E: Project cannot be found`** - the staging copy produced an
empty tree. The harness now uses robocopy with explicit `/XD` exclusions and asserts
`.project` exists afterwards; `xcopy /EXCLUDE` matched substrings and silently copied
nothing.

**Paths return 200 but the schema is clearly violated** - that is the finding, not a
bug. See the answer section in the project README.

**`BIP1991I` never appears** - read `%BASE_DIR%\%SERVER_NAME%.console.log`. On ACE
13.0.8 a `BIP2203E` at startup usually means the call-home placeholder is unreplaced
in `registry.13.0.conf.yaml`; set `CallHome:\n  enabled: false`.

**One request silently skipped while the rest pass** - check the .bat line endings.
cmd's `call :LABEL` search misjumps on LF-only batch files, and the misjump moves as
the file grows: this harness ran three clean runs, then adding paths shifted byte
offsets and `call :DRIVE B_badschema` jumped into the middle of a later line, skipped
B entirely, and even printed an early RESULTS block mid-run. Every `.bat` here must be
CRLF. Verify by counting both ending kinds per file (a boolean "contains 
" grep
passed while all three files were pure LF).

**Port already in use** - set `HTTP_PORT` and `ADMIN_PORT` to something free.

**The harness finishes but your shell does not return** - the server is left running
and inherited the console's stdout handle, so a caller that captures output (a
PowerShell pipeline, CI, an agent) waits on a pipe the server still holds. The run is
complete; check `results/` timestamps. Run `stop_server.bat` to release it, or invoke
the harness without capturing its output.

## Notes

Waits use `ping 127.0.0.1 -n <n>`, never `timeout /t`. With stdout redirected (CI, a
logged run, an agent driving it) `timeout` aborts immediately, every wait silently
becomes a no-op, and the race it guarded only bites on a slower machine.
