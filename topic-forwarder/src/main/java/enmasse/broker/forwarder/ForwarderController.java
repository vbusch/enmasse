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

package enmasse.broker.forwarder;

import enmasse.discovery.DiscoveryListener;
import enmasse.discovery.Host;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maintains a set of {@link Forwarder} instances from a given host based on discovered hosts.
 */
public class ForwarderController extends AbstractVerticle implements DiscoveryListener {
    private static final Logger log = LoggerFactory.getLogger(ForwarderController.class.getName());

    private final Map<Host, String> replicatedHosts = new HashMap<>();

    private final Host localHost;
    private final String address;
    private final long connectionRetryInterval = 5000;

    public ForwarderController(Host localHost, String address) {
        this.localHost = localHost;
        this.address = address;
    }

    @Override
    public void start() {
        vertx.createHttpServer()
                .requestHandler(request -> request.response().setStatusCode(HttpResponseStatus.OK.code()).end())
                .listen(8080);
    }

    @Override
    public synchronized void hostsChanged(Set<Host> hosts) {
        hosts.remove(localHost);

        log.debug("Hosts changed to " + hosts);
        createForwarders(hosts);
        deleteForwarders(hosts);
    }

    private void createForwarders(Set<Host> newHosts) {
        Set<Host> currentHosts = replicatedHosts.keySet();
        Set<Host> hostsToCreate = new HashSet<>(newHosts);
        hostsToCreate.removeAll(currentHosts);
        hostsToCreate.forEach(this::createForwarder);
    }

    private void deleteForwarders(Set<Host> newHosts) {
        Set<Host> hostsToRemove = new HashSet<>(replicatedHosts.keySet());
        hostsToRemove.removeAll(newHosts);
        hostsToRemove.forEach(this::deleteForwarder);
    }

    private void deleteForwarder(Host host) {
        String forwarder = replicatedHosts.remove(host);
        log.info("Deleting forwarder " + forwarder);
        assert (forwarder != null);
        vertx.undeploy(forwarder);
    }

    private void createForwarder(Host host) {
        Forwarder forwarder = new Forwarder(localHost.amqpEndpoint(), host.amqpEndpoint(), address, connectionRetryInterval);
        log.info("Creating forwarder " + forwarder);
        vertx.deployVerticle(forwarder, result -> {
            if (result.succeeded()) {
                replicatedHosts.put(host, result.result());
            }
        });
    }
}
