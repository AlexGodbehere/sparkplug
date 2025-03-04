/* ******************************************************************************
 * Copyright (c) 2021, 2022 Ian Craggs
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Ian Craggs - initial implementation and documentation
 ****************************************************************************** */

package org.eclipse.sparkplug.tck.test.host;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.extension.sdk.api.packets.connect.ConnectPacket;
import com.hivemq.extension.sdk.api.packets.disconnect.DisconnectPacket;
import com.hivemq.extension.sdk.api.packets.general.Qos;
import com.hivemq.extension.sdk.api.packets.publish.PublishPacket;
import com.hivemq.extension.sdk.api.packets.subscribe.SubscribePacket;
import org.eclipse.sparkplug.tck.sparkplug.Sections;
import org.eclipse.sparkplug.tck.test.TCK;
import org.eclipse.sparkplug.tck.test.TCKTest;
import org.eclipse.sparkplug.tck.test.common.TopicConstants;
import org.eclipse.sparkplug.tck.test.common.Utils;
import org.jboss.test.audit.annotations.SpecAssertion;
import org.jboss.test.audit.annotations.SpecVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.eclipse.sparkplug.tck.test.common.Requirements.*;
import static org.eclipse.sparkplug.tck.test.common.Utils.TestStatus.DEATH_MESSAGE_RECEIVED;
import static org.eclipse.sparkplug.tck.test.common.Utils.setResult;

/**
 * This is the primary host Sparkplug session termination test.
 * <p>
 * We do know the host application id, but there is no requirement on the MQTT client id, which means the first that we
 * know we are dealing with the host application is the receipt of the STATE message.
 * <p>
 * Currently this test works if the first MQTT client to connect is the host application. To make it completely robust
 * means following all connect/subscribe/publish combinations and ruling out the ones that don't match. There could be
 * many in parallel.
 *
 * @author Ian Craggs, Anja Helmbrecht-Schaar
 */
@SpecVersion(
		spec = "sparkplug",
		version = "3.0.0-SNAPSHOT")
public class SessionTerminationTest extends TCKTest {
	private static final Logger logger = LoggerFactory.getLogger("Sparkplug");
	private final @NotNull Map<String, String> testResults = new HashMap<>();

	private final @NotNull List<String> testIds = List.of(
			ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DISCONNECT_INTENTIONAL,
			ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_TOPIC,
			ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_PAYLOAD, ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_QOS,
			ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_RETAINED);

	private final @NotNull TCK theTCK;
	private @NotNull String hostApplicationId;
	private Utils.TestStatus state = Utils.TestStatus.NONE;

	private @Nullable String hostClientId = null;

	public SessionTerminationTest(final @NotNull TCK aTCK, final @NotNull String[] params) {
		logger.info("Primary host {}: Parameters: {} ", getName(), Arrays.asList(params));
		theTCK = aTCK;

		if (params.length != 2) {
			String errmsg = "Parameters to host session termination test must be: hostApplicationId hostClientId";
			logger.error(errmsg);
			prompt(errmsg);
			throw new IllegalArgumentException(errmsg);
		}
		hostApplicationId = params[0];
		hostClientId = params[1];

		logger.info("Parameters are HostApplicationId: {}, HostClientId: {}", hostApplicationId, hostClientId);

	}

	@Override
	public void endTest(Map<String, String> results) {
		testResults.putAll(results);
		Utils.setEndTest(getName(), testIds, testResults);
		reportResults(testResults);
	}

	public String getName() {
		return "Host SessionTermination";
	}

	public String[] getTestIds() {
		return testIds.toArray(new String[0]);
	}

	public Map<String, String> getResults() {
		return testResults;
	}

	@Override
	public void connect(final @NotNull String clientId, final @NotNull ConnectPacket packet) {
	}

	@SpecAssertion(
			section = Sections.OPERATIONAL_BEHAVIOR_SPARKPLUG_HOST_APPLICATION_SESSION_TERMINATION,
			id = ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DISCONNECT_INTENTIONAL)
	@Override
	public void disconnect(final @NotNull String clientId, final @NotNull DisconnectPacket packet) {
		logger.info("Host - {} test - DISCONNECT - clientId: {}, state: {} ", getName(), clientId, state);

		if (clientId.equals(hostClientId)) {
			logger.debug("Check Req: {}:{}.", ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DISCONNECT_INTENTIONAL,
					OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DISCONNECT_INTENTIONAL);
			testResults.put(ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DISCONNECT_INTENTIONAL, setResult(
					state == DEATH_MESSAGE_RECEIVED, OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DISCONNECT_INTENTIONAL));
			theTCK.endTest();
		}
	}

	public void subscribe(final @NotNull String clientId, final @NotNull SubscribePacket packet) {
	}

	@Override
	public void publish(final @NotNull String clientId, final @NotNull PublishPacket packet) {
		logger.info("{} test - PUBLISH - topic: {}, clientId: {} ", getName(), packet.getTopic(), clientId);

		if (clientId.equals(hostClientId)) {
			if (checkDeathMessage(packet)) {
				state = DEATH_MESSAGE_RECEIVED;
			}
		}
	}

	@SpecAssertion(
			section = Sections.OPERATIONAL_BEHAVIOR_SPARKPLUG_HOST_APPLICATION_SESSION_TERMINATION,
			id = ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_TOPIC)
	@SpecAssertion(
			section = Sections.OPERATIONAL_BEHAVIOR_SPARKPLUG_HOST_APPLICATION_SESSION_TERMINATION,
			id = ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_PAYLOAD)
	@SpecAssertion(
			section = Sections.OPERATIONAL_BEHAVIOR_SPARKPLUG_HOST_APPLICATION_SESSION_TERMINATION,
			id = ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_QOS)
	@SpecAssertion(
			section = Sections.OPERATIONAL_BEHAVIOR_SPARKPLUG_HOST_APPLICATION_SESSION_TERMINATION,
			id = ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_RETAINED)
	private boolean checkDeathMessage(final @NotNull PublishPacket packet) {
		logger.info("Host - {} test - PUBLISH - topic: {}, checkDeathMessage state: {} ", getName(), packet.getTopic(),
				state);
		boolean overallResult = true;

		// Topic is STATE/{host_application_id}
		final boolean isStateTopic =
				(packet.getTopic().equals(TopicConstants.TOPIC_ROOT_STATE + "/" + hostApplicationId));
		overallResult &= isStateTopic;
		logger.debug("Check Req: {}:{}.", ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_TOPIC,
				OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_TOPIC);
		testResults.put(ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_TOPIC,
				setResult(isStateTopic, OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_TOPIC));

		// Payload exists
		final boolean isPayloadPresent = (packet.getPayload().isPresent());
		overallResult &= isPayloadPresent;
		logger.debug("Check Req: {}:{}.", ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_PAYLOAD,
				OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_PAYLOAD);
		testResults.put(ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_PAYLOAD,
				setResult(isPayloadPresent, OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_TOPIC));

		// Payload message exists
		boolean payloadIsOffline = isPayloadPresent
				&& "OFFLINE".equals(StandardCharsets.UTF_8.decode(packet.getPayload().get()).toString());
		overallResult &= payloadIsOffline;

		logger.debug("Check Req: {}:{}.", ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_PAYLOAD,
				OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_PAYLOAD);
		testResults.put(ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_PAYLOAD,
				setResult(payloadIsOffline, OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_PAYLOAD));

		// Will publish is QoS 1
		final boolean isWillQoS1 = (packet.getQos() == Qos.AT_LEAST_ONCE);
		overallResult &= isWillQoS1;

		logger.debug("Check Req: {}:{}.", ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_QOS,
				OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_QOS);
		testResults.put(ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_QOS,
				setResult(isStateTopic, OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_QOS));

		// Retain flag is set
		final boolean isRetain = (packet.getRetain());
		overallResult &= isRetain;

		logger.debug("Check Req: {}:{}.", ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_RETAINED,
				OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_RETAINED);
		testResults.put(ID_OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_RETAINED,
				setResult(isRetain, OPERATIONAL_BEHAVIOR_HOST_APPLICATION_DEATH_RETAINED));

		return overallResult;
	}

}