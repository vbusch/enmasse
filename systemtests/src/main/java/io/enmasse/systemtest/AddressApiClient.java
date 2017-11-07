package io.enmasse.systemtest;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;

public class AddressApiClient {
    private final WebClient client;
    private final OpenShift openshift;
    private Endpoint endpoint;
    private final Vertx vertx;
    private final int initRetry = 10;
    private final String addressSpacesPath = "/v1/addressspaces";
    private final String addressPath = "/v1/addresses";

    public AddressApiClient(OpenShift openshift) throws InterruptedException {
        this.vertx = VertxFactory.create();
        this.client = WebClient.create(vertx);
        this.openshift = openshift;
        this.endpoint = openshift.getRestEndpoint();
    }

    public void close() {
        client.close();
        vertx.close();
    }


    public void createAddressSpace(AddressSpace addressSpace, String authServiceType) throws Exception {
        JsonObject config = new JsonObject();
        config.put("apiVersion", "v1");
        config.put("kind", "AddressSpace");

        JsonObject metadata = new JsonObject();
        metadata.put("name", addressSpace.getName());
        metadata.put("namespace", addressSpace.getNamespace());
        config.put("metadata", metadata);

        JsonObject spec = new JsonObject();
        spec.put("type", addressSpace.getType().toString().toLowerCase());
        config.put("spec", spec);

        JsonObject authService = new JsonObject();
        authService.put("type", authServiceType);
        spec.put("authenticationService", authService);

        Logging.log.info("Following payload will be used in POST request: " + config.toString());
        CompletableFuture<JsonObject> responsePromise = new CompletableFuture<>();

        doRequestNTimes(initRetry, () -> {
            client.post(endpoint.getPort(), endpoint.getHost(), addressSpacesPath)
                    .timeout(20_000)
                    .as(BodyCodec.jsonObject())
                    .sendJsonObject(config, ar -> {
                        if (ar.succeeded()) {
                            responsePromise.complete(responseHandler(ar));
                        } else {
                            Logging.log.warn("Error creating address space {}", addressSpace);
                            responsePromise.completeExceptionally(ar.cause());
                        }
                    });
            return responsePromise.get(30, TimeUnit.SECONDS);
        });
    }

    public void deleteAddressSpace(AddressSpace addressSpace) throws Exception {
        CompletableFuture<JsonObject> responsePromise = new CompletableFuture<>();
        doRequestNTimes(initRetry, () -> {
            client.delete(endpoint.getPort(), endpoint.getHost(), addressSpacesPath + "/" + addressSpace.getName())
                    .as(BodyCodec.jsonObject())
                    .timeout(20_000)
                    .send(ar -> {
                        if (ar.succeeded()) {
                            responsePromise.complete(responseHandler(ar));
                        } else {
                            Logging.log.warn("Error deleting address space {}", addressSpace);
                            responsePromise.completeExceptionally(ar.cause());
                        }
                    });
            return responsePromise.get(30, TimeUnit.SECONDS);
        });
    }

    /**
     * get address space by address space name vie rest api
     *
     * @param name name of address space
     * @return
     * @throws InterruptedException
     */
    public JsonObject getAddressSpace(String name) throws Exception {
        CompletableFuture<JsonObject> responsePromise = new CompletableFuture<>();
        return doRequestNTimes(initRetry, () -> {
            client.get(endpoint.getPort(), endpoint.getHost(), addressSpacesPath + "/" + name)
                    .as(BodyCodec.jsonObject())
                    .send(ar -> {
                        if (ar.succeeded()) {
                            responsePromise.complete(responseHandler(ar));
                        } else {
                            Logging.log.warn("Error when getting address space {}", name);
                            responsePromise.completeExceptionally(ar.cause());
                        }
                    });
            return responsePromise.get(30, TimeUnit.SECONDS);
        });
    }

    public Set<String> listAddressSpaces() throws Exception {
        Logging.log.info("Following HTTP request will be used for getting address space:" + addressSpacesPath +
                " with host: " + endpoint.getHost() + " with Port: " + endpoint.getPort());

        CompletableFuture<JsonObject> list = new CompletableFuture<>();
        Set<String> spaces = new HashSet<>();
        JsonArray items = doRequestNTimes(initRetry, () -> {
            client.get(endpoint.getPort(), endpoint.getHost(), addressSpacesPath)
                    .as(BodyCodec.jsonObject())
                    .timeout(20_000)
                    .send(ar -> {
                        if (ar.succeeded()) {
                            JsonObject object = responseHandler(ar);
                            list.complete(object);
                        } else {
                            Logging.log.warn("Failed listing address spaces", ar.cause());
                            list.completeExceptionally(ar.cause());
                        }
                    });
            return list.get(30, TimeUnit.SECONDS);
        }).getJsonArray("items");

        Logging.log.info("Following list of address spaces received" + items.toString());
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                spaces.add(items.getJsonObject(i).getJsonObject("metadata").getString("name"));
            }
        }
        return spaces;
    }

    /**
     * give you JsonObject with AddressesList or Address kind
     *
     * @param addressSpace name of instance, this is used only if isMultitenant is set to true
     * @param addressName  name of address
     * @return
     * @throws Exception
     */
    public JsonObject getAddresses(AddressSpace addressSpace, Optional<String> addressName) throws Exception {
        StringBuilder path = new StringBuilder();
        path.append(addressPath).append("/").append(addressSpace.getName());
        path.append(addressName.isPresent() ? addressName.get() : "");
        Logging.log.info("Following HTTP request will be used for getting address: " + path);

        return doRequestNTimes(initRetry, () -> {
            CompletableFuture<JsonObject> responsePromise = new CompletableFuture<>();
            client.get(endpoint.getPort(), endpoint.getHost(), path.toString())
                    .as(BodyCodec.jsonObject())
                    .timeout(20_000)
                    .send(ar -> {
                        if (ar.succeeded()) {
                            responsePromise.complete(responseHandler(ar));
                        } else {
                            Logging.log.warn("Error when getting addresses");
                            responsePromise.completeExceptionally(ar.cause());
                        }
                    });
            return responsePromise.get(30, TimeUnit.SECONDS);
        });
    }

    /**
     * delete addresses via reset api
     *
     * @param addressSpace address space
     * @param destinations variable count of destinations that you can delete
     * @throws Exception
     */
    public void deleteAddresses(AddressSpace addressSpace, Destination... destinations) throws Exception {
        StringBuilder path = new StringBuilder();
        for (Destination destination : destinations) {
            path.append(addressPath).append("/").append(addressSpace.getName()).append("/").append(destination.getAddress());
            doDelete(path.toString(), destination.getAddress());
            path.setLength(0);
        }
    }

    private void doDelete(String path, String addressName) throws Exception {
        CompletableFuture<JsonObject> responsePromise = new CompletableFuture<>();
        doRequestNTimes(initRetry, () -> {
            client.delete(endpoint.getPort(), endpoint.getHost(), path)
                    .timeout(20_000)
                    .as(BodyCodec.jsonObject())
                    .send(ar -> {
                        if (ar.succeeded()) {
                            Logging.log.info("Address {} successfully removed", addressName);
                            responsePromise.complete(responseHandler(ar));
                        } else {
                            Logging.log.warn("Error during deleting addresses");
                            responsePromise.completeExceptionally(ar.cause());
                        }
                    });
            return responsePromise.get(30, TimeUnit.SECONDS);
        });
    }

    public void deploy(AddressSpace addressSpace, HttpMethod httpMethod, Destination... destinations) throws Exception {
        JsonObject config = new JsonObject();
        config.put("apiVersion", "v1");
        config.put("kind", "AddressList");
        JsonArray items = new JsonArray();
        for (Destination destination : destinations) {
            JsonObject entry = new JsonObject();
            JsonObject metadata = new JsonObject();
            metadata.put("name", destination.getAddress());
            metadata.put("addressSpace", addressSpace.getName());
            entry.put("metadata", metadata);

            JsonObject spec = new JsonObject();
            spec.put("address", destination.getAddress());
            spec.put("type", destination.getType());
            destination.getPlan().ifPresent(e -> spec.put("plan", e));
            entry.put("spec", spec);

            items.add(entry);
        }
        config.put("items", items);
        deploy(addressSpace, httpMethod, config);
    }

    /**
     * deploying addresses via rest api
     *
     * @param addressSpace name of instance
     * @param httpMethod   PUT, POST and DELETE method are supported
     * @throws Exception
     */
    private void deploy(AddressSpace addressSpace, HttpMethod httpMethod, JsonObject config) throws Exception {
        StringBuilder path = new StringBuilder();
        path.append(addressPath).append("/").append(addressSpace.getName()).append("/");

        Logging.log.info("Following HTTP request will be used for deploy: " + path);
        Logging.log.info("Following payload will be used in request: " + config.toString());
        CompletableFuture<JsonObject> responsePromise = new CompletableFuture<>();
        doRequestNTimes(initRetry, () -> {
            client.request(httpMethod, endpoint.getPort(), endpoint.getHost(), path.toString())
                    .timeout(20_000)
                    .as(BodyCodec.jsonObject())
                    .sendJsonObject(config, ar -> {
                        if (ar.succeeded()) {
                            responsePromise.complete(responseHandler(ar));
                        } else {
                            Logging.log.warn("Error when deploying addresses");
                            responsePromise.completeExceptionally(ar.cause());
                        }
                    });
            return responsePromise.get(30, TimeUnit.SECONDS);
        });
    }

    public JsonObject responseAddressHandler(JsonObject responseData) throws AddressAlreadyExistsException {
        if (responseData != null) {
            String errMsg = responseData.getString("error");
            switch (errMsg) {
                case "Address already exists":
                    throw new AddressAlreadyExistsException(errMsg);
            }
        }
        return responseData;
    }

    private JsonObject responseHandler(AsyncResult<HttpResponse<JsonObject>> ar) {
        try {
            return ar.result().body();
        } catch (io.vertx.core.json.DecodeException decEx) {
            if (ar.result().bodyAsString().toLowerCase().contains("application is not available")) {
                Logging.log.warn("Address-controller is not available.", ar.cause());
                throw new IllegalStateException("Address-controller is not available.");
            } else {
                Logging.log.warn("Unexpected object received", ar.cause());
                throw new IllegalStateException("JsonObject expected, but following object was received: " + ar.result().bodyAsString());
            }
        }
    }

    JsonObject doRequestNTimes(int retry, Callable<JsonObject> fn) throws Exception {
        return TestUtils.doRequestNTimes(retry, () -> {
            endpoint = openshift.getRestEndpoint();
            return fn.call();
        });
    }
}
