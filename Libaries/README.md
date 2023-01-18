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

### Custom Timout
A library that contains 2 timeout processing flows. Let's say you want some retry logic in your code but you want to wait 
for a certain amount of time between tries without breaking transactions or loosing some info from your environment.
There are 2 subflows here
 - ReprocessWithTimeoutSingleTransaction.subflow: this one uses mq message expiry and mqget to wait 
 - ReprocessWithTimeoutSeperateTransaction.subflow: this one uses timeout notification in combination with mq message expiry

### Sharepoint File Upload
SharePointLibrary

Uploading a file to sharepoint including authentication via an azure ad app