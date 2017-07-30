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

package enmasse.queue.scheduler;

import io.enmasse.address.model.Address;
import io.vertx.core.AbstractVerticle;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonServer;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;
import org.apache.qpid.proton.amqp.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Acts as an arbiter deciding in which broker a queue should run.
 */
public class QueueScheduler extends AbstractVerticle implements ConfigListener {
    private static final Logger log = LoggerFactory.getLogger(QueueScheduler.class.getName());
    private static final Symbol groupSymbol = Symbol.getSymbol("qd.route-container-group");

    private final SchedulerState schedulerState = new SchedulerState();
    private final BrokerFactory brokerFactory;
    private ProtonSaslAuthenticatorFactory saslAuthenticatorFactory;
    private volatile ProtonServer server;

    private final int port;

    public QueueScheduler(BrokerFactory brokerFactory, int listenPort) {
        this.brokerFactory = brokerFactory;
        this.port = listenPort;
    }

    // This is a temporary hack until Artemis can support sasl anonymous
    public void setProtonSaslAuthenticatorFactory(ProtonSaslAuthenticatorFactory saslAuthenticatorFactory) {
        this.saslAuthenticatorFactory = saslAuthenticatorFactory;
    }

    private static String getGroupId(ProtonConnection connection) {
        Map<Symbol, Object> connectionProperties = connection.getRemoteProperties();
        if (connectionProperties.containsKey(groupSymbol)) {
            return (String) connectionProperties.get(groupSymbol);
        } else {
            return connection.getRemoteContainer();
        }
    }

    @Override
    public void start() {
        server = ProtonServer.create(vertx);
        server.saslAuthenticatorFactory(saslAuthenticatorFactory);
        server.connectHandler(connection -> {
            connection.setContainer("queue-scheduler");
            connection.openHandler(result -> {
                connectionOpened(connection);
            }).closeHandler(conn -> {
                log.info("Broker connection " + connection.getRemoteContainer() + " closed");
                executeBlocking(() -> schedulerState.brokerRemoved(getGroupId(connection), connection.getRemoteContainer()),
                        "Error removing broker");
                connection.close();
                connection.disconnect();
            }).disconnectHandler(protonConnection -> {
                log.info("Broker connection " + connection.getRemoteContainer() + " disconnected");
                executeBlocking(() -> schedulerState.brokerRemoved(getGroupId(connection), connection.getRemoteContainer()),
                        "Error removing broker");
                connection.disconnect();
            });

            if (connection.getRemoteContainer() != null) {
                connectionOpened(connection);
            }

            connection.open();
        });
        server.listen(port, event -> {
            if (event.succeeded()) {
                log.info("QueueScheduler is up and running");
            } else {
                log.error("Error starting queue scheduler", event.cause());
            }
        });
    }

    private void connectionOpened(ProtonConnection connection) {
        log.info("Connection opened from " + connection.getRemoteContainer());
        Future<Broker> broker = brokerFactory.createBroker(connection);
        executeBlocking(() -> schedulerState.brokerAdded(getGroupId(connection), connection.getRemoteContainer(), broker.get(30, TimeUnit.SECONDS)),"Error adding broker");
    }

    @Override
    public void stop() {
        log.info("Stopping server!");
        if (server != null) {
            server.close();
        }
    }

    @Override
    public void addressesChanged(Map<String, Set<Address>> addressMap) {
        executeBlocking(() -> schedulerState.addressesChanged(addressMap), "Error handling address change");
    }

    private void executeBlocking(Task task, String errorMessage) {
        vertx.executeBlocking(promise -> {
            try {
                task.run();
                promise.complete();
            } catch (Exception e) {
                promise.fail(e);
            }
        }, true, result -> {
            if (result.failed()) {
                log.error(errorMessage, result.cause());
            }
        });
    }

    private interface Task {
        void run() throws Exception;
    }

    public int getPort() {
        if (server == null) {
            return 0;
        } else {
            return server.actualPort();
        }
    }
}
