# CredVaultDemo - retrieving a stored credential from a Java Compute node (ACE 13)

This is a small, self-contained IBM App Connect Enterprise v13 application built to
verify whether you can store a credential and read it back from a Java Compute node.
It was created to fact-check a blog post that claimed this is done with a class
`com.ibm.broker.plugin.MBCredentials` and a `getCredentials()` call.

That class does not exist. The correct, verified API is used here instead.

## What the flow does

```
HTTP Input  /getcred  ->  JavaCompute "Lookup Credential"  ->  HTTP Reply
```

The Java node looks a credential up in the integration server vault and returns a
small JSON proof of retrieval. It never returns secret values, only the username,
the client id, and the lengths of the password and client secret.

By default it looks up type `userdefined`, name `blogOAuthCred`. You can override
both per request with HTTP headers `X-Cred-Type` and `X-Cred-Name`, which makes it
easy to probe how different storage styles behave.

## Project layout

```
CredVaultDemo/                              (application project)
  .project                                 references CredVaultDemoJava
  application.descriptor
  GetCredentialFlow.msgflow                HTTP Input -> JavaCompute -> HTTP Reply
  README.md                                this file
  CredVaultDemo_technical_review.md        Level 2 code review
  testing/standalone-server/
    deploy_and_test.bat                    full automated harness
    run-test-with-ace-env.bat              wrapper that sources the ACE profile

CredVaultDemoJava/                          (Java compute project, sibling)
  .project  .classpath
  com/acme/security/GetCredential_JavaCompute.java
```

## The verified API (what the blog got wrong)

```java
// CORRECT - what this project uses (confirmed against javacompute.jar):
MbCredential cred = MbCredential.getCredential("userdefined", "blogOAuthCred");
char[] user   = cred.username();      // also clientId(), clientSecret(), password()
boolean has   = cred.hasUsername();   // guard before each accessor

// WRONG - the blog's version, no such class or method:
// com.ibm.broker.plugin.MBCredentials.getCredentials()
```

`MbCredential.getCredential(String type, String name)` is static, takes the
credential TYPE and NAME, and throws `MbException`. There is no `MBCredentials`
class and no no-argument `getCredentials()`.

## Prerequisites

- IBM ACE 13.0.7.x installed.
- Commands run from a shell where the ACE environment is sourced. On Windows that
  means running `mqsiprofile.cmd` first (the `run-test-with-ace-env.bat` wrapper
  does this). Without it, `ibmint` / `IntegrationServer` / `mqsi*` are not on PATH.
- `curl` (built in on current Windows).

Adjust the paths near the top of `deploy_and_test.bat` for your machine.

## Fastest path: the automated harness

```
testing\standalone-server\run-test-with-ace-env.bat
```

It packages the BAR, creates a work directory, stores the credential, deploys,
starts a standalone integration server, drives `POST /getcred`, verifies the
response, and stops the server.

## Manual test, step by step

All commands assume the ACE environment is already sourced.

1. Package the application and its Java project into a BAR:

   ```
   ibmint package --input-path "C:\Users\<you>\IBM\ACET13\workspace" ^
     --project CredVaultDemo --project CredVaultDemoJava ^
     --output-bar-file "D:\tmp\CredVaultDemo.bar" --java-version 17
   ```

2. Create a standalone integration server work directory:

   ```
   mqsicreateworkdir "D:\tmp\TEST_SERVER_CRED"
   ```

3. (Optional) set the HTTP listener port. Append to
   `D:\tmp\TEST_SERVER_CRED\overrides\server.conf.yaml` (spaces, no tabs):

   ```yaml
   ResourceManagers:
     HTTPConnector:
       ListenerPort: 7799
   ```

4. Store the credential. The `userdefined::` prefix is REQUIRED (see Rules below):

   ```
   mqsisetdbparms --work-dir "D:\tmp\TEST_SERVER_CRED" ^
     --resource userdefined::blogOAuthCred ^
     --user svc_blog_user --password p4ss-WORD ^
     --client-identity blog-client-123 --client-secret s3cr3t-CLIENT
   ```

5. Deploy the BAR into the work directory:

   ```
   ibmint deploy --input-bar-file "D:\tmp\CredVaultDemo.bar" ^
     --output-work-directory "D:\tmp\TEST_SERVER_CRED"
   ```

6. Start the server (wait for `BIP1991I` in the output):

   ```
   IntegrationServer --work-dir "D:\tmp\TEST_SERVER_CRED" --name TEST_SERVER_CRED
   ```

7. Drive the flow:

   ```
   curl -X POST http://localhost:7799/getcred
   ```

   Expected response:

   ```json
   {"retrieved":true,"credentialType":"userdefined","credentialName":"blogOAuthCred",
    "username":"svc_blog_user","clientId":"blog-client-123",
    "clientSecretLength":13,"passwordLength":9}
   ```

8. Probe other types or names without rebuilding, using headers:

   ```
   curl -X POST http://localhost:7799/getcred ^
     -H "X-Cred-Type: userdefined" -H "X-Cred-Name: blogOAuthCred"
   ```

## Rules proven by testing (the parts the blog omits)

1. Only credentials of type `userdefined` can be referenced from user Java code
   BY DEFAULT. Any other type returns:

   ```
   BIP9544E: Unable to lookup credential '<name>' of type '<type>' because only
   credentials of type 'userdefined' can be referenced from user code.
   ```

   To read other types, add them to `server.conf.yaml` and restart:

   ```yaml
   Credentials:
     userRetrievableCredentialTypes: 'ALL'   # or a specific type such as 'odbc'
   ```

2. The `mqsisetdbparms` resource prefix determines the credential type:
   - `--resource userdefined::myCred`  -> type `userdefined`  (retrievable by default)
   - `--resource myCred`  (bare name)  -> type `odbc`          (blocked by default)
   - `--resource jdbc::myCred`         -> type `jdbc`          (blocked by default)

   `userdefined::` is not listed in the `mqsisetdbparms` help, but it works.

3. `mqsisetdbparms` is not a live update. A change made while the server is running
   is only picked up after the server is restarted.

4. The keystore part of the blog is independent of credentials and is standard:

   ```
   keytool -genkeypair -alias brokercert -keyalg RSA -keysize 2048 ^
     -keystore broker.jks -storepass <pw> -keypass <pw> -dname "CN=..."
   keytool -list -keystore broker.jks -storepass <pw>
   ```

   Note: keytool warns that JKS is a proprietary format and recommends PKCS12.

## Cleanup

```
mqsistop TEST_SERVER_CRED          (or stop the IntegrationServer process)
rmdir /s /q "D:\tmp\TEST_SERVER_CRED"
del "D:\tmp\CredVaultDemo.bar"
```
