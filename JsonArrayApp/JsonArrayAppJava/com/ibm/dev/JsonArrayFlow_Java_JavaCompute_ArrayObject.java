package com.ibm.dev;

import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbJSON;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbUserException;

public class JsonArrayFlow_Java_JavaCompute_ArrayObject extends MbJavaComputeNode {

	public void evaluate(MbMessageAssembly inAssembly) throws MbException {
		MbOutputTerminal out = getOutputTerminal("out");
		MbOutputTerminal alt = getOutputTerminal("alternate");

		MbMessage inMessage = inAssembly.getMessage();

		// create new empty message
		MbMessage outMessage = new MbMessage();
		MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly, outMessage);

		try {
			// optionally copy message headers
			copyMessageHeaders(inMessage, outMessage);
			// ----------------------------------------------------------
			// Add user code below
			MbElement outRoot = outMessage.getRootElement(); 
			MbElement outJsonRoot = outRoot.createElementAsLastChild(MbJSON.PARSER_NAME); 
			MbElement outJsonData = outJsonRoot.createElementAsLastChild(MbElement.TYPE_NAME, MbJSON.DATA_ELEMENT_NAME, null); 

			// Create a JSON array node under "objectArray" 
			MbElement objectArray = outJsonData.createElementAsLastChild(MbJSON.ARRAY, "objectArray", null); 

			// First object 
			MbElement firstItem = objectArray.createElementAsLastChild(MbElement.TYPE_NAME, MbJSON.ARRAY_ITEM_NAME, null); 
			firstItem.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "key1", "valueA"); 

			// Second object 
			MbElement secondItem = objectArray.createElementAsLastChild(MbElement.TYPE_NAME, MbJSON.ARRAY_ITEM_NAME, null); 
			secondItem.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "key2", "valueB"); 
			 
			// Third object 
			MbElement thirdItem = objectArray.createElementAsLastChild(MbElement.TYPE_NAME, MbJSON.ARRAY_ITEM_NAME, null); 
			thirdItem.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "key3", "valueC"); 
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

	public void copyMessageHeaders(MbMessage inMessage, MbMessage outMessage) throws MbException {
		MbElement outRoot = outMessage.getRootElement();

		// iterate though the headers starting with the first child of the root
		// element, stopping before the last child (body)
		MbElement header = inMessage.getRootElement().getFirstChild();
		while (header != null && header.getNextSibling() != null) {
			// copy the header and add it to the out message
			MbElement newHeader = outRoot.createElementAsLastChild(header.getParserClassName());
			newHeader.setName(header.getName());
			newHeader.copyElementTree(header);
			// move along to next header
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
