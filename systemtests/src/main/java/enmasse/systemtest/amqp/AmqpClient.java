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

package enmasse.systemtest.amqp;

import enmasse.systemtest.VertxFactory;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonQoS;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.message.Message;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AmqpClient implements AutoCloseable {
    private final enmasse.systemtest.Endpoint endpoint;
    private final TerminusFactory terminusFactory;
    private final List<Vertx> clients = new ArrayList<>();
    private final ProtonClientOptions protonClientOptions;
    private final ProtonQoS qos;

    public AmqpClient(enmasse.systemtest.Endpoint endpoint, TerminusFactory terminusFactory, ProtonClientOptions protonClientOptions) {
        this(endpoint, terminusFactory, protonClientOptions, ProtonQoS.AT_LEAST_ONCE);
    }

    public AmqpClient(enmasse.systemtest.Endpoint endpoint, TerminusFactory terminusFactory, ProtonClientOptions protonClientOptions, ProtonQoS qos) {
        this.endpoint = endpoint;
        this.terminusFactory = terminusFactory;
        this.protonClientOptions = protonClientOptions;
        this.qos = qos;
    }

    public Future<List<String>> recvMessages(String address, int numMessages) throws InterruptedException, IOException {
        return recvMessages(address, numMessages, 1, TimeUnit.MINUTES);
    }

    public Future<List<String>> recvMessages(String address, int numMessages, long connectTimeout, TimeUnit timeUnit) throws InterruptedException, IOException {
        return recvMessages(terminusFactory.getSource(address), numMessages, Optional.empty(), connectTimeout, timeUnit);
    }

    public Future<List<String>> recvMessages(Source source, String linkName, int numMessages) throws InterruptedException, IOException {
        return recvMessages(source, numMessages, Optional.of(linkName), 1, TimeUnit.MINUTES);
    }

    public Future<List<String>> recvMessages(Source source,String linkName, Predicate<Message> done) throws InterruptedException, IOException {
        return recvMessages(source, done, Optional.of(linkName), 1, TimeUnit.MINUTES);
    }

    public Future<List<String>> recvMessages(Source source, Predicate<Message> done, Optional<String> linkName, long connectTimeout, TimeUnit timeUnit) throws InterruptedException, IOException {
        CompletableFuture<List<String>> promise = new CompletableFuture<>();
        CountDownLatch connectLatch = new CountDownLatch(1);

        Vertx vertx = VertxFactory.create();
        vertx.deployVerticle(new Receiver(endpoint, done, promise, new ClientOptions(source, new Target(), protonClientOptions, linkName), connectLatch));
        clients.add(vertx);
        if (!connectLatch.await(connectTimeout, timeUnit)) {
            throw new RuntimeException("Timeout waiting for client to connect");
        }
        return promise;
    }

    @Override
    public void close() throws Exception {
        for (Vertx client : clients) {
            client.close();
        }
    }


    private static class Count implements Predicate<Message> {
        private final int expected;
        private int actual;

        Count(int expected) {
            this.expected = expected;
        }

        public boolean test(Message message) {
            return ++actual == expected;
        }
    }

    public Future<List<String>> recvMessages(Source source, int numMessages, Optional<String> linkName, long connectTimeout, TimeUnit timeUnit) throws InterruptedException, IOException {
        return recvMessages(source, new Count(numMessages), linkName, connectTimeout, timeUnit);
    }

    public Future<Integer> sendMessages(String address, List<String> messages) throws IOException, InterruptedException {
        Message [] messageList = messages.stream()
                .map(body -> {
                    Message message = Message.Factory.create();
                    message.setBody(new AmqpValue(body));
                    message.setAddress(address);
                    return message;
                })
                .collect(Collectors.toList()).toArray(new Message[0]);
        return sendMessages(address, messageList);
    }

    public Future<Integer> sendMessages(String address, Message ... messages) throws IOException, InterruptedException {
        return sendMessages(address, 1, TimeUnit.MINUTES, messages);
    }

    public Future<Integer> sendMessages(String address, long connectTimeout, TimeUnit timeUnit, Message ... messages) throws IOException, InterruptedException {

        CompletableFuture<Integer> promise = new CompletableFuture<>();
        CountDownLatch connectLatch = new CountDownLatch(1);
        Queue<Message> messageQueue = new LinkedList<>(Arrays.asList(messages));
        Vertx vertx = VertxFactory.create();
        vertx.deployVerticle(new Sender(endpoint, new ClientOptions(terminusFactory.getSource(address), terminusFactory.getTarget(address), protonClientOptions, Optional.empty()), connectLatch, promise, messageQueue, qos));
        clients.add(vertx);
        if (!connectLatch.await(connectTimeout, timeUnit)) {
            throw new RuntimeException("Timeout waiting for client to connect");
        }
        return promise;
    }
}
