# WebScrapeProxy - Testing

## What's under test
REST-triggered web-scraping flow. `POST /scrape` with `{ jobNumber, topicReference }`
submits a form-encoded search to a site (default recc.org.uk member search, where
the URL does not change on submit), scrapes the FIRST results-table row with ESQL
string operations, and returns it as JSON. ESQL-only - no Java/jsoup.

Validated to **Level 3 (build + deploy + run)** against a standalone server, with a
live POST to recc.org.uk.

## Components
- ACE 13.0.7.2 standalone integration server `WSP_IS`
- Application `WebScrapeProxy` (3 ESQL modules + msgflow); no sibling Java project
- Ports: HTTP 7843, admin REST 7643 (7800 was already in use)

## Build + deploy (exact commands run)
```bat
"C:\Program Files\IBM\ACE\13.0.7.2\server\bin\mqsiprofile.cmd"
ibmint package --input-path <workspace>\WebScrapeProxy --output-bar-file WebScrapeProxy.bar
mqsicreateworkdir work_dir
ibmint deploy --input-bar-file WebScrapeProxy.bar --output-work-directory work_dir
chcp 1252
IntegrationServer --work-dir work_dir --name WSP_IS --admin-rest-api 7643 --http-port-number 7843 --console-log
```

## Test scenario (S2 - full pipeline, live)
```bash
curl -X POST http://localhost:7843/scrape \
  -H "Content-Type: application/json" \
  -d '{"jobNumber":"J-2020","topicReference":"2020 Solar PV Ltd"}'
```
Expected (PASS, observed 2026-06-29):
```json
{ "jobNumber":"J-2020", "status":"OK", "found":true,
  "member":{ "name":"2020 Solar PV Ltd", "postcode":"WR8 9LW",
             "link":"https://www.recc.org.uk/scheme/members/00043091-2020-solar-pv-ltd" } }
```

## What to watch for
- `found:false` with HTTP 200 = the POST reached the site but no member matched
  (or the first row had no anchor). If you instead get the form page back, the
  request went out as GET - check the WSRequest node `httpMethod="POST"`.
- On outbound DNS/TLS/4xx-5xx: JSON error `{ status:"ERROR", errorCode, errorText }`, HTTP 502.

## Cleanup
- Stop the server: `taskkill /PID <IntegrationServer pid> /T /F`.
- Work dir + BAR live under the scratchpad build dir.

## Things this test cannot prove
- Robustness of the ESQL first-row extraction if recc changes its HTML markup
  (the dropped jsoup path was the robust alternative).
- Multi-row / pagination handling (only the first row is returned, by design).
- Behaviour behind a corporate proxy / DataPower (not configured).
- Searches needing authentication or a session cookie.
