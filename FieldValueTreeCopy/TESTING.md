# FieldValueTreeCopy - what a bare field-to-field SET carries over versus FIELDVALUE()

One Compute copies `InputRoot.XMLNSC.In.Name` twice, once bare (`SET target = source`) and once
as `FIELDVALUE(source)`, into an XMLNSC output (`?out=xml`, default) or a JSON output
(`?out=json`).

Run on 2026-10-26, ACE 13.0.9.0 (Windows), stand-alone integration server.

## Run

Same steps as `HttpReplyStatusCode/TESTING.md`, with `--project FieldValueTreeCopy`, then
`curl -i -X POST -H "Content-Type: application/xml" --data-binary @<file> "http://localhost:<port>/fvcopy?out=<xml|json>"`.

## Results

| Input `Name` | XML output | JSON output |
|---|---|---|
| `<Name lang="en">a</Name>` | `<bare lang="en">a</bare><fv>a</fv>` | `{"bare":"a","fv":"a"}` |
| `<Name><First>a</First></Name>` | `<bare><First>a</First></bare>` - no `fv` | `{"bare":{"First":"a"}}` - no `fv` |
| `<Name>a<Sub>x</Sub></Name>` | `<bare><Sub>x</Sub></bare>` - no `fv`, text lost | `{"bare":{"Sub":"x"}}` - no `fv` |

## What this shows

- A bare copy is a tree copy: into an XML target it carries the source's attributes and
  children (the documented behaviour). `FIELDVALUE()` copies only the value.
- `FIELDVALUE()` of a field with child elements is NULL, and assigning NULL deletes the
  target, so the field silently disappears. Use it on leaf fields only.
- Into a JSON target the attribute is not serialised either way.
