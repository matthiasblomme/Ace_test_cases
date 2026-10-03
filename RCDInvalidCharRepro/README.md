# RCDInvalidCharRepro

A minimal ACE v13 application that reproduces, and explains, a production incident:
a COPRAR EDIFACT message carried a control character in a goods description, the
character survived mapping into an outbound XML document, the outbound flow's
`ResetContentDescriptor` node was configured with **Validate = Content and Value**
and raised no objection, and the downstream flow then failed on the same document
with:

```
errorNumber=5004;errorText=An XML parsing error has occurred while parsing the XML document;
1502;2;1;219233;An invalid XML character (Unicode: 0x1a) was found in the element content
of the document.
```

The question this project answers: **does an RCD node stop checking for invalid
characters once the message already has the desired type, and only validate the XML
structure against the schema?**

## Answer

No. On the production shape of this flow the RCD was validating nothing at all. The
general rule, after driving every configuration:

**An RCD validates exactly what it causes to be parsed, at the node where the parse
actually happens.**

Every row below is an RCD with `validateMaster="contentAndValue"` and
`validateFailureAction="exception"`, followed by a non-validating reply, fed a
document carrying an undeclared element:

| RCD configuration | What parses | What validates | Paths |
|---|---|---|---|
| Reset `XMLNSC` to `XMLNSC` (tree already in that domain) | nothing | **nothing**: 200, bogus element on the wire | F |
| Reset `BLOB` to `XMLNSC`, Parse Timing = **Complete** or **Immediate** | the whole message, at the RCD | the whole message, at the RCD: 500 BIP5025 | G, K |
| Reset `BLOB` to `XMLNSC`, Parse Timing = **On Demand** (the default) | only what downstream references force, at the node referencing it | **exactly what gets parsed, where it gets parsed**: a referenced violation throws (J), an unreferenced one passes (H, I) | H, I, J |

Path G's and K's faults are attributed to the RCD itself:

```
BIP2230E: Error detected whilst processing a message in node
          'RCDRepro.RCD BLOB->XMLNSC validate C+V'.
          ImbResetContentDescNode::evaluate: ComIbmResetContentDescriptorNode
BIP5025E: XML schema validation error 'cvc-complex-type.2.4.d: ... Invalid content
          was found starting with element "ord:Bogus".'
```

The third row is the subtle one. On Demand defers the parse, and the RCD's validation
options **travel with the deferred parse**: each element is validated at the moment
the parse reaches it, at whatever node forces it. Path I reads `Description`, which
sits before the undeclared `Bogus` in the document, so the parse stops short of the
violation and the request returns 200. Path J reads `Bogus` itself and throws BIP5025
at the Compute doing the reading. Content the flow never references is never parsed,
so it is never validated, and it reaches the output rebuilt from the original bit
stream.

So the answer to the original question is no, the RCD is not skipping character checks
while still doing structural ones. On the production shape of this flow it was doing
neither, because it never parsed anything.

### Why

Validation in ACE is not a free-standing check. IBM's wording (ACE v13, *Validating
messages*):

> Message validation involves navigating a message tree, and checking the validity of
> the tree. Message validation is an extension of tree creation when the input message
> is parsed, and of bit stream creation when the output message is written.

Validation is an **extension of parsing and of writing**. It has to attach to one of
those two events. Path G has a parse to extend, so validation runs there. Path F does
not: the node re-stamps the message properties and propagates the validation options
onto the tree, with no parse and no write to hook into, so nothing is checked.

The setting is not thrown away in that case. It is **inherited** by the next node that
does serialize. That is the whole difference between the path pairs:

- Paths A and B leave the reply node's `Validate` unset, so the reply inherits
  `contentAndValue` from the tree and validates on write. Both throw, **at the reply
  node**.
- Paths E and F set the reply to `validateMaster="none"`, which overrides the
  inherited setting. Nothing validates anywhere, and both return 200.

So an RCD that does not reparse is doing nothing more than setting a flag for somebody
downstream. If the node that eventually writes the bit stream has validation off, the
flag is discarded and no validation ever happens.

The practical rule: reading `Validate = Content and Value` on an RCD property page
tells you nothing on its own.

1. If the RCD does not change the domain, nothing is parsed and nothing is checked at
   or after the node. The setting is only a flag stamped on the tree for whatever
   serializes next, and a downstream node with validation off discards it.
2. If it does change the domain, Parse Timing decides where and how much. Complete or
   Immediate validate the whole message at the RCD. The default, On Demand, validates
   lazily: each element is checked when a downstream reference forces it to be parsed,
   and elements nothing references are never checked at all.

The On Demand case is the treacherous one in production: violations in fields the flow
actually maps will throw somewhere downstream, attributed to whatever node touched
them, so validation looks alive, while anything the flow passes through untouched is
never validated.

### Output validation does catch 0x1A

This is the part that inverts the obvious conclusion. When output validation actually
runs, it **does** reject the invalid character. Path A returns HTTP 500 with:

```
BIP2230E: Error detected whilst processing a message in node 'RCDRepro.build reply'.
BIP5010E: XML Writing Errors have occurred.
BIP5009E: XML Parsing Errors have occurred.
BIP5004E: An XML parsing error 'An invalid XML character (Unicode: 0x1a) was found in
          the element content of the document.' occurred on line 1 column 92
```

Read the chain inwards: a **writing** error wrapping a **parsing** error. Validating
on output is implemented by serializing the tree and running the resulting bit stream
back through the validating parser. That re-parse applies the full XML 1.0 grammar,
including the `Char` production that forbids `0x1A`, and it throws the same BIP5004
the downstream flow saw.

The consequence for the incident: had validation been live on the node that actually
wrote the outbound document, ACE would have caught this at the source, with the same
error code, before the message ever reached GTOS. It was not caught because the only
node configured to validate was an RCD, and an RCD in this shape validates nothing.

### The asymmetry underneath

With validation off, the XMLNSC **serializer is more permissive than the XMLNSC
parser**. Path D writes the tree with no validation anywhere and returns HTTP 200
with the raw byte on the wire:

```
41 42 43 1a 44 45 46      ABC <SUB> DEF
```

The writer escapes what would otherwise be markup (`<` to `&lt;`, `&` to `&amp;`) but
does not reject codepoints outside the XML `Char` production. So ACE will happily
produce a document that ACE itself cannot read back. Path C proves the other half:
feeding that same document to a parsing input node throws BIP5004 at
`RCDRepro.parse in`.

Two ways to close it in a flow shaped like this one:
1. Turn validation on at the node that **writes** the bit stream, not at a
   non-reparsing RCD.
2. Sanitise the character data before it reaches the writer (strip or replace
   anything outside `#x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD]`).

### How to validate mid-flow, measured

The follow-up question ("we receive XML, transform it, and want to validate before
sending"): four mechanisms tested, each against both the `0x1A` tree and the
undeclared-element tree, always with a non-validating reply so the throw localises.

| Mechanism | `0x1A` | Undeclared element | Where it throws |
|---|---|---|---|
| **Validate node**, C+V, exception | **500 BIP5004** | 500 BIP5026 | the Validate node itself |
| **RCD to BLOB, RCD back to XMLNSC** (C+V, Parse Timing Complete) | 500 BIP5004 | 500 BIP5025 | the second RCD |
| **ESQL `ASBITSTREAM`** with `BITOR(RootBitStream, ValidateContentAndValue, ValidateException)` | 500 BIP5004 | 500 BIP5026 | the Compute running it |
| **JavaCompute `MbElement.toBitstream`** with `VALIDATE_CONTENT_AND_VALUE \| VALIDATE_EXCEPTION` | 500 BIP5004 | 500 BIP5026 | the JavaCompute node |

The Validate node result (paths V1/V2) disproves the plausible "it walks the tree so
it cannot see characters" reasoning: its error chain contains **BIP5010E, XML Writing
Errors**, so it serialises under the covers, which is exactly why it catches the
character. Same fingerprint for ASBITSTREAM (BIP5010E, write side), while the
double-RCD chain shows BIP5009E (parse side). Write-validation schema errors surface
as BIP5026, parse-validation ones as BIP5025.

Practical ranking for the production case: validation on the output node is free of
extra nodes (the serialisation happens anyway); the Validate node is the mid-flow
option with match/failure terminals for routing to an error queue; the double-RCD and
ASBITSTREAM variants do the same work by hand and add nothing unless you need the bit
stream anyway.

### On "the DEL character"

The incident report describes a **DEL** character. The error reports **`0x1A`**, which
is SUB, not DEL (`0x7F`). This matters, because `0x7F` is a legal XML 1.0 character
and would never have produced BIP5004. Something between the EDIFACT and the XML
turned the character into `0x1A`.

The likely cause (inference, not verified against the actual message data): `0x1A` is
the conventional substitution character emitted by codepage conversion when a source
byte has no mapping in the target codepage. An EBCDIC to ASCII/UTF-8 step that could
not map the original byte would produce exactly this. If so, the corrupt character is
introduced by a conversion in the chain, not carried verbatim from the partner, and
asking them to resend fixes this one message without addressing the conversion path.
Worth confirming against the raw inbound bytes before closing the incident.

## The paths and what they actually returned

Measured on ACE 13.0.8.1, standalone integration server, via
`testing/standalone-server/run-test-with-ace-env.bat`. Raw output is under
`testing/standalone-server/results/`.

| Path | URL | Setup | Result | Evidence |
|---|---|---|---|---|
| **A** | `/build` | Tree with `0x1A` to RCD (C+V, exception) to reply **inheriting** validation | **500** | `BIP5010E` to `BIP5009E` to `BIP5004E` at `build reply`. Output validation writes then re-parses, and the re-parse rejects the character. |
| **B** | `/badschema` | Tree with undeclared `Bogus` element, same RCD, reply inherits | **500** | `BIP5025E cvc-complex-type.2.4.d` at `badschema reply`. Schema violation caught, again at the reply, **not** at the RCD. |
| **C** | `/parse` | Parse the dirty document, Parse Timing = Complete | **500** | `BIP5004E` at `parse in`. The downstream production failure, reproduced. |
| **D** | `/noval` | Tree with `0x1A`, no RCD, no validation anywhere | **200** | Body contains raw `41 42 43 1a 44 45 46`. The writer emits the invalid character. |
| **E** | `/roundtrip` | Parsed+validated tree, inject `0x1A`, RCD (C+V, exception), reply `validate=none` | **200** | Body contains raw `0x1A`. **The production scenario.** RCD with full validation raises nothing. |
| **F** | `/roundtripbad` | Same, but inject an undeclared `Bogus` element | **200** | Body contains `<ord:Bogus>`. The RCD does not catch even a structural violation, so it is not validating here at all. |
| **G** | `/reparse` | BLOB input, RCD resets **BLOB to XMLNSC** with Parse Timing = **Complete**, reply `validate=none` | **500** | `BIP5025E` attributed to the **RCD node itself** (`ImbResetContentDescNode::evaluate`). The same settings that did nothing in F throw here. |
| **H** | `/reparseondemand` | Same as G but Parse Timing left on the default **On Demand**, nothing touches the tree | **200** | Bogus element on the wire. |
| **I** | `/reparsetouched` | Same as H, plus a Compute that reads `Description`, which sits **before** `Bogus`, forcing a partial deferred parse | **200** | Bogus element on the wire. The parse stopped short of the violation; everything it did parse validated clean. |
| **H2** | `/reparseondemand` | H's route fed the **`0x1A`** document | **200** | Raw `0x1A` still in the response. Proves the parse never ran on H's route. |
| **I2** | `/reparsetouched` | I's route fed the **`0x1A`** document | **500** | `BIP5004E` at `touch tree (forces parse)`. Proves the parse *did* run on I's route, at the Compute. |
| **J** | `/reparsereadbad` | Same as H, but the Compute reads the undeclared `Bogus` element itself | **500** | `BIP5025E` at `read bogus element`, the Compute. The deferred parse reached the violation and the RCD's validation options were live on it. |
| **K** | `/reparseimmediate` | As G but Parse Timing = **Immediate**, fed the undeclared element | **500** | `BIP5025E` at the RCD itself. Immediate behaves like Complete. |
| **K2** | `/reparseimmediate` | K's route fed the **`0x1A`** document | **500** | `BIP5004E` at the RCD. Proves Immediate genuinely parsed at the node, so K's result is not an attribute that silently failed to apply. |
| **V1** | `/validatenode` | `0x1A` tree into a **Validate node** (C+V, exception), reply `validate=none` | **500** | `BIP5010E` + `BIP5004E` at the Validate node. It serialises internally, so it sees characters. |
| **V2** | `/validatenodebad` | Undeclared element into the same Validate node | **500** | `BIP5010E` + `BIP5026E` at the Validate node. Control: schema errors caught too. |
| **W** | `/rcdblobroundtrip` | `0x1A` tree, RCD to BLOB, RCD back to XMLNSC (C+V, Complete), reply `validate=none` | **500** | `BIP5009E` + `BIP5004E` at the second RCD. Mid-flow serialize+reparse catches the character. |
| **W2** | `/rcdblobroundtripbad` | Same chain, undeclared element | **500** | `BIP5009E` + `BIP5025E` at the second RCD. Control. |
| **X** | `/asbitstream` | `0x1A` tree, Compute runs `ASBITSTREAM` with validate options | **500** | `BIP5010E` + `BIP5004E` at that Compute. |
| **X2** | `/asbitstreambad` | Same, undeclared element | **500** | `BIP5010E` + `BIP5026E` at that Compute. Control. |
| **Y** | `/jcn` | `0x1A` tree, JavaCompute runs `toBitstream` with validate options | **500** | `BIP5010E` + `BIP5004E` at the JCN. The Java API twin of X. |
| **Y2** | `/jcnbad` | Same, undeclared element | **500** | `BIP5010E` + `BIP5026E` at the JCN. Control. |

How to read the matrix:

- **F and G together are the first load-bearing pair.** F alone only shows that this
  RCD did not validate, which is consistent with both "the property does nothing" and
  "the property needs a parse". G settles it: identical node type, identical
  validation settings, and it throws as soon as the node actually reparses.
- **H2 and I2 are what make H and I readable at all.** Both H and I return 200, and a
  200 cannot distinguish "parsed, but validation was skipped" from "never parsed".
  Feeding the same two routes a document that is not well-formed forces the
  difference into the status code, because a parse must reject `0x1A` whatever the
  validation settings are. H2 passes (no parse), I2 throws (parse happened). Only then
  does I's 200 mean anything at all.
- **J is what makes I's 200 attributable.** I's parse stops at `Description`, before
  the violation, so I alone cannot distinguish "validation options dropped on the
  deferred parse" from "validation is lazy and never reached the violation". J forces
  the parse through the violating element and throws at the Compute, which pins the
  mechanism: on-demand validation is lazy, not absent.
- Without F, E is ambiguous: "the RCD validated and had no objection to a control
  character" and "the RCD did not validate" both predict a 200. F distinguishes them.

`SET OutputRoot = InputRoot` is **not** enough to force an on-demand parse. A
whole-tree copy can be satisfied straight from the bit stream, and the reply then
echoes the input back byte for byte, indentation and all, which looks exactly like a
successful parse in a status code. Reading an individual field is what triggers it.
That is the difference between `Manifest.esql` and `ForceParse.esql`.
- **A versus E** is the cleanest pair. Identical tree, identical RCD, identical
  `0x1A`. The only difference is whether the reply node validates. A throws, E does
  not. That localises validation to the serializing node.
- **A versus B** shows the two error classes at the same node: BIP5004 (parse-level,
  character) and BIP5025 (schema-level, structure). Both surface at the reply.
- **C and D** are the two halves of the round trip: D produces the document nothing
  can read, C fails to read it.

## Files

```
RCDInvalidCharRepro/
  Order.xsd                              minimal model, Description is a bare xs:string
  application.descriptor
  .project
  com/example/repro/
    RCDRepro.msgflow                     paths A to X2
    BuildOrder.esql                      A + D: build schema-valid tree with 0x1A
    BuildSchemaViolation.esql            B: build tree with undeclared element
    Manifest.esql                        C: whole-tree copy on an already parsed message
    ForceParse.esql                      I: reads Description, forcing a partial deferred parse
    ReadBogus.esql                       J: reads the undeclared element, so the parse must hit the violation
    ValidateViaAsbitstream.esql          X: mid-flow write-validation via ASBITSTREAM
RCDInvalidCharReproJava/               sibling Java project (referenced by the app .project)
  com/example/repro/
    ValidateViaToBitstream.java        Y: the JavaCompute twin, toBitstream with validate options
    InjectInvalidChar.esql               E: inject 0x1A into an already-typed tree
    InjectSchemaViolation.esql           F: inject undeclared element into a typed tree
  testing/
    test-resources/
      make-test-data.ps1                 generates the fixtures byte-exactly
      input/clean-order.xml              schema-valid, no control characters
      input/dirty-order.xml              same document with a literal 0x1A (generated)
      input/bad-order.xml                well-formed, carries an undeclared element
    standalone-server/
      run-test-with-ace-env.bat          wrapper: sources mqsiprofile, calls the harness
      deploy_and_test.bat                the harness
      stop_server.bat
      results/                           captured bodies, status codes, console log
```

`Order.xsd` is deliberately anonymous and minimal. `Description` is a plain
`xs:string` with no facets. ESQL has no codepoint literal, so the character is
produced with `CAST(X'1A' AS CHARACTER CCSID 1208)`.

`dirty-order.xml` is generated rather than committed: the `0x1A` is invisible in an
editor and most tooling strips or re-encodes it on save.

## Running it

```
testing\standalone-server\run-test-with-ace-env.bat -fresh
```

The wrapper sources `mqsiprofile.cmd`, then the harness stages the project, creates a
work directory under `C:\temp\rcdrepro`, deploys, starts a server, waits for
`BIP1991I`, verifies the listener, drives all six paths and captures everything to
`results/`. Override `ACE_VERSION`, `HTTP_PORT`, `ADMIN_PORT`, `BASE_DIR` or
`SERVER_NAME` as environment variables. `-fresh` rebuilds the work directory;
without it the existing one is reused and redeployed.

Defaults are ACE 13.0.8.1 on port 7801, deliberately not 7800, so the harness does
not collide with a server already running on the default port.

Harness notes worth keeping:
- `--compile-maps-and-schemas` on `ibmint deploy` is load-bearing. Without it
  `Order.xsd` is never compiled and the validation this repro is about has no model
  to work against.
- `mqsicreateworkdir` is a `.cmd` script while `ibmint` and `IntegrationServer` are
  real `.exe` files. Invoking the `.cmd` from a `.bat` without `call` transfers
  control and never returns: the harness ends there having printed "Successful
  command completion", which reads exactly like a pass.

## What this project does not prove

- It does not identify where in the real chain the character was introduced. See the
  DEL versus SUB note; that needs the raw inbound EDIFACT bytes.
- It does not test a real domain change where the document is **valid**, so it does
  not measure what a passing G looks like. Only the failing case was needed here.
- On Demand's lazy validation was proven with ESQL field references forcing the
  parse. Other ways of touching the tree (a Mapping node, a serializing output with
  Validate = Inherit consuming the unparsed remainder) were not separately measured.
- It uses HTTP rather than MQ. The parse and write layers are the same, but MQ adds
  `MQMD.CodedCharSetId` as a further way to break character handling, which is a
  separate failure mode.
- It says nothing about DFDL or MRM, which validate to the same XML Schema 1.0 level
  but have their own writer behaviour.
