# Ace_test_cases

## Description
Some mixed ace test cases that I created and want to maintain.
Don't expect any fancy stuff here. I've created these because I wanted to
 - test the value of a variable
 - test different ways of modifying a field
 - test how some threads are started
 - quickly explain something with a visual aid
 - debug an issue
 - ...

Really don't expect anything fancy. A lot of this is just a couple of dumb tries that I occasionaly do to verify
or check some things that I wonder about or that I'm explaining to someone. In the past I usually just throw these
test projects away and I ended up making some of these again, so why not just save them?

## Cases

### Backout Count
A very basic and dumb flow that keeps throwing exceptions and keeps performing a backout
of the input message to test the backout count and backout threshold.

### BOM Header
Stripping a bom header from an input message

### BusinessTransactionMonitoring
A couple of simple flows to test BTM in ace. These flows are complementary to the BTM blog post on Integration Designers,
although I'm still waiting for it to be published.

### CheckCiphers
Enumeration of available and default TLS cipher suites using Java's `SSLServerSocketFactory` to inspect runtime cryptography configuration.

### CommandLine
Library for executing external command-line processes with configurable parameters and logging, capturing output to files and handling success/failure results.

### CompareTimestamps
Comparing timestamp values across multiple compute modules and Java to verify time consistency across different processing stages.

### ContextLogNode
Demonstrates logging flow execution using the Logging Library with context tree passing and error handling across multiple logging points.

### ContextTreeFlowOrderApp
Verifies context tree and `LocalEnvironment` variable preservation across REST API calls, enabling state restoration after external requests.

### Create Json Array
A couple of ways of creating a json array

### Create XML Fields
A flow to find out how to create a self closing xml tag `<lastfield2 />` and not just create an empty xml tag
` <lastfield2></ lastfield2>`

### CreatingJsonFromBody
Converting a JSON string stored in the message body to a proper JSON tree element using bitstream parsing.

### CustomTimeout
Contains two retry-pattern sub-projects: `ReprocessWithTimeout` and `WaitForTimeout`.

#### WaitForTimeout
Demonstrates an MQ-expiry-driven retry style (rather than a timer-driven one). An MQ message on the START queue
triggers an HTTP call; on failure, the `ReprocessWithTimeoutSeperateTransaction` subflow stores the original
message on `ORIGINALMESSAGE`, places a wait-marker on `WAITQUEUE` with `MQMD.Expiry` set to the desired delay,
and uses TimeoutControl only as a safety-net trigger. When the wait message expires (or the timeout safety-net
fires), the subflow retrieves the original from `ORIGINALMESSAGE` and re-routes it to the HTTPRequest node via
a `PROPAGATE TO LABEL`. Useful when you want the retry delay enforced by MQ semantics (so it survives
integration-server restarts cleanly through MQ persistence) rather than by the timeout pair alone.

> Contrast with `RetryHTTPDispatcher`: that project exercises the timeout nodes as the **primary** scheduler
> (cleanest test of those nodes), whereas `WaitForTimeout` uses MQ expiry as the primary delay with the
> timeout pair only as a safety net (more production-like for long retry windows).

#### ReprocessWithTimeout
Timeout-driven retry pattern that stores the original message separately and uses the TimeoutControl / TimeoutNotification node pair (with MQ persistence) to schedule delayed reprocessing.

### DeadLetterHandler
Processing messages from the `SYSTEM.DEAD.LETTER.QUEUE` and routing them either back to their original destination or to a hold queue with backout handling.

### Delete Array Contents
A flow to see what the best way is to delete the contents from an array in the message tree without deleting and
recreating the entire array.

### FlowOrder
Testing execution order and variable propagation across sequential compute modules to verify `LocalEnvironment` variable assignment timing.

### HelloWorld
Basic HTTP request/response flow with a route-to-label pattern and error handlers for catch, failure, and timeout conditions.

### HelloWorld_https
HTTPS variant of HelloWorld demonstrating a secure HTTP input listener with JSON message domain handling.

### HTTPListenerHealthCheck
Health check endpoint responding to HTTP requests with computed status information for integration server liveness probes.

### HTTPReply_SeparateFlow
Receiving an HTTP request and routing to MQ output without immediate reply, enabling separate asynchronous response handling.

### HttpInputDelayCheck
REST API flow generated from an OpenAPI definition, testing HTTP input node delay behaviour with timeout and error handling.

### HttpRequestArray
Testing the HTTPRequest node with array-based input parameters for batch processing of multiple backend requests.

### JavaEnvironment
Extracting and returning all Java environment variables and system properties as JSON for runtime diagnostics.

### JsonArrayApp
Demonstrating array element access methods in ESQL including absolute indexing, relative positioning (first/last), and cardinality operations.

### JsonParser
JSON validation against schema with error detail extraction and reporting of parser exceptions from invalid input.

### Json To Xml
A very basic and dumb flow to demonstrate how to convert between json and xml with a single line of code.

### Json Validation
Validating JSON messages on the http input node and with a validate node.
Supplied an invalid json schema (with https for the schema definition) since I ran into that paticular issue.

### LoggingLib
Shared logging library subflows for consistent event logging across applications, with UUID tracking and status reporting.

### MIME
Building multipart MIME email messages with both text and JSON attachment parts from computed content.

### MissingQueryParam
HTTP query parameter access and handling of missing parameters using `COALESCE` to provide defaults.

### MQDeadLetterRescuer
Processing dead-letter queue messages by routing them back to original queues or hold queues based on validation and backout count logic.

### MQEventReader
Listening to MQ system event queues (channel events, configuration changes, command responses) and writing event details to files.

### mq_usr
Reading and writing custom user properties (`MQRFH2.mq_usr`) on MQ messages, with nested structure support.

### OAuthProvider
OAuth token generation via REST endpoint, using a compute module for token request preparation.

### PackageTest
Package and library structure testing, demonstrating deployment artifact organisation across shared and application libraries.

### PascalCaseToCamelCase
Converting PascalCase string identifiers to camelCase using character iteration and case conversion logic.

### PDFRestToFile
HTTP REST endpoint receiving PDF binary content and writing it to the file system using the BLOB domain and a file output.

### randomGenerator
Generating random integer values within a specified range using `RAND()` for unique identifier creation.

### ReadEnvVars
Reading operating system environment variables via a Java external function call wrapped in ESQL.

### ReprocessBackoutMessages
Reprocessing messages from a backout file trigger by reading from queue, validating attempt count, and routing to original destination or hold queue.

### RestoreFromBackout
Reading backout messages from file, retrieving the original message from queue, and restoring it to the original destination with attempt tracking.

### RetryHTTPDispatcher
Demonstrates a delayed-retry pattern using the TimeoutControl / TimeoutNotification node pair as the actual
scheduler. An MQ message arriving on `APP.IN` triggers an outbound HTTP POST; on connection failure or
transient HTTP error (408/429/5xx) the flow schedules a retry with exponential backoff
(10s / 20s / 40s / 80s / 160s) by writing a `LocalEnvironment.TimeoutRequest` and parking the original message
in `SYSTEM.BROKER.TIMEOUT.QUEUE`. When the timer fires, TimeoutNotification re-injects the message into the
same HTTPRequest node. After 5 failed attempts the message is dead-lettered to `APP.ERROR` with the failure
reason annotated in `MQRFH2.usr`. Bundled with a companion `HTTPInjector` flow exposing `POST /inject` so the
whole cycle can be kicked off with curl instead of a manual MQ put.

> See `CustomTimeout/WaitForTimeout` for an MQ-expiry-driven counterpart that uses the timeout pair only as a
> safety net.

### SelectTheRow
Using ESQL `THE()` with a `SELECT … WHERE` clause to extract a single matching element from an array by condition.

### SelectTheRowJava
XPath-based element selection from an XML array using Java `MbXPath` to find the first matching row by attribute condition.

### Sharepoint File Upload
SharepointFileUpload
Uploading a file to sharepoint including authentication via an azure ad app

### SharePointLibrary
Library for SharePoint file operations (upload, download, delete) with Microsoft Graph API integration and OAuth authentication.

### Single and Double Quotes
An esql test to remove single and/or double quotes

### TestArrayExistsOrEmpty
Testing multiple methods of checking array emptiness — cardinality, null checks, `EXISTS()`, and `FIELDTYPE()` — to compare the different approaches.

### TestCCSID
Testing character set identification by parsing BLOB content and determining the CCSID encoding.

### TestQueueEnvironment
Basic message flow reading from a queue and testing `LocalEnvironment` variable access for queue context information.

### TimeStampConvert
Converting between `TIMESTAMP` and string formats with format specifiers for flexible date/time parsing and serialisation.

### Timer and File
A flow to check and test that 2 input nodes start 2 input threads.

### Unit Test Ignore Fields
A applicatino setup to demo how to ignore specific fields in ACE unit/integration testing

### User Defined properties
A flow to verify that the flow restarts or reinitializes if you change a UDP value.

### Working With References
Upon playing around with references I noticed that sometimes the reference doesn't show up in the debugger but is still
callable. There was a specific use cases that required to know if there were children (by using cardinality) of a certain
reference. Depending on where you create the reference from (object or message tree) the behaviour is different. I wanted
to test and see the differences in behaviour.

### XmlFolderType
Testing XMLNSC field type identification and reference types (Folder, Element, Attribute, Value) for XML tree navigation.
