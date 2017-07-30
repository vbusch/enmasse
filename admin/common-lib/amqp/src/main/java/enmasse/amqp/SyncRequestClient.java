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

package enmasse.amqp;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.message.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A simple client for doing request-response over AMQP.
 */
public class SyncRequestClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final Vertx vertx;

    public SyncRequestClient(String host, int port) {
        this(host, port, Vertx.vertx());
    }

    public SyncRequestClient(String host, int port, Vertx vertx) {
        this.host = host;
        this.port = port;
        this.vertx = vertx;
    }

    public Message request(Message message, long timeout, TimeUnit timeUnit) throws InterruptedException, TimeoutException, ExecutionException {
        String address = message.getAddress();
        CompletableFuture<Message> response = new CompletableFuture<>();

        ProtonClient client = ProtonClient.create(vertx);
        client.connect(host, port, connectEvent -> {
            if (connectEvent.succeeded()) {
                ProtonConnection connection = connectEvent.result();
                connection.open();

                ProtonSender sender = connection.createSender(address);
                sender.openHandler(senderOpenEvent -> {
                    if (senderOpenEvent.succeeded()) {
                        ProtonReceiver receiver = connection.createReceiver(address);
                        Source source = new Source();
                        source.setDynamic(true);
                        receiver.setSource(source);
                        receiver.setPrefetch(1);
                        receiver.handler(((delivery, msg) -> {
                            response.complete(msg);

                            receiver.close();
                            sender.close();
                            connection.close();
                        }));

                        receiver.openHandler(receiverOpenEvent -> {
                            if (receiverOpenEvent.succeeded()) {
                                if (receiver.getRemoteSource() != null) {
                                    message.setReplyTo(receiver.getRemoteSource().getAddress());
                                }
                                sender.send(message);
                            } else {
                                response.completeExceptionally(receiverOpenEvent.cause());
                            }
                        });
                        receiver.open();
                    }
                });
                sender.open();
            } else {
                response.completeExceptionally(connectEvent.cause());
            }
        });
        return response.get(timeout, timeUnit);
    }

    @Override
    public void close() throws Exception {
        vertx.close();
    }
}
