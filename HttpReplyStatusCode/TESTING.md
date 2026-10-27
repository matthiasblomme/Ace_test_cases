# HttpReplyStatusCode - which ways of setting an HTTP reply status does HTTPReply honour?

Three documented ways to make an HTTP Reply answer something other than 200, tried side by side,
in a Compute with `computeMode="destinationAndMessage"` (`StatusFlow`, `/status`) and with the
default `message` mode (`StatusFlowMessageMode`, `/status2`). Both flows share one ESQL module;
`?v=` picks the method.

Run on 2026-10-26, ACE 13.0.9.0 (Windows), stand-alone integration server.

## Variants

| `?v=` | What the ESQL sets |
|---|---|
| `none` | nothing (control) |
| `rsc` | `OutputLocalEnvironment.Destination.HTTP.ReplyStatusCode = 202` |
| `replyhdr` | `OutputRoot.HTTPReplyHeader."X-Original-HTTP-Status-Code" = 202` |
| `resphdr` | `OutputRoot.HTTPResponseHeader."X-Original-HTTP-Status-Code" = 202` |

## Run

1. `mqsicreateworkdir <wd>`; `<wd>/overrides/server.conf.yaml` with free ports
   (`RestAdminListener: port`, `ResourceManagers: HTTPConnector: ListenerPort`).
2. `ibmint deploy --input-path <repo> --output-work-directory <wd> --project HttpReplyStatusCode`.
3. Start `IntegrationServer --work-dir <wd>` in its own terminal (or detached with output to a
   log); wait for `BIP1991I`.
4. `curl -i "http://localhost:<port>/status?v=<variant>"` and `.../status2?v=<variant>`.

## Results

| Variant | `/status` (destinationAndMessage) | `/status2` (message) |
|---|---|---|
| none | 200 | 200 |
| rsc | 202 | 200 - the LocalEnvironment write is discarded in message mode |
| replyhdr | 202 | 202 |
| resphdr | 202 | 202 |

## What this shows, and what it cannot

- `ReplyStatusCode` works only when the Compute's mode includes the destination (and
  `OutputLocalEnvironment` is copied from the input, which carries the reply identifier).
- On 13.0.9.0 both header forms are honoured as well, in either compute mode.
- A Bob-built flow on 13.0.8.1 (2026-10-12) returned 200 with a status set in
  `HTTPResponseHeader`. That was not reproduced here: the 13.0.8.1 install on this box is
  partial, so a version difference versus a flow-shape difference stays open.
