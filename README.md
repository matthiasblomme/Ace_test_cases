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

### BusinessTransactionMonitoring
...

### Create Json Array
A couple of ways of creating a json array

### Create XML Fields
A flow to find out how to create a self closing xml tag `<lastfield2 />` and not just create an empty xml tag
` <lastfield2></ lastfield2>`

### Delete Array Contents
A flow to see what the best way is to delete the contents from an array in the message tree without deleting and
recreating the entire array.

### Json To Xml
A very basic and dumb flow to demonstrate how to convert between json and xml with a single line of code.

### Json Validation
Validating JSON messages on the http input node and with a validate node. 
Supplied an invalid json schema (with https for the schema definition) since I ran into that paticular issue.

### Single and Double Quotes
An esql test to remove single and/or double quotes

### Timer and File
A flow to check and test that 2 input nodes start 2 input threads.

### User Defined properties
A flow to verify that the flow restarts or reinitializes if you change a UDP value.

### Working With References
Upon playing around with references I noticed that sometimes the reference doesn't show up in the debugger but is still 
callable. There was a specific use cases that required to know if there were children (by using cardinality) of a certain
reference. Depending where you create the reference from (object or message tree) the behaviour is different. I wanted
to test and see the differences in behaviour.