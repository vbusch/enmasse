/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.integration.amqp;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ConnectorService;
import org.apache.activemq.artemis.protocol.amqp.client.AMQPClientConnectionFactory;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientConnectionManager;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.BaseConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.qpid.proton.amqp.Symbol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Connector service for outgoing AMQP connections.
 */
public class AMQPConnectorService implements ConnectorService, BaseConnectionLifeCycleListener<ProtonProtocolManager> {
   private static final Symbol groupSymbol = Symbol.getSymbol("qd.route-container-group");
   private final String name;
   private final String host;
   private final int port;
   private final ActiveMQServer server;
   private volatile RemotingConnection connection;
   private final Executor closeExecutor = Executors.newSingleThreadExecutor();
   private final Executor threadPool = Executors.newFixedThreadPool(4);
   private final ScheduledExecutorService scheduledExecutorService;
   private final ProtonClientConnectionManager lifecycleHandler;
   private volatile boolean started = false;

   public AMQPConnectorService(String connectorName, String host, int port, String containerId, String groupId, Optional<SubscriberInfo> subscriberInfo, ActiveMQServer server, ScheduledExecutorService scheduledExecutorService) {
      this.name = connectorName;
      this.host = host;
      this.port = port;
      this.server = server;
      this.scheduledExecutorService = scheduledExecutorService;
      AMQPClientConnectionFactory factory = new AMQPClientConnectionFactory(server, containerId, Collections.singletonMap(groupSymbol, groupId), 5000);
      this.lifecycleHandler = new ProtonClientConnectionManager(factory, subscriberInfo.map(LinkInitiator::new));
   }

   @Override
   public void start() throws Exception {
      ActiveMQAMQPLogger.LOGGER.info("Starting connector");
      final Map<String, Object> config = new LinkedHashMap<>();
      config.put(TransportConstants.HOST_PROP_NAME, host);
      config.put(TransportConstants.PORT_PROP_NAME, port);

      ProtonClientProtocolManager protocolManager = new ProtonClientProtocolManager(new ProtonProtocolManagerFactory(), server);
      NettyConnector connector = new NettyConnector(config, lifecycleHandler, this, closeExecutor, threadPool, server.getScheduledPool(), protocolManager);
      connector.start();
      connector.createConnection();
      started = true;
   }

   @Override
   public void stop() throws Exception {
      started = false;
      ActiveMQAMQPLogger.LOGGER.info("Stopping connector");
      if (connection != null) {
         lifecycleHandler.stop();
      }
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public void connectionCreated(ActiveMQComponent component, Connection connection, ProtonProtocolManager protocol) {
      lifecycleHandler.connectionCreated(component, connection, protocol);
      this.connection = connection.getProtocolConnection();
   }

   @Override
   public void connectionDestroyed(Object connectionID) {
      lifecycleHandler.connectionDestroyed(connectionID);
      ActiveMQAMQPLogger.LOGGER.info("Connection destroyed, reconnecting");
      if (started) {
         scheduledExecutorService.schedule(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
               start();
               return true;
            }
         }, 5, TimeUnit.SECONDS);
      }
   }

   @Override
   public void connectionException(Object connectionID, ActiveMQException me) {
      ActiveMQAMQPLogger.LOGGER.info("Connection exception: " + me.getMessage());
      lifecycleHandler.connectionException(connectionID, me);
   }

   @Override
   public void connectionReadyForWrites(Object connectionID, boolean ready) {
      lifecycleHandler.connectionReadyForWrites(connectionID, ready);
   }
}
