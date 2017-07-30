/*
 * Copyright 2016 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package enmasse.systemtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import enmasse.amqp.SyncRequestClient;
import io.fabric8.kubernetes.api.model.Pod;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestUtils {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void setReplicas(OpenShift openShift, Destination destination, int numReplicas, TimeoutBudget budget) throws InterruptedException {
        openShift.setDeploymentReplicas(destination.getGroup(), numReplicas);
        waitForNReplicas(openShift, destination.getGroup(), numReplicas, budget);
    }

    public static void waitForNReplicas(OpenShift openShift, String group, int expectedReplicas, TimeoutBudget budget) throws InterruptedException {
        boolean done = false;
        int actualReplicas = 0;
        do {
            List<Pod> pods = openShift.listPods(Collections.singletonMap("role", "broker"), Collections.singletonMap("cluster_id", group));
            actualReplicas = numReady(pods);
            Logging.log.info("Have " + actualReplicas + " out of " + pods.size() + " replicas. Expecting " + expectedReplicas);
            if (actualReplicas != pods.size() || actualReplicas != expectedReplicas) {
                Thread.sleep(5000);
            } else {
                done = true;
            }
        } while (budget.timeLeft() >= 0 && !done);

        if (!done) {
            throw new RuntimeException("Only " + actualReplicas + " out of " + expectedReplicas + " in state 'Running' before timeout");
        }
    }

    private static int numReady(List<Pod> pods) {
        int numReady = 0;
        for (Pod pod : pods) {
            if ("Running".equals(pod.getStatus().getPhase())) {
                numReady++;
            } else {
                Logging.log.info("POD " + pod.getMetadata().getName() + " in status : " + pod.getStatus().getPhase());
            }
        }
        return numReady;
    }

    public static void waitForExpectedPods(OpenShift client, int numExpected, TimeoutBudget budget) throws InterruptedException {
        List<Pod> pods = listRunningPods(client);
        while (budget.timeLeft() >= 0 && pods.size() != numExpected) {
            Thread.sleep(2000);
            pods = listRunningPods(client);
        }
        if (pods.size() != numExpected) {
            throw new IllegalStateException("Unable to find " + numExpected + " pods. Found : " + printPods(pods));
        }
    }

    public static String printPods(List<Pod> pods) {
        return pods.stream()
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.joining(","));
    }

    public static List<Pod> listRunningPods(OpenShift openShift) {
        return openShift.listPods().stream()
                .filter(pod -> pod.getStatus().getPhase().equals("Running"))
                .collect(Collectors.toList());
    }

    public static void waitForBrokerPod(OpenShift openShift, String group, TimeoutBudget budget) throws InterruptedException {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("role", "broker");

        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("cluster_id", group);


        int numReady = 0;
        List<Pod> pods = null;
        while (budget.timeLeft() >= 0 && numReady != 1) {
            pods = openShift.listPods(labels, annotations);
            numReady = numReady(pods);
            if (numReady != 1) {
                Thread.sleep(5000);
            }
        }
        if (numReady != 1) {
            throw new IllegalStateException("Unable to find broker pod for " + group + " within timeout. Found " + pods);
        }
    }

    public static void deploy(AddressApiClient apiClient, OpenShift openShift, TimeoutBudget budget, String instanceName, Destination ... destinations) throws Exception {
        apiClient.deploy(instanceName, destinations);
        Set<String> groups = new HashSet<>();
        for (Destination destination : destinations) {
            if (Destination.isQueue(destination) || Destination.isTopic(destination)) {
                waitForBrokerPod(openShift, destination.getGroup(), budget);
                if (!Destination.isTopic(destination)) {
                    waitForAddress(openShift, destination.getAddress(), budget);
                }
                groups.add(destination.getGroup());
            }
        }
        int expectedPods = openShift.getExpectedPods() + groups.size();
        Logging.log.info("Waiting for " + expectedPods + " pods");
        waitForExpectedPods(openShift, expectedPods, budget);
    }

    public static void waitForAddress(OpenShift openShift, String address, TimeoutBudget budget) throws Exception {
        ArrayNode root = mapper.createArrayNode();
        ObjectNode data = root.addObject();
        data.put("name", address);
        data.put("store_and_forward", true);
        data.put("multicast", false);
        String json = mapper.writeValueAsString(root);
        Message message = Message.Factory.create();
        message.setAddress("health-check");
        message.setSubject("health-check");
        message.setBody(new AmqpValue(json));

        int numConfigured = 0;
        List<Pod> agents = openShift.listPods(Collections.singletonMap("name", "ragent"));

        while (budget.timeLeft() >= 0 && numConfigured < agents.size()) {
            numConfigured = 0;
            for (Pod pod : agents) {
                SyncRequestClient client = new SyncRequestClient(pod.getStatus().getPodIP(), pod.getSpec().getContainers().get(0).getPorts().get(0).getContainerPort());
                Message response = client.request(message, budget.timeLeft(), TimeUnit.MILLISECONDS);
                AmqpValue value = (AmqpValue) response.getBody();
                if ((Boolean) value.getValue() == true) {
                    numConfigured++;
                }
            }
            Thread.sleep(1000);
        }
        if (numConfigured != agents.size()) {
            throw new IllegalStateException("Timed out while waiting for routers to be configured");
        }
    }

    public static List<String> generateMessages(String prefix, int numMessages) {
        return IntStream.range(0, numMessages).mapToObj(i -> prefix + i).collect(Collectors.toList());
    }

    public static List<String> generateMessages(int numMessages) {
        return generateMessages("testmessage", numMessages);
    }

    public static boolean resolvable(Endpoint endpoint) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            try {
                InetAddress[] addresses = Inet4Address.getAllByName(endpoint.getHost());
                return addresses.length > 0;
            } catch (Exception e) {
            }
            Thread.sleep(1000);
        }
        return false;
    }
}
