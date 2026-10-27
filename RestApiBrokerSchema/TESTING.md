# RestApiBrokerSchema - REST API with its ESQL in a broker schema

The Toolkit's REST API wizard puts operation and error-handler ESQL at the project root, in
the default schema. This is the `HelloWorld` REST API from this repo, renamed, with every
`.esql` moved to `com/example/restschema/`, given `BROKER SCHEMA com.example.restschema`, and
each subflow's `computeExpression` changed to `esql://routine/com.example.restschema#<Module>.Main`.
Subflows, `gen/`, the spec and `restapi.descriptor` stay where the wizard puts them.

Run on 2026-10-26, ACE 13.0.9.0 (Windows), stand-alone integration server.

## Run

Same steps as `HttpReplyStatusCode/TESTING.md` with `--project RestApiBrokerSchema`, then
`curl -i http://localhost:<port>/world/hello`.

## Results

- `BIP9332I: RestAPI 'RestApiBrokerSchema' has been reloaded successfully.`
- `GET /world/hello` -> `200 {"message":"Hello, Foo Bar!"}`.

## What this shows, and what it cannot

A REST API project accepts schema-qualified ESQL for its operation and handler subflows. The
POST operation and the handler paths were not exercised, and the project was not opened in
the Toolkit editor.
