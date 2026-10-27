# XmlToJsonArrayLoop - repeating XML elements to a JSON array with a REFERENCE walk

Checks the repeating-element pattern used by the ace-flow-builder skill: create the target
with `IDENTITY (JSON.Array)`, walk the input with `DECLARE ... REFERENCE TO ...Item[1]`,
`WHILE LASTMOVE`, `MOVE ... NEXTSIBLING NAME 'Item'`, and build each member with
`CREATE LASTCHILD ... AS outRef NAME 'Item'`. `?mode=fv` copies leaf values with
`FIELDVALUE()`; `?mode=bare` copies with a bare field reference, to compare the two.

Run on 2026-10-26, ACE 13.0.9.0 (Windows), stand-alone integration server.

## Run

Same steps as `HttpReplyStatusCode/TESTING.md`, with `--project XmlToJsonArrayLoop`, then
`curl -i -X POST -H "Content-Type: application/xml" --data-binary @<file> "http://localhost:<port>/xmljson?mode=<fv|bare>"`.

## Results

| Input | `mode=fv` | `mode=bare` |
|---|---|---|
| two plain Items | `{"items":[{"name":"a","value":"1"},{"name":"b","value":"2"}]}` | same |
| `<Name lang="en">a</Name>` | `{"items":[{"name":"a","value":"1"}]}` | same - the attribute does not reach JSON |
| `<Name>a<Sub>x</Sub></Name>` (mixed) | `{"items":[{"value":"1"}]}` - name dropped | `{"items":[{"name":{"Sub":"x"},"value":"1"}]}` |
| one Item | `{"items":[{"name":"only","value":"9"}]}` - still an array | - |
| `<Items/>` | `{"items":[]}` | - |

## What this shows

- The loop and the explicit `JSON.Array` give a JSON array for 0, 1 and n items.
- For a JSON target, `FIELDVALUE()` and a bare copy behave the same on plain and attributed
  leaf fields. On mixed content `FIELDVALUE()` returns NULL and the field disappears. See
  `FieldValueTreeCopy` for the XML-target case, where the difference matters.
