package util.cli;

import java.util.ArrayList;

import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbExecutionGroup;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbUserException;

public class CommandLineExecution_JavaCompute extends MbJavaComputeNode {

	private String logLevel;
	
	public void evaluate(MbMessageAssembly inAssembly) throws MbException {
		String commandLine = (String) getUserDefinedAttribute("commandLine");
		String outputFile = (String) getUserDefinedAttribute("outputFile");
		logLevel = (String) getUserDefinedAttribute("logLevel");
		int returnValue = 0;
			
		MbOutputTerminal out = getOutputTerminal("out");
		MbOutputTerminal alt = getOutputTerminal("alternate");

		MbMessage inMessage = inAssembly.getMessage();
		MbMessageAssembly outAssembly = null;
		Logging logger = new Logging(logLevel);
		Command command;
		try {
			// create new message as a copy of the input
			MbMessage outMessage = new MbMessage(inMessage);
			outAssembly = new MbMessageAssembly(inAssembly, outMessage);
			// ----------------------------------------------------------
			// Add user code below
			
			MbElement messageCommandLine = inMessage.getRootElement().getLastChild().getFirstElementByPath("/JSON/Data/CLE/commandLine");
			if (messageCommandLine != null && !messageCommandLine.getValueAsString().equals("")) {
				commandLine = messageCommandLine.getValueAsString();
				
				MbElement messageOutputFile = inMessage.getRootElement().getLastChild().getFirstElementByPath("/JSON/Data/CLE/outputFile");
				if (messageOutputFile != null && !messageOutputFile.getValueAsString().equals("")) outputFile = messageOutputFile.getValueAsString();
				
				MbElement messageLogLevel = inMessage.getRootElement().getLastChild().getFirstElementByPath("/JSON/Data/CLE/logLevel");
				if (messageLogLevel != null && !messageLogLevel.getValueAsString().equals("")) logLevel = messageLogLevel.getValueAsString();
				logger.setLogLevel(logLevel);
				
				logger.log_debug("Using parameters from input message");
			} else {
				logger.log_debug("Using parameters defined on the subflow");
			}
			
			logger.log_info("Running command line from IS: " + MbExecutionGroup.getExecutionGroup().getName() + ", APP: " + getMessageFlow().getApplicationName() + ", FLOW: " + getMessageFlow().getName());
			logger.log_info("Commandline: " + commandLine);
			logger.log_debug("OutputFile: " + outputFile);
			logger.log_debug("LogLevel: " + logLevel);
			
			command = new Command(logger);
			returnValue = command.Exec(commandLine);
			
			logger.log_info("Return value: " + String.valueOf(returnValue));
			
			ArrayList<String> output = command.getOutput();

			MbMessage env = outAssembly.getGlobalEnvironment();
			MbElement envVariables = env.getRootElement().createElementAsLastChild(MbElement.TYPE_NAME, "Variables", null);
			MbElement envCommandOutput = envVariables.createElementAsLastChild(MbElement.TYPE_NAME, "CommandOutput", null);
			envCommandOutput.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "ResultCode", returnValue); 
			envCommandOutput.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Output", output.toString()); 
			
			logger.log_debug(output);
			if (outputFile != null && !outputFile.equals("")) command.WriteOutputToFile(output, outputFile);
			
			if (returnValue != 0) {
	            ArrayList<String> error = command.getError();
	            envCommandOutput.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Error", error.toString()); 
	            logger.log_error(error);
	            if (outputFile != null && !outputFile.equals("")) command.WriteOutputToFile(error, outputFile);
	            alt.propagate(outAssembly);
	            return;
	        } 
	        
			// End of user code
			// ----------------------------------------------------------
		} catch (CommandLineException e) {
			logger.log_error("Something went wrong running the CommmandLineExecution: " + e.getMessage());
			throw new MbUserException(this, "evaluate()", "", "", e.toString(), null);
		} catch (MbException e) {
			// Re-throw to allow Broker handling of MbException
			throw e;
		} catch (RuntimeException e) {
			// Re-throw to allow Broker handling of RuntimeException
			throw e;
		} catch (Exception e) {
			// Consider replacing Exception with type(s) thrown by user code
			// Example handling ensures all exceptions are re-thrown to be handled in the flow
			throw new MbUserException(this, "evaluate()", "", "", e.toString(), null);
		} finally {
			command = null;
			logger = null;
		}
		// The following should only be changed
		// if not propagating message to the 'out' terminal
		
		out.propagate(outAssembly);
	}
	
	

	/**
	 * onPreSetupValidation() is called during the construction of the node
	 * to allow the node configuration to be validated.  Updating the node
	 * configuration or connecting to external resources should be avoided.
	 *
	 * @throws MbException
	 */
	@Override
	public void onPreSetupValidation() throws MbException {
	}

	/**
	 * onSetup() is called during the start of the message flow allowing
	 * configuration to be read/cached, and endpoints to be registered.
	 *
	 * Calling getPolicy() within this method to retrieve a policy links this
	 * node to the policy. If the policy is subsequently redeployed the message
	 * flow will be torn down and reinitialized to it's state prior to the policy
	 * redeploy.
	 *
	 * @throws MbException
	 */
	@Override
	public void onSetup() throws MbException {
		
	}

	/**
	 * onStart() is called as the message flow is started. The thread pool for
	 * the message flow is running when this method is invoked.
	 *
	 * @throws MbException
	 */
	@Override
	public void onStart() throws MbException {
	}

	/**
	 * onStop() is called as the message flow is stopped. 
	 *
	 * The onStop method is called twice as a message flow is stopped. Initially
	 * with a 'wait' value of false and subsequently with a 'wait' value of true.
	 * Blocking operations should be avoided during the initial call. All thread
	 * pools and external connections should be stopped by the completion of the
	 * second call.
	 *
	 * @throws MbException
	 */
	@Override
	public void onStop(boolean wait) throws MbException {
	}

	/**
	 * onTearDown() is called to allow any cached data to be released and any
	 * endpoints to be deregistered.
	 *
	 * @throws MbException
	 */
	@Override
	public void onTearDown() throws MbException {
	}

}
