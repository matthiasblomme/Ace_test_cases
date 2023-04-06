package shared;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbJSON;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbUserException;
import com.ibm.mq.constants.*;

public class HandleEvent_JavaCompute extends MbJavaComputeNode {

	public void evaluate(MbMessageAssembly inAssembly) throws MbException {
		MbOutputTerminal out = getOutputTerminal("out");
		
		MbMessage inMessage = inAssembly.getMessage();
		MbMessageAssembly outAssembly = null;
		try {
			// create new message as a copy of the input
			//MbMessage outMessage = new MbMessage(inMessage);
			//outAssembly = new MbMessageAssembly(inAssembly, outMessage);
			// ----------------------------------------------------------
			// Add user code below

			//Copy local environment
			MbMessage env = inAssembly.getLocalEnvironment();
			MbMessage newEnv = new MbMessage(env);

			//Add wildcard to local environment
			String wildCardValue = "java_";
			LocalDateTime now = LocalDateTime.now();
			wildCardValue = wildCardValue + DateTimeFormatter.ofPattern("yyyyMMdd").format(now);
	        MbElement wildcard = newEnv.getRootElement().createElementAsLastChild(MbElement.TYPE_NAME, "Wildcard", null);
			wildcard.createElementAsFirstChild(MbElement.TYPE_NAME_VALUE, "WildcardMatch", wildCardValue);
			
			//Initialize output message
			MbMessage outMessage = new MbMessage();
			copyMessageHeaders(inMessage, outMessage);
			
			//Fill output message
			MbElement root = inMessage.getRootElement();
			MbElement inMqmd = root.getFirstElementByPath("MQMD");
			MbElement inPcf = root.getFirstElementByPath("MQPCF");
			
			MbElement outRoot = outMessage.getRootElement();
			MbElement outJsonRoot = outRoot.createElementAsLastChild(MbJSON.PARSER_NAME);
			MbElement outJsonData = outJsonRoot.createElementAsLastChild(MbElement.TYPE_NAME, MbJSON.DATA_ELEMENT_NAME, null);
			MbElement outJsonMessage = outJsonData.createElementAsLastChild(MbElement.TYPE_NAME, "Message", null);
			outJsonMessage.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Type", inPcf.getFirstElementByPath("Type").getValueAsString());
			outJsonMessage.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Version", inPcf.getFirstElementByPath("Version").getValueAsString());		
			String commandCode = inPcf.getFirstElementByPath("Command").getValueAsString();
			String command = MQConstants.lookup(commandCode, "MQCMD_.*");		
			outJsonMessage.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "CommandCode", commandCode);
			outJsonMessage.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Command", command);
			outJsonMessage.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "MsgSeqNumber", inPcf.getFirstElementByPath("MsgSeqNumber").getValueAsString());
			outJsonMessage.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Control", inPcf.getFirstElementByPath("Control").getValueAsString());
			outJsonMessage.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "CompCode", inPcf.getFirstElementByPath("CompCode").getValueAsString());
			outJsonMessage.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Time", inMqmd.getFirstElementByPath("PutDate").getValueAsString() + " " + inMqmd.getFirstElementByPath("PutTime").getValueAsString());
			String reasonCode = inPcf.getFirstElementByPath("Reason").getValueAsString();
			String reasonText = MQConstants.lookupReasonCode(Integer.parseInt(reasonCode)) ;
			outJsonMessage.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "ReasonCode", reasonCode);
			outJsonMessage.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Reason", reasonText);
					
			MbElement jsonArray = outJsonMessage.createElementAsLastChild(MbJSON.ARRAY, "Inserts", null);
			
			//loop here
			//check if a group is present
			
			MbElement group = inPcf.getFirstElementByPath("Group");
			if (group != null) {
				handleGroups(inPcf, jsonArray);
			} else {
				handleParameters(inPcf,jsonArray);
			}
			
			//Build output assembly
			outAssembly = new MbMessageAssembly(
					inAssembly,
					newEnv,
					inAssembly.getExceptionList(),
					outMessage);		
			
			
			// End of user code
			// ----------------------------------------------------------
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
		}
		// The following should only be changed
		// if not propagating message to the 'out' terminal
		out.propagate(outAssembly);

	}
	
	public void handleGroups(MbElement inPcf, MbElement jsonArray) throws MbException {
		MbElement group = inPcf.getFirstElementByPath("Group");
		while(group != null)
		{
			handleParameters(group, jsonArray);
			group = group.getNextSibling();
		}
	}
	
	public void handleParameters(MbElement inPcf, MbElement jsonArray) throws MbException {
		MbElement parameter = inPcf.getFirstElementByPath("Parameter");
		while(parameter != null)
		{
			MbElement jsonArrayItem = jsonArray.createElementAsLastChild(MbElement.TYPE_NAME, MbJSON.ARRAY_ITEM_NAME, null);
			String key = parameter.getValueAsString();
			String value = parameter.getLastChild().getValueAsString();
			String translatedKey = MQConstants.lookup(key, "(MQCACF|MQBACF|MQIACF|MQCA)_.*");	//MQBACF  MQIACF  MQCA
			if (translatedKey == null || translatedKey.equals("") ) {
				jsonArrayItem.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, key, value);	
			} else {
				jsonArrayItem.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, translatedKey, value);
			}
			
			parameter = parameter.getNextSibling();
		}
	}
	
	public void copyMessageHeaders(MbMessage inMessage, MbMessage outMessage) throws MbException
	{
		MbElement outRoot = outMessage.getRootElement();
		MbElement header = inMessage.getRootElement().getFirstChild();

		while(header != null && header.getNextSibling() != null)
		{
			outRoot.addAsLastChild(header.copy());
			header = header.getNextSibling();
		}
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
