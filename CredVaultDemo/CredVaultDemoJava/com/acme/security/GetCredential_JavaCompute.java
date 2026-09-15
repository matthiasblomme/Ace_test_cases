package com.acme.security;

import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbCredential;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbJSON;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbUserException;

/**
 * Retrieves a vault credential and returns proof that the lookup succeeded,
 * without echoing any secret value. The credential TYPE and NAME can be
 * overridden per request with the X-Cred-Type and X-Cred-Name HTTP headers,
 * so different storage styles can be probed without rebuilding. When the
 * credential cannot be found, the node returns retrieved:false (HTTP 200)
 * rather than throwing, to make negative cases easy to observe.
 */
public class GetCredential_JavaCompute extends MbJavaComputeNode {

	private static final String DEFAULT_TYPE = "userdefined";
	private static final String DEFAULT_NAME = "blogOAuthCred";

	public void evaluate(MbMessageAssembly inAssembly) throws MbException {
		MbOutputTerminal out = getOutputTerminal("out");
		MbMessage inMessage = inAssembly.getMessage();

		String credType = readHeader(inMessage, "X-Cred-Type", DEFAULT_TYPE);
		String credName = readHeader(inMessage, "X-Cred-Name", DEFAULT_NAME);

		MbMessageAssembly outAssembly;
		try {
			MbCredential credential = MbCredential.getCredential(credType, credName);

			MbMessage outMessage = new MbMessage();
			outAssembly = new MbMessageAssembly(inAssembly, outMessage);
			MbElement root = outMessage.getRootElement();
			MbElement json = root.createElementAsLastChild(MbJSON.PARSER_NAME);
			MbElement data = json.createElementAsLastChild(MbJSON.OBJECT, MbJSON.DATA_ELEMENT_NAME, null);

			data.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "credentialType", credType);
			data.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "credentialName", credName);

			if (credential == null) {
				data.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "retrieved", Boolean.FALSE);
			} else {
				String username = credential.hasUsername() ? new String(credential.username()) : "";
				String clientId = credential.hasClientId() ? new String(credential.clientId()) : "";
				int clientSecretLength = credential.hasClientSecret() ? credential.clientSecret().length : 0;
				int passwordLength = credential.hasPassword() ? credential.password().length : 0;

				data.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "retrieved", Boolean.TRUE);
				data.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "username", username);
				data.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "clientId", clientId);
				data.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "clientSecretLength", Long.valueOf(clientSecretLength));
				data.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "passwordLength", Long.valueOf(passwordLength));
			}
		} catch (MbException e) {
			throw e;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new MbUserException(this, "evaluate()", "", "", e.toString(), null);
		}

		out.propagate(outAssembly);
	}

	private String readHeader(MbMessage msg, String headerName, String dflt) throws MbException {
		MbElement folder = findChild(msg.getRootElement(), "HTTPInputHeader");
		MbElement header = findChild(folder, headerName);
		if (header == null) {
			return dflt;
		}
		Object value = header.getValue();
		return value == null ? dflt : value.toString();
	}

	private MbElement findChild(MbElement parent, String name) throws MbException {
		if (parent == null) {
			return null;
		}
		MbElement child = parent.getFirstChild();
		while (child != null) {
			if (name.equalsIgnoreCase(child.getName())) {
				return child;
			}
			child = child.getNextSibling();
		}
		return null;
	}
}
