# RowOverrideTest - Flow Builder State

**Last updated:** 2026-09-03
**Build mode:** Iterative
**Iteration:** 1

## Confirmed
- Flow name: RowOverrideTest
- Pattern: SimpleHTTPResponse (HTTPInput -> Compute -> HTTPReply, no MQ, no DB, no external calls)
- Project location: d:/GIT/Ace_test_cases/RowOverrideTest
- Input: HTTP POST (GET also accepted, not restricted) on /rowoverridetest - request body ignored, all test data hardcoded
- Output: JSON, Content-Type application/json, HTTP 200
- Broker schema: com.mbl.test (matches repo convention, e.g. UnitTestIgnoreFields)
- Compute module: RowOverrideTest_BuildResponse, computeExpression esql://routine/com.mbl.test#RowOverrideTest_BuildResponse.Main
- Purpose: empirically test ROW assignment semantics via before/after snapshots in the JSON response. v0.2 runs three cases from the same starting tree (`parent.child = ROW(attr1..attr5)`): (1) constructor onto the parent, (2) constructor onto the same child, (3) declared ROW variable onto the parent

## Assumed (please confirm or override)
- HTTP response status explicitly set to 200 (brief did not specify a status code; 200 fits a synchronous diagnostic response better than the SimpleHTTPResponse example's 202)
- HTTPInput messageDomainProperty set to JSON even though the request body is never read, matching the SimpleHTTPResponse convention of declaring a domain regardless of payload use
- No [CONFIGURE: ...] markers - the brief confirmed there is nothing environment-specific in this flow

## Open questions
- None

## Iteration history
- v0.1 (2026-09-03): initial generate from the full brief - single case (constructor onto parent)
- v0.2 (2026-09-03): user asked for three side-by-side cases; Matthias's own Toolkit edit (constructor onto same child) folded in as case 2, ROW-variable case added as case 3; ESQL edited inline in the main conversation (single-file change, msgflow untouched)

## Deviations from customer profile (recorded per the brief)
- No `_HandleException.esql` and no catch/failure wiring - the brief explicitly wants no error handling for this throwaway diagnostic flow
- No `LoggingLibrary`/`ActivityLogLib` wiring from customer_profile.md - out of scope for a throwaway diagnostic per the brief

## Validation Results
<!-- Phase B5 appends one entry per validation run (level 2 or level 3). -->
<!-- Credentials are NEVER recorded here - only the vault commands invoked, never the values. -->

### 2026-09-03 - Level 3 (build + deploy + run) - PASS
- Standalone server RowOverrideTestSrv, ACE 13.0.8.1, scratchpad work dir RowOverrideTest_sis, admin 7602, HTTP 7800.
- S1 PASS (POST and GET): HTTP 200 application/json; afterSecondAssign.parent = { child{attr1..attr5}, attr6, attr7 }. Conclusion: `SET <parent> = ROW(...)` after `SET <parent>.<child> = ROW(...)` MERGES - the child survives, the ROW fields are appended beside it. Re-verified first-hand from the main session after the incident below.
- S2/S3 not applicable: no downstream, error handling omitted by spec.
- Incident (attribution corrected later the same day): the .esql changed on disk AFTER packaging and deploying (second SET target became work.parent.child). Initially blamed on the validation subagent; Matthias later confirmed he was editing the flow in Toolkit in parallel - the edit and the RowOverrideTest_inputMessage.xml Flow Exerciser artifact were his. The BAR-extraction check still did its job: it proved what the deployed server actually ran. The source was reverted once before the parallel work was understood; Matthias redid his edit and it became case 2 of v0.2.
- Full evidence and runbook: TESTING.md. Server stopped, ports released; work dir kept for re-runs.

### 2026-09-03 - Level 3 rerun on v0.2 (three cases) - PASS
- Same standalone server setup (RowOverrideTestSrv, 13.0.8.1, HTTP 7800 / admin 7602), redeployed via ibmint package + deploy --output-work-directory, run first-hand from the main conversation.
- S1 PASS: HTTP 200 application/json, all three cases in one body. Case 1 constructor-onto-parent: MERGE (child survives, attr6/attr7 appended). Case 2 constructor-onto-same-child: MERGE (all seven attrs inside child). Case 3 ROW-variable-onto-parent: REPLACE (child wiped, only attr6/attr7 remain).
- Verbatim body and per-case conclusions: TESTING.md. Server stopped after capture.
