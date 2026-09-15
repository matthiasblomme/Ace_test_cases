# CredVaultDemo - Technical Review

## Overview

CredVaultDemo is a deliberately minimal IBM ACE v13.0.7.2 verification harness built to validate the claims made in a blog post about retrieving credentials from the integration server vault inside a JavaCompute node. It exposes a single HTTP endpoint (`/getcred`) that looks up a named credential through the runtime API `MbCredential.getCredential` and returns proof that the lookup succeeded - the username, the client id, and the byte lengths of the client secret and password - without ever echoing a secret value back to the caller.

This is a test harness, not a production deliverable. The review below applies the full Level 2 checklist (ESQL/flow/Java coding, common mistakes, and error handling) honestly, while noting where a finding is a conscious harness tradeoff rather than a defect.

## Architecture

### Purpose

The harness proves two things end to end: that a credential of type `userdefined` named `blogOAuthCred` is retrievable at runtime via the documented Java API, and that the runtime accessor methods (`hasUsername()`/`username()`, `hasClientId()`/`clientId()`, `hasClientSecret()`/`clientSecret()`, `hasPassword()`/`password()`) behave as the blog describes against the 13.0.7.2 `javacompute.jar`. If it stopped working the only impact would be loss of the verification harness; no business process depends on it.

### Components

The application consists of 1 main message flow:

1. **GetCredentialFlow** - HTTP request-response flow that looks up a vault credential in a JavaCompute node and replies with a JSON proof-of-retrieval document.

### Dependencies

- **CredVaultDemoJava** - referenced Java project containing the `com.acme.security.GetCredential_JavaCompute` compute node implementation. Linked via the application `.project` project reference and the JavaCompute node `javaClass` attribute.
- **Integration server vault** - the runtime credential store. The credential type `userdefined` must be listed under `Credentials.userRetrievableCredentialTypes` in `server.conf.yaml` for the lookup to return a value.
- **javacompute.jar / jplugin2.jar / IntegrationAPI.jar** - IBM-provided ACE runtime libraries referenced from the Java project `.classpath` via the `JCN_HOME` and `COMMON_CLASSES_HOME` classpath variables.

---

## Flow: GetCredentialFlow

### Purpose

Accept an HTTP request on `/getcred`, retrieve the configured vault credential in a JavaCompute node, and reply with a JSON document confirming retrieval without disclosing secret values.

### Flow Design

```mermaid
graph LR
    A["HTTP Input<br/>/getcred (BLOB)"] --> B["JavaCompute<br/>Lookup Credential"]
    B --> C["HTTP Reply"]

    style A fill:#e1f5ff
    style B fill:#e1f5ff
    style C fill:#90ee90
```

There is no error path. The JavaCompute node's Failure terminal is unconnected and there is no upstream TryCatch node, so any thrown exception propagates back to the HTTP Input node and surfaces to the caller as HTTP 500. This is a known and accepted harness tradeoff (see R-01).

### Key Components

#### 1. HTTP Input "HTTP Input" ([`GetCredentialFlow.msgflow:7`](GetCredentialFlow.msgflow))

**Configuration:**
- URLSpecifier: `/getcred`
- messageDomainProperty: `BLOB`
- Parsing: On Demand (default - no immediate/complete override set)

**Responsibilities:**
1. Receive the inbound HTTP request and start the request-response interaction.
2. Establish the parse domain for the request body.

**Error Handling:**
- The HTTP Input node is the propagation target of last resort. With no upstream error handling, exceptions from downstream nodes return to this node and are mapped to an HTTP 500 response.

#### 2. JavaCompute "Lookup Credential" ([`GetCredentialFlow.msgflow:10`](GetCredentialFlow.msgflow))

**Configuration:**
- javaClass: `com.acme.security.GetCredential_JavaCompute`

**Responsibilities:**
1. Look up the vault credential `blogOAuthCred` of type `userdefined`.
2. Build a JSON proof-of-retrieval document.
3. Propagate the assembled reply to the `out` terminal.

**Key Logic:**
```java
MbCredential credential = MbCredential.getCredential(CREDENTIAL_TYPE, CREDENTIAL_NAME);
if (credential == null) {
    throw new MbUserException(this, "evaluate()", "", "",
            "Credential '" + CREDENTIAL_NAME + "' of type '" + CREDENTIAL_TYPE
                    + "' was not found. ...", null);
}
```

**Error Handling:**
- On credential-not-found the node throws `MbUserException`.
- The `try` block catches `MbException` first (rethrow), then `RuntimeException` (rethrow), then `Exception` (wrap in `MbUserException`). This catch ordering is correct - it preserves ACE error structure.
- The node's Failure terminal is not wired (see R-01).

#### 3. HTTP Reply "HTTP Reply" ([`GetCredentialFlow.msgflow:13`](GetCredentialFlow.msgflow))

**Configuration:**
- Default configuration (no properties set on the node).

**Responsibilities:**
1. Return the JSON document assembled by the JavaCompute node to the original HTTP caller.

**Error Handling:**
- None local to this node; relies on the assembly built upstream.

---

## Configuration

### Deployment Properties

N/A - no `.properties` or `.yaml` deployment descriptor was provided with this harness, and no node properties are promoted in the flow. Runtime configuration cannot be cross-checked against node defaults. The one runtime dependency that lives outside the BAR is the `server.conf.yaml` setting `Credentials.userRetrievableCredentialTypes`, which must include `userdefined` for the lookup to return a value; this is environment configuration rather than a deployment descriptor and is documented in the Java class Javadoc.

---

## Logging Strategy

### Log Points

N/A - the harness performs no explicit logging. The only diagnostic output is the message text carried on the `MbUserException` thrown when the credential is not found, which the runtime records in the exception list and the integration server log. There is no Elasticsearch integration.

---

## Strengths

1. **Secret-safe response design**
   - The node returns only `username`, `clientId`, and the byte LENGTHS of the client secret and password - never the secret values themselves ([`GetCredential_JavaCompute.java:43`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)). For a credential-handling demo this is exactly the right default and is a deliberate security-positive choice.

2. **Thread-safe JavaCompute implementation**
   - The node holds only `static final` constants and uses method-local variables throughout `evaluate()`. There is no mutable instance or static state, so the single shared node instance is safe across parallel flow instances ([`GetCredential_JavaCompute.java:24`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)). This aligns with the ACE single-instance rule for JavaCompute nodes.

3. **Correct exception-catch ordering**
   - The `try` block catches `MbException` first and rethrows it before the generic `Exception` catch ([`GetCredential_JavaCompute.java:60`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)). This preserves the ACE-specific error structure (error number, insertion strings, exception hierarchy) instead of flattening it to a string, exactly as the Java guidelines require.

4. **Explicit, helpful failure message**
   - The not-found `MbUserException` names the credential, its type, and the `userRetrievableCredentialTypes` prerequisite ([`GetCredential_JavaCompute.java:32`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)), which makes the most likely misconfiguration self-diagnosing.

5. **Minimal, correct flow topology**
   - Three nodes, one linear path, no adjacent compute nodes, no flow loops, no unnecessary nodes, and a single output connection per node so execution order is unambiguous. The flow has none of the common message-flow mistakes around node count, adjacency, looping, or unordered multi-connections.

---

## Areas for Improvement

Findings are grouped by topic (Security -> Reliability -> Performance -> Flow Design -> Coding -> Configuration -> Observability -> Maintainability) and ordered by severity within each group.

---

### R-01 JavaCompute Failure terminal unconnected, no flow-level error handling 🟡

**Issue:** The JavaCompute node's Failure terminal is not wired and there is no TryCatch node anywhere upstream. On credential-not-found, or on any exception inside `evaluate()`, the thrown `MbUserException` propagates back to the HTTP Input node and is returned to the caller as a bare HTTP 500 with no structured error body, no correlation id, and no local logging. A caller cannot distinguish "credential not configured" from "runtime fault" except by reading the 500 body text. This is a conscious minimal-harness choice and is acceptable for verification, but it would not be acceptable in a production deliverable.

**Location:** [`GetCredentialFlow.msgflow:10`](GetCredentialFlow.msgflow)

```xml
<nodes xmi:type="ComIbmJavaCompute.msgnode:FCMComposite_1" xmi:id="FCMComposite_1_2" location="280,80" javaClass="com.acme.security.GetCredential_JavaCompute">
  <translation xmi:type="utility:ConstantString" string="Lookup Credential"/>
</nodes>
<!-- Only out (FCMConnection_2) is wired; no connection from the Failure terminal -->
```

**Recommendation:**
- For any non-harness reuse, wire the JavaCompute Failure terminal to a small error subflow that builds a standardized JSON error response (error code, message, correlation id) and replies with an appropriate 4xx/5xx status, so the caller receives a typed error instead of a raw 500.
- Benefit: callers get an actionable, machine-readable error; failures become observable.
- Consideration: keep the not-found case (a 404-style typed error) distinct from unexpected faults (500).
- Reference: [IBM ACE 13 - Handling errors in message flows](https://www.ibm.com/docs/en/app-connect/13.0.x?topic=flows-handling-errors-in-message)

**Severity:** 🟡 Medium - a processing node has an unconnected Failure terminal with no upstream TryCatch. Accepted as a deliberate harness tradeoff.

---

### FD-01 HTTP Reply built without a Properties folder or response headers 🟡

**Issue:** The reply is assembled from a brand-new `MbMessage` that contains only a JSON body - no `Properties` folder is established and no HTTP response header tree is built ([`GetCredential_JavaCompute.java:46`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)). Because the output tree must be serialized in child order (Properties first, transport headers next, body last), a reply with no Properties folder leaves the HTTP Reply node to fall back to runtime defaults for content type and status. The client may receive the JSON payload without a `Content-Type: application/json` header, which some strict clients reject.

**Location:** [`GetCredential_JavaCompute.java:46`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)

```java
MbMessage outMessage = new MbMessage();
outAssembly = new MbMessageAssembly(inAssembly, outMessage);

MbElement root = outMessage.getRootElement();
MbElement json = root.createElementAsLastChild(MbJSON.PARSER_NAME);   // body only - no Properties, no HTTP header tree
MbElement data = json.createElementAsLastChild(MbJSON.OBJECT, MbJSON.DATA_ELEMENT_NAME, null);
```

**Recommendation:**
- Build the output assembly so a `Properties` folder is present and set `Properties.ContentType` (or create an `HTTPReplyHeader`/`HTTPResponseHeader` tree) to `application/json` before the JSON body. The simplest path is to copy `Properties` from the input message into the new message first, then append the JSON body.
- Benefit: deterministic, correct content type and header ordering on the wire.
- Consideration: keep Properties before the body so child order is preserved.
- Reference: [IBM ACE 13 - HTTP headers and the message tree](https://www.ibm.com/docs/en/app-connect/13.0.x?topic=development-http-nodes)

**Severity:** 🟡 Medium - best-practice / interoperability concern; works in the harness because the runtime supplies a default content type.

---

### FD-02 Default Additional Instances not documented as intentional 🟢

**Issue:** The flow runs with the default Additional Instances of 0 (single-threaded). For a low-volume verification harness this is correct, but there is no sticky note or comment recording that single-threading is intentional. The Java node is thread-safe, so the setting could be raised safely if the harness were ever load-tested.

**Location:** [`GetCredentialFlow.msgflow:3`](GetCredentialFlow.msgflow)

**Recommendation:**
- Add a sticky note to the flow stating that Additional Instances is intentionally left at 0 because this is a single-request verification harness, and that the JavaCompute node is thread-safe should instances be increased.
- Benefit: removes ambiguity for anyone who reuses the flow.
- Reference: [IBM ACE 13 - Configuring additional instances](https://www.ibm.com/docs/en/app-connect/13.0.x?topic=flows-message-flow-properties)

**Severity:** 🟢 Low - documentation only; no runtime impact at harness volume.

---

### C-01 username and clientId decoded with the platform default charset 🟢

**Issue:** `username` and `clientId` are converted from their byte arrays with `new String(byte[])`, which uses the JVM default charset ([`GetCredential_JavaCompute.java:41`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)). If a stored credential ever contained non-ASCII characters, the decoded string would depend on the integration server's locale and could differ between environments. The secret and password are only length-measured, so they are unaffected.

**Location:** [`GetCredential_JavaCompute.java:41`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)

```java
String username = credential.hasUsername() ? new String(credential.username()) : "";
String clientId = credential.hasClientId() ? new String(credential.clientId()) : "";
```

**Recommendation:**
- Decode with an explicit charset: `new String(credential.username(), java.nio.charset.StandardCharsets.UTF_8)`.
- Benefit: deterministic, environment-independent decoding.
- Consideration: confirm the vault stores credentials as UTF-8 (the ACE default).
- Reference: [Java SE - String(byte[], Charset)](https://docs.oracle.com/javase/8/docs/api/java/lang/String.html)

**Severity:** 🟢 Low - latent only; ASCII credentials are unaffected.

---

### C-02 Redundant RuntimeException catch is dead relative to the generic catch 🟢

**Issue:** The `catch (RuntimeException e) { throw e; }` block ([`GetCredential_JavaCompute.java:62`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)) rethrows unchanged, which is exactly what would happen if it were absent and the unchecked exception simply propagated. It adds no behaviour beyond bypassing the generic `Exception` wrapper below it. This is harmless but slightly obscures intent.

**Location:** [`GetCredential_JavaCompute.java:60`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)

```java
} catch (MbException e) {
    throw e;
} catch (RuntimeException e) {
    throw e;                 // identical to letting it propagate
} catch (Exception e) {
    throw new MbUserException(this, "evaluate()", "", "", e.toString(), null);
}
```

**Recommendation:**
- If the intent is to let unchecked exceptions propagate raw while wrapping only checked exceptions, add a one-line comment saying so; otherwise remove the `RuntimeException` block, since the body of `evaluate()` throws no checked exceptions other than `MbException` and the generic catch is effectively unreachable.
- Benefit: clearer intent, less dead code.
- Reference: [IBM ACE 13 - Java exception handling](https://www.ibm.com/docs/en/app-connect/13.0.x?topic=nodes-javacompute-node)

**Severity:** 🟢 Low - style / clarity only.

---

### CF-01 server.conf.yaml prerequisite is not captured in the project 🟢

**Issue:** The lookup only returns a value when `userdefined` is listed under `Credentials.userRetrievableCredentialTypes` in `server.conf.yaml`. That prerequisite lives outside the BAR and is recorded only in the Java class Javadoc ([`GetCredential_JavaCompute.java:18`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)). Anyone deploying the harness on a fresh server without that setting will hit the not-found path and a 500.

**Location:** [`GetCredential_JavaCompute.java:18`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java)

**Recommendation:**
- Add a short README or a project-level note giving the exact `server.conf.yaml` snippet and the `ibmint set credentials` (vault) command used to seed `blogOAuthCred`, so the harness is reproducible without consulting the blog.
- Benefit: self-contained, reproducible verification.
- Reference: [IBM ACE 13 - Storing and using credentials with the vault](https://www.ibm.com/docs/en/app-connect/13.0.x?topic=cacm-storing-credentials-vault)

**Severity:** 🟢 Low - reproducibility / documentation concern.

---

## Findings Summary

| ID | Title | Severity | Location |
|----|-------|----------|----------|
| R-01 | JavaCompute Failure terminal unconnected, no flow-level error handling | 🟡 Medium | [`GetCredentialFlow.msgflow:10`](GetCredentialFlow.msgflow) |
| FD-01 | HTTP Reply built without a Properties folder or response headers | 🟡 Medium | [`GetCredential_JavaCompute.java:46`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java) |
| FD-02 | Default Additional Instances not documented as intentional | 🟢 Low | [`GetCredentialFlow.msgflow:3`](GetCredentialFlow.msgflow) |
| C-01 | username and clientId decoded with the platform default charset | 🟢 Low | [`GetCredential_JavaCompute.java:41`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java) |
| C-02 | Redundant RuntimeException catch is dead relative to the generic catch | 🟢 Low | [`GetCredential_JavaCompute.java:60`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java) |
| CF-01 | server.conf.yaml prerequisite is not captured in the project | 🟢 Low | [`GetCredential_JavaCompute.java:18`](../CredVaultDemoJava/com/acme/security/GetCredential_JavaCompute.java) |

*One row per finding. Order: Critical -> High -> Medium -> Low.*

---

## ACE Best Practices Compliance

### Alignment with IBM ACE Guidelines

#### ✅ Followed Best Practices

1. **Thread-safe JavaCompute node**
   - Only `static final` constants and method-local variables; no mutable shared state.
   - Aligns with the ACE single-shared-instance rule for JavaCompute nodes.

2. **MbException caught before generic Exception**
   - Preserves ACE error number, insertion strings, and exception hierarchy.
   - Aligns directly with the Java guideline on catch ordering.

3. **Secret values never disclosed**
   - Only lengths of the client secret and password are returned; the values stay in the vault.
   - Strong default for any credential-handling component.

4. **Lean flow with no common-mistake patterns**
   - No adjacent compute nodes, no flow loops, no unnecessary nodes, single output connection per node (execution order unambiguous), On Demand parsing on the input node.

5. **Vault-based credential retrieval via the documented runtime API**
   - Uses `MbCredential.getCredential` rather than embedding secrets in the flow or properties - the recommended ACE 13 pattern.

#### ⚠️ Areas for Enhancement

1. **Flow-level error handling**
   - Currently: failures propagate to the HTTP Input node as a raw HTTP 500.
   - Recommendation: wire the JavaCompute Failure terminal to an error subflow that returns a typed JSON error (distinct not-found vs fault).
   - Benefits: actionable, observable errors for callers.
   - Reference: [IBM ACE 13 - Handling errors in message flows](https://www.ibm.com/docs/en/app-connect/13.0.x?topic=flows-handling-errors-in-message)

2. **HTTP response Properties / content type**
   - Currently: a fresh `MbMessage` with a JSON body only; no Properties folder.
   - Recommendation: establish Properties first and set `application/json`.
   - Benefits: deterministic content type and header ordering on the wire.
   - Reference: [IBM ACE 13 - HTTP nodes](https://www.ibm.com/docs/en/app-connect/13.0.x?topic=development-http-nodes)

---

## Testing Recommendations

### Unit Testing

1. **JavaCompute behaviour**
   - Test the happy path: a seeded `blogOAuthCred` returns `retrieved=true` with correct `username`, `clientId`, and non-negative lengths.
   - Test the not-found path: an unseeded or non-retrievable credential throws `MbUserException` with the diagnostic message.
   - Test a credential that has a password but no client secret (and vice versa) to confirm the `has*()` guards.

### Integration Testing

1. **End-to-end HTTP**
   - POST/GET `/getcred` against a server where `userdefined` is in `userRetrievableCredentialTypes` and assert a 200 with the expected JSON shape.
   - Repeat against a server where the type is NOT retrievable and assert the failure response (currently 500) - this validates the documented prerequisite.

### Performance Testing

1. **Not a priority for the harness.** If reused under load, raise Additional Instances and confirm the thread-safe node sustains parallel requests without shared-state corruption.

---

## Operational Considerations

### Monitoring

1. **HTTP 500 rate on /getcred**
   - Monitor for 500s, which here indicate either credential-not-found or an unexpected fault. Until R-01 is addressed these two cases are indistinguishable without reading the response body.

### Maintenance

1. **Credential lifecycle**
   - When `blogOAuthCred` is rotated or removed from the vault, the harness returns the not-found path. Keep the vault seeding command alongside the project (see CF-01).

### Troubleshooting

1. **Common Issues**
   - Issue: HTTP 500 with "Credential ... was not found".
   - Cause: credential not seeded, or `userdefined` missing from `Credentials.userRetrievableCredentialTypes`.
   - Resolution: seed the credential with the vault tooling and add the type to `server.conf.yaml`, then restart the integration server.

---

## Dependencies and Integration Points

### Upstream Systems
- **HTTP client** - any caller issuing a request to `/getcred`.

### Downstream Systems
- N/A - the node reads from the local integration server vault; there is no external downstream call.

### Shared Libraries
- **CredVaultDemoJava** - the referenced Java project providing the compute node class.

---

## Conclusion

CredVaultDemo is a clean, correctly scoped verification harness that does exactly what it sets out to do: prove that a vault credential is retrievable from a JavaCompute node via the documented ACE 13.0.7.2 runtime API and report proof of retrieval without leaking secrets. The Java code is thread-safe, catches `MbException` before the generic `Exception`, and returns only secret lengths - all strong practices. The flow is minimal and free of the common message-flow mistakes.

### Key Strengths
- Secret-safe response design (lengths only, never values).
- Thread-safe JavaCompute implementation with correct exception-catch ordering.
- Lean flow with no adjacent-compute, loop, or unordered-connection issues.

### Priority Improvements

1. **Medium Priority - Reliability and HTTP correctness**
   - Wire the JavaCompute Failure terminal to a typed error response (R-01).
   - Establish a Properties folder and set `application/json` on the reply (FD-01).

2. **Low Priority - Robustness and clarity**
   - Decode credential strings with an explicit UTF-8 charset (C-01).
   - Remove or comment the redundant `RuntimeException` catch (C-02).

3. **Low Priority - Documentation and reproducibility**
   - Document the intentional single-threading (FD-02).
   - Capture the `server.conf.yaml` and vault-seeding prerequisites in the project (CF-01).

**Overall Assessment:** ✅ Production-Ready as a verification harness with Recommended Enhancements. The Medium findings (R-01, FD-01) would need to be addressed before any of this code was promoted into a production deliverable, but for its stated purpose - validating the blog post's claims - the harness is fit and correct.

**Use Case Fit:** Excellent. The harness is the right size for its goal: it exercises exactly the runtime API under test, keeps secrets safe, and surfaces the most likely misconfiguration with a self-diagnosing message.

---

## Review Metadata

**Reviewed By:** Claude (ACE Review Mode)  
**Review Date:** 2026-06-25  
**ACE Version:** 13.0.7.2  
**Review Level:** 2 - Intermediate (ESQL + Flow Design + Java + Common Mistakes + Error Handling)  
**Review Focus:** Flow Design + Java + Error Handling  
**Next Review:** Before any reuse of this code outside the verification harness (address R-01 and FD-01 first)
