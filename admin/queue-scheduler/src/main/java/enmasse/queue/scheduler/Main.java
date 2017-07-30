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

import io.vertx.core.Vertx;

public class Main {
    public static void main(String [] args) {
        Vertx vertx = Vertx.vertx();
        String configHost = getEnvOrThrow("CONFIGURATION_SERVICE_HOST");
        int configPort = Integer.parseInt(getEnvOrThrow("CONFIGURATION_SERVICE_PORT"));
        int listenPort = 55667;

        QueueScheduler scheduler = new QueueScheduler(
                connection -> Artemis.create(vertx, connection),
                listenPort);

        scheduler.setProtonSaslAuthenticatorFactory(new DummySaslAuthenticatorFactory());
        ConfigServiceClient configServiceClient = new ConfigServiceClient(configHost, configPort, scheduler);

        vertx.deployVerticle(configServiceClient);
        vertx.deployVerticle(scheduler);
    }

    private static String getEnvOrThrow(String env) {
        String value = System.getenv(env);
        if (value == null) {
            throw new IllegalArgumentException("No environment " + env + " defined");
        }
        return value;
    }
}
