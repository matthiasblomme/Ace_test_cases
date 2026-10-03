# WebScrapeProxy - Flow Builder State

**Last updated:** 2026-06-29
**Build mode:** Iterative
**Iteration:** v0.5

## Confirmed
- Flow name: WebScrapeProxy
- Pattern: HTTP Request-Reply with outbound HTTP content-enrichment (web scrape)
- Project location: <workspace>\WebScrapeProxy
- Broker schema: com.integration.webscrape
- Input: inbound REST POST /scrape, JSON body { jobNumber, topicReference }
- Processing: POST a form-encoded search to a site, scrape the FIRST results-table
  row with ESQL string ops, return it as JSON.
- Output: JSON reply { jobNumber, status, found, member: { name, postcode, link } }
- Target site: recc.org.uk member search (POST /scheme/members; the URL does not
  change on submit). companyname={ref} performs a name search.
- ESQL-only: Java/jsoup path, marker-extract path, router and multi-mode were all
  stripped in v0.5 at user request. Single linear path.

## Flow (6 nodes, all ESQL)
WSInput POST /scrape
  -> BuildRequest      build form body, set RequestURL
  -> WSRequest POST    httpMethod="POST" on the node (static), BLOB response
  -> ExtractMember     BLOB->CHAR, slice first <table> row, build JSON reply
  -> WSReply
WSInput.catch + WSRequest.error/failure -> HandleException -> WSReply

## User Defined Properties
- govSearchUrl   = form action URL   (default https://www.recc.org.uk/scheme/members)
- searchPostBody = form body template, {ref} substituted with the encoded search term
  (default companyname={ref}&postcode=&region=&technology=&submitbutton=submit)

To search by postcode instead of name, set searchPostBody=postcode={ref}&companyname=&region=&technology=&submitbutton=submit.

## Assumed (please confirm or override)
- Search term encoding is minimal (spaces -> %20). Add a fuller encoder if terms
  carry &, =, /, etc.
- First-row extraction depends on recc's exact markup (<table>, href=", class="nowrap">,
  </tr>, </a>). Brittle if recc restructures its HTML - this is the cost of dropping
  jsoup. The robust alternative was the Java/jsoup DOM path (removed in v0.5).
- Reply status: 200 on success, 502 on scrape error.

## Open questions
- [ ] Does recc need credentials / a session cookie for any searches? (The simple
      single POST works without one in testing.)

## Iteration history
- v0.1 (2026-06-29): initial generate. 10-node, 3 scrape modes (raw/extract/jsoup),
  sibling Java project, 5 UDPs, shared reply builder.
- v0.2: removed proxy (direct internet).
- v0.3: retargeted at DuckDuckGo HTML; added query-param UDP + User-Agent; fixed the
  Toolkit "Namespace URI does not match location" error (nsURI/nsPrefix must carry the
  package path for a dotted-package flow). Validated to Level 2 (build+deploy).
- v0.4: added POST-form support (searchHttpMethod + searchPostBody UDPs) via a runtime
  method override. NOTE: the runtime Destination.HTTP.RequestLine.Method override did
  NOT take effect at runtime - the node's static method won, so the flow sent GET.
- v0.5: stripped to ESQL-only at user request - removed the Java/jsoup project, the
  marker-extract and raw modules, the router and the multi-mode UDPs. Fixed the method
  bug by setting httpMethod="POST" statically on the WSRequest node. Added ExtractMember
  (ESQL first-row slice). Configured for recc name search. Validated to Level 3 (live).

## Validation Results
<!-- Phase B5 appends one entry per validation run (level 2 or level 3). -->

### v0.3 (2026-06-29) - level: build + deploy
Result: PASS - clean build/deploy, server up on 7843, /scrape listening. (3-mode build.)

### v0.5 (2026-06-29) - level: build + deploy + run
Commands run:
  - ibmint package --input-path <app> --output-bar-file WebScrapeProxy.bar
  - ibmint deploy --input-bar-file WebScrapeProxy.bar --output-work-directory work_dir
  - IntegrationServer --work-dir work_dir --name WSP_IS --admin-rest-api 7643 --http-port-number 7843 --console-log
  - curl POST /scrape { jobNumber:"J-2020", topicReference:"2020 Solar PV Ltd" }
Dependencies used: ACE 13.0.7.2 standalone; live POST to https://www.recc.org.uk/scheme/members.
Result: PASS - HTTP 200, found=true, member={ name:"2020 Solar PV Ltd", postcode:"WR8 9LW",
  link:https://www.recc.org.uk/scheme/members/00043091-2020-solar-pv-ltd }. First results-table
  row returned correctly.
TESTING.md: WebScrapeProxy/TESTING.md
Notes: server left running (bg job) on 7843.
