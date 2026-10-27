package test;

import static com.ibm.integration.test.v1.Matchers.nodeCallCountIs;
import static com.ibm.integration.test.v1.Matchers.terminalPropagateCountIs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ibm.integration.test.v1.NodeSpy;
import com.ibm.integration.test.v1.SpyObjectReference;
import com.ibm.integration.test.v1.TestMessageAssembly;
import com.ibm.integration.test.v1.TestSetup;
import com.ibm.integration.test.v1.exception.TestException;

public class FeedApplication_com_mbl_test_processFeed_Compute_0001_Test {

	@AfterEach
	public void cleanupTest() throws TestException {
		TestSetup.restoreAllMocks();
	}

	private NodeSpy computeSpy() throws TestException {
		return new NodeSpy(new SpyObjectReference().application("FeedApplication")
				.messageFlow("com.mbl.test.processFeed").node("Compute"));
	}

	@Test
	public void FeedApplication_com_mbl_test_processFeed_Compute_TestCase_001() throws TestException {
		NodeSpy nodeSpy = computeSpy();
		TestMessageAssembly input = new TestMessageAssembly();
		input.buildJSONMessage("{\"type\":\"feedRequest\",\"request\":{\"time\":\"2024-08-01 09:18:21\",\"id\":\"A1\"},"
				+ "\"data\":{\"cat\":\"tuna\",\"dog\":\"kibble\",\"chicken\":\"mealworm\"}}");

		nodeSpy.evaluate(input, true, "in");

		assertThat(nodeSpy, nodeCallCountIs(1));
		assertThat(nodeSpy, terminalPropagateCountIs("out1", 1));
		assertThat(nodeSpy, terminalPropagateCountIs("out", 1));
		assertThat(nodeSpy, terminalPropagateCountIs("failure", 0));

		TestMessageAssembly feed = nodeSpy.propagatedMessageAssembly("out1", 1);
		assertThat(feed.messagePath("JSON.Data.request").getValueAsString(), equalTo("feed"));
		assertThat(feed.messagePath("JSON.Data.feedCount").getValueAsString(), equalTo("3"));

		TestMessageAssembly reply = nodeSpy.propagatedMessageAssembly("out", 1);
		assertThat(reply.messagePath("JSON.Data.status").getValueAsString(), equalTo("processed"));
	}

	@Test
	public void FeedApplication_com_mbl_test_processFeed_Compute_TestCase_002() throws TestException {
		NodeSpy nodeSpy = computeSpy();
		TestMessageAssembly input = new TestMessageAssembly();
		input.buildJSONMessage("{\"type\":\"feedRequest\",\"request\":{\"time\":\"not a time\",\"id\":\"A2\"},\"data\":{}}");

		assertThrows(TestException.class, () -> nodeSpy.evaluate(input, true, "in"));
		assertThat(nodeSpy, terminalPropagateCountIs("out1", 0));
	}
}
