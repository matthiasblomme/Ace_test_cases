# General
## Description
This library contains a subflow with a JavaCompute node that executes a command or script from either the subflow properties or the incoming message and stores the result in the environment.

## Components
util.cli.CommandLineExecution.subflow

### Description
The flow calls a JavaCompute node that executes a script or command from the input message or from user defined properties.

### Technical setup


The subflow takes the command or script from the user defined property "commandLine" or from the input message element "JSON/Data/CLE/commandLine" if it is present and executes it on the runtime.

There are 2 additional parameters available "logLevel" and "outputFile". Both are read from the UDP by default but if the "JSON/Data/CLE/commandLine" element is present in the input message you can overwrite both values via "JSON/Data/CLE/logLevel" and "JSON/Data/CLE/outputFile" respectively.

The output of the script is put into the Environment under "Environment/Variables/CommandOutput/Output" and the resultcode is available at "Environment/Variables/CommandOutput/ResultCode". If the script execution fails for some reason, the error output is put under "Environment/Variables/CommandOutput/Error".

The output and error output are both written to the outputFile if it is configured.

Depending on the logLevel, more or less data is written to the standard output (stdout/stderr on linux or console.log on windows). There are 4 options, sorted from less output to more output:
 - NONE
 - ERROR
 - INFO
 - DEBUG

If an actual exception occurs like "file not found" or "authorization issues" (so not an error from the script) this exception is thrown back to the flow and needs to be handled from the parent flow.

There are a couple of limitations:
 - Shell scripts are not allowed on windows environments.
 - Bat scripts are not allowed on linux environment

### Configuration

Command
 - commandLine: the command or script to execute
 - outputFile: when configured the file where the stdout and sterr are written to
 - logLevel: the amound of logging you want to see in the standard integration server log file

