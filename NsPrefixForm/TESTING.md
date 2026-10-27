# NsPrefixForm - does a slashed nsPrefix break a schema-qualified flow or subflow?

The Toolkit writes a flow's `nsURI` as the path (`com/example/nsp/Flow.msgflow`) and its
`nsPrefix` with underscores (`com_example_nsp_Flow.msgflow`). This app has one flow in that form
(`FlowUnderscore`, `/nsp/underscore`) and one flow plus its subflow with a slashed `nsPrefix`
(`FlowSlash` -> `SubSlash.subflow`, `/nsp/slash`).

Run on 2026-10-26, ACE 13.0.9.0 (Windows).

## Run

1. Runtime: same steps as `HttpReplyStatusCode/TESTING.md` with `--project NsPrefixForm`, then
   `curl -i http://localhost:<port>/nsp/underscore` and `.../nsp/slash`.
2. Headless Toolkit build: copy the project into an empty workspace folder and run
   `mqsicreatebar -data <ws> -b NsPrefixForm.bar -a NsPrefixForm -cleanBuild -deployAsSource`.

## Results

- Runtime: `BIP9332I` for the application; both URLs answer 200.
- Headless build: `BIP0986I Command completed successfully`, with both flows and the subflow
  added to the BAR.

## What this shows, and what it cannot

A slashed `nsPrefix` is not an error for the runtime or for a headless build. The underscore
form is still what the Toolkit writes, so generated files should use it. Opening and
re-saving the slashed files in the Toolkit editor was not tried.
