# UnitTestCodeBuilt - an ACE unit test written in code, built and run without the Toolkit

A NodeSpy unit test project for the `FeedApplication` in `../UnitTestIgnoreFields/`, written
by hand in the Toolkit's project shape (no recorded `.mxml` messages): the test input is built
with `TestMessageAssembly.buildJSONMessage()`, the Compute node is spied with
`SpyObjectReference().application("FeedApplication").messageFlow("com.mbl.test.processFeed").node("Compute")`,
and the assertions check call counts, terminal propagation and output fields. It is the
shape the `ace-flow-test` skill now generates.

The project is named `FeedApplication_CodeTest` so it can sit in one Toolkit workspace next
to the Toolkit-generated `FeedApplication_Test`.

Run on 2026-10-26, ACE 13.0.9.0 (Windows).

## Run

1. Stage `UnitTestIgnoreFields/FeedApplication` and `UnitTestCodeBuilt/FeedApplication_CodeTest`
   side by side in one folder (`<src>`).
2. `mqsicreateworkdir <wd>`
3. `ibmint deploy --input-path <src> --output-work-directory <wd> --project FeedApplication`
4. `ibmint deploy --input-path <src> --output-work-directory <wd> --project FeedApplication_CodeTest`
   - this compiles the Java test classes; no Gradle needed.
5. `IntegrationServer --work-dir <wd> --test-project FeedApplication_CodeTest --start-msgflows false --admin-rest-api -1`
   - runs the tests and exits; exit code 0 = all passed.

## Results

```
FeedApplication_com_mbl_test_processFeed_Compute_TestCase_001():SUCCESSFUL
FeedApplication_com_mbl_test_processFeed_Compute_TestCase_002():SUCCESSFUL
PASSED    :2
FAILED    :0
BIP9911I: Running tests in test project 'FeedApplication_CodeTest' completed successfully.
```

Exit code 0. A copy with a deliberately wrong expected value failed (exit 1) in an earlier run
the same day, so the run does report failures.

## What this cannot show

The project was not opened in the Toolkit editor. `--stop-after-duration` is not used: in an
earlier run the same day it was reported to stop the server before any test ran (not re-checked).
