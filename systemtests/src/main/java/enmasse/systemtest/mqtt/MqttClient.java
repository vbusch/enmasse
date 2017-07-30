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

package enmasse.systemtest.mqtt;

import enmasse.systemtest.Endpoint;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class MqttClient implements AutoCloseable {

    private final Endpoint endpoint;
    private final List<ClientHandlerBase> clients = new ArrayList<>();
    private final MqttConnectOptions options;

    public MqttClient(Endpoint endpoint, final MqttConnectOptions options) {
        this.endpoint = endpoint;
        this.options = options;
    }

    public Future<List<String>> recvMessages(String topic, int numMessages) throws ExecutionException, InterruptedException {
        return this.recvMessages(topic, numMessages, 0,  1, TimeUnit.MINUTES);
    }

    public Future<List<String>> recvMessages(String topic, int numMessages, int qos) throws ExecutionException, InterruptedException {
        return this.recvMessages(topic, numMessages, qos, 1, TimeUnit.MINUTES);
    }

    public Future<Integer> sendMessages(String topic, List<String> messages) throws InterruptedException {
        return this.sendMessages(topic, messages, Collections.nCopies(messages.size(), 0), 1, TimeUnit.MINUTES);
    }

    public Future<Integer> sendMessages(String topic, List<String> messages, List<Integer> qos) throws InterruptedException {
        return this.sendMessages(topic, messages, qos, 1, TimeUnit.MINUTES);
    }

    public Future<List<String>> recvMessages(String topic, int numMessages, int qos, long connectTimeout, TimeUnit timeUnit) throws ExecutionException, InterruptedException {

        CompletableFuture<List<String>> promise = new CompletableFuture<>();
        CountDownLatch connectLatch = new CountDownLatch(1);

        Subscriber subscriber = new Subscriber(this.endpoint, this.options, topic, qos, new Count(numMessages), promise, connectLatch);
        subscriber.start();

        this.clients.add(subscriber);
        if (!connectLatch.await(connectTimeout, timeUnit)) {
            throw new RuntimeException("Timeout waiting for client to connect");
        }
        if (promise.isCompletedExceptionally()) {
            promise.get();
        }
        return promise;
    }

    public Future<Integer> sendMessages(String topic, List<String> messages, List<Integer> qos, long connectTimeout, TimeUnit timeUnit) throws InterruptedException {

        if (messages.size() != qos.size())
            throw new IllegalArgumentException("Number of messages and qos don't match");

        Iterator<String> messageIterator = messages.iterator();
        Iterator<Integer> qosIterator = qos.iterator();

        Queue<MqttMessage> messageQueue = new LinkedList<>();
        int messageId = 0;

        while (messageIterator.hasNext() && qosIterator.hasNext()) {
            MqttMessage message = new MqttMessage();
            message.setId(++messageId);
            message.setPayload(messageIterator.next().getBytes());
            message.setQos(qosIterator.next());
            messageQueue.add(message);
        }

        CompletableFuture<Integer> promise = new CompletableFuture<>();
        CountDownLatch connectLatch = new CountDownLatch(1);

        Publisher publisher = new Publisher(this.endpoint, this.options, topic, messageQueue, promise, connectLatch);
        publisher.start();

        this.clients.add(publisher);
        if (!connectLatch.await(connectTimeout, timeUnit)) {
            throw new RuntimeException("Timeout waiting for client to connect");
        }
        return promise;
    }

    @Override
    public void close() throws Exception {

        for (ClientHandlerBase client : this.clients) {
            client.close();
        }
    }

    private static class Count implements Predicate<MqttMessage> {

        private final int expected;
        private int actual;

        Count(int expected) {
            this.expected = expected;
        }

        @Override
        public boolean test(MqttMessage mqttMessage) {
            return ++actual == expected;
        }
    }
}
