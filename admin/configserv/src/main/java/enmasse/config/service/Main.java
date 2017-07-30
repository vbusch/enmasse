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

package enmasse.config.service;

import enmasse.config.service.amqp.AMQPServer;
import enmasse.config.service.config.ConfigSubscriptionConfig;
import enmasse.config.service.model.ResourceDatabase;
import enmasse.config.service.kubernetes.KubernetesResourceDatabase;
import enmasse.config.service.podsense.PodSenseSubscriptionConfig;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.vertx.core.Vertx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Main entrypoint for configuration service with arg parsing.
 */
public class Main {

    public static void main(String [] args) {
        try {
            Map<String, String> env = System.getenv();
            String openshiftUri = String.format("https://%s:%s", getEnvOrThrow(env, "KUBERNETES_SERVICE_HOST"), getEnvOrThrow(env, "KUBERNETES_SERVICE_PORT"));
            String listenAddress = env.getOrDefault("CONFIGURATION_SERVICE_LISTEN_ADDRESS", "0.0.0.0");
            int listenPort = Integer.parseInt(env.getOrDefault("CONFIGURATION_SERVICE_LISTEN_PORT", "5672"));

            String namespace = getNamespace();

            Config config = new ConfigBuilder().withMasterUrl(openshiftUri).withOauthToken(getAuthenticationToken()).withNamespace(namespace).build();
            KubernetesClient client = new DefaultKubernetesClient(config);

            Map<String, ResourceDatabase> databaseMap = new LinkedHashMap<>();
            databaseMap.put("v1/addresses", new KubernetesResourceDatabase<>(client, new ConfigSubscriptionConfig()));
            databaseMap.put("podsense", new KubernetesResourceDatabase<>(client, new PodSenseSubscriptionConfig()));

            AMQPServer server = new AMQPServer(listenAddress, listenPort, databaseMap);

            Vertx vertx = Vertx.vertx();
            vertx.deployVerticle(server);
        } catch (IllegalArgumentException e) {
            System.out.println("Error parsing environment: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.out.println("Error running config service");
            System.exit(1);
        }
    }

    private static String getEnvOrThrow(Map<String, String> env, String envVar) {
        String var = env.get(envVar);
        if (var == null) {
            throw new IllegalArgumentException(String.format("Unable to find value for required environment var '%s'", envVar));
        }
        return var;
    }

    private static final String SERVICEACCOUNT_PATH = "/var/run/secrets/kubernetes.io/serviceaccount";

    private static String getNamespace() throws IOException {
        return readFile(new File(SERVICEACCOUNT_PATH, "namespace"));
    }

    private static String getAuthenticationToken() throws IOException {
        return readFile(new File(SERVICEACCOUNT_PATH, "token"));
    }

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()));
    }
}
