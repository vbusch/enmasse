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

package enmasse.controller;

import enmasse.controller.auth.AuthController;
import enmasse.controller.auth.CertManager;
import enmasse.controller.auth.SelfSignedCertManager;
import enmasse.controller.common.Kubernetes;
import enmasse.controller.common.KubernetesHelper;
import enmasse.controller.k8s.api.AddressSpaceApi;
import enmasse.controller.k8s.api.ConfigMapAddressSpaceApi;
import enmasse.controller.standard.StandardController;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.CertProvider;
import io.enmasse.address.model.Endpoint;
import io.enmasse.address.model.SecretCertProvider;
import io.enmasse.address.model.types.AddressSpaceType;
import io.enmasse.address.model.types.standard.StandardAddressSpaceType;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.vertx.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Controller extends AbstractVerticle {
    private final OpenShiftClient controllerClient;
    private final ControllerOptions options;
    private final Kubernetes kubernetes;

    public Controller(ControllerOptions options) throws Exception {
        this.controllerClient = new DefaultOpenShiftClient(new ConfigBuilder()
                .withMasterUrl(options.masterUrl())
                .withOauthToken(options.token())
                .withNamespace(options.namespace())
                .build());
        this.options = options;
        this.kubernetes = new KubernetesHelper(options.namespace(), controllerClient, options.templateDir());
    }

    @Override
    public void start(Future<Void> startPromise) {
        AddressSpaceApi addressSpaceApi = new ConfigMapAddressSpaceApi(vertx, controllerClient);

        if (!options.isMultiinstance() && !kubernetes.hasService("messaging")) {
            AddressSpaceType type = new StandardAddressSpaceType();
            AddressSpace.Builder builder = new AddressSpace.Builder()
                    .setName("default")
                    .setNamespace(kubernetes.getNamespace())
                    .setType(type)
                    .setPlan(type.getDefaultPlan());

            Optional<CertProvider> certProvider = options.certSecret().map(SecretCertProvider::new);

            options.messagingHost().ifPresent(host ->
                    appendEndpoint(certProvider, "messaging", "messaging", host));
            options.mqttHost().ifPresent(host ->
                    appendEndpoint(certProvider, "mqtt", "mqtt", host));
            options.consoleHost().ifPresent(host ->
                    appendEndpoint(certProvider, "console", "console", host));
            addressSpaceApi.createAddressSpace(builder.build());
        }

        CertManager certManager = SelfSignedCertManager.create(controllerClient);

        deployVerticles(startPromise,
                new Deployment(new AuthController(certManager, addressSpaceApi)),
                new Deployment(new StandardController(controllerClient, addressSpaceApi, kubernetes, options.isMultiinstance())),
//                new Deployment(new AMQPServer(kubernetes.getNamespace(), addressSpaceApi, options.port())),
                new Deployment(new HTTPServer(addressSpaceApi, options.certDir()), new DeploymentOptions().setWorker(true)));
    }

    private Endpoint appendEndpoint(Optional<CertProvider> certProvider, String name, String service, String host) {
        return new Endpoint.Builder()
                .setCertProvider(certProvider.orElse(null))
                .setName(name)
                .setService(service)
                .setHost(host)
                .build();
    }

    private void deployVerticles(Future<Void> startPromise, Deployment ... deployments) {
        List<Future> futures = new ArrayList<>();
        for (Deployment deployment : deployments) {
            Future<Void> promise = Future.future();
            futures.add(promise);
            vertx.deployVerticle(deployment.verticle, deployment.options, result -> {
                if (result.succeeded()) {
                    promise.complete();
                } else {
                    promise.fail(result.cause());
                }
            });
        }

        CompositeFuture.all(futures).setHandler(result -> {
            if (result.succeeded()) {
                startPromise.complete();
            } else {
                startPromise.fail(result.cause());
            }
        });
    }

    private static class Deployment {
        final Verticle verticle;
        final DeploymentOptions options;

        private Deployment(Verticle verticle) {
            this(verticle, new DeploymentOptions());
        }

        private Deployment(Verticle verticle, DeploymentOptions options) {
            this.verticle = verticle;
            this.options = options;
        }
    }

    public static void main(String args[]) {
        try {
            Vertx vertx = Vertx.vertx();
            vertx.deployVerticle(new Controller(ControllerOptions.fromEnv(System.getenv())));
        } catch (IllegalArgumentException e) {
            System.out.println(String.format("Unable to parse arguments: %s", e.getMessage()));
            System.exit(1);
        } catch (Exception e) {
            System.out.println("Error starting address controller: " + e.getMessage());
            System.exit(1);
        }
    }
}
