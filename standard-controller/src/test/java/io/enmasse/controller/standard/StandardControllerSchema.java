/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.controller.standard;

import io.enmasse.address.model.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StandardControllerSchema {

    private AddressSpacePlan plan;
    private AddressSpaceType type;
    private Schema schema;

    public StandardControllerSchema() {
        this(Arrays.asList(new ResourceAllowance("router", 0.0, 1.0),
                new ResourceAllowance("broker", 0.0, 3.0),
                new ResourceAllowance("aggregate", 0.0, 3.0)));

    }

    public StandardControllerSchema(List<ResourceAllowance> resourceAllowanceList) {
        plan = new AddressSpacePlan.Builder()
                .setName("plan1")
                .setResources(resourceAllowanceList)
                .setAddressSpaceType("standard")
                .setAddressPlans(Arrays.asList(
                        "small-anycast",
                        "small-queue",
                        "pooled-queue-larger",
                        "pooled-queue-small",
                        "pooled-queue-tiny",
                        "small-topic",
                        "small-subscription"
                ))
                .build();

        type = new AddressSpaceType.Builder()
                .setName("standard")
                .setDescription("standard")
                .setAddressSpacePlans(Arrays.asList(plan))
                .setAvailableEndpoints(Collections.singletonList(new EndpointSpec.Builder()
                        .setName("messaging")
                        .setService("messaging")
                        .setServicePort("amqps")
                        .build()))
                .setAddressTypes(Arrays.asList(
                        new AddressType.Builder()
                                .setName("anycast")
                                .setDescription("anycast")
                                .setAddressPlans(Arrays.asList(
                                        new AddressPlan.Builder()
                                        .setName("small-anycast")
                                        .setAddressType("anycast")
                                        .setRequestedResources(Arrays.asList(
                                                new ResourceRequest("router", 0.2000000000)))
                                        .build()))
                                .build(),
                        new AddressType.Builder()
                                .setName("queue")
                                .setDescription("queue")
                                .setAddressPlans(Arrays.asList(
                                        new AddressPlan.Builder()
                                                .setName("pooled-queue-large")
                                                .setAddressType("queue")
                                                .setRequestedResources(Arrays.asList(
                                                        new ResourceRequest("broker", 0.6)))
                                                .build(),
                                        new AddressPlan.Builder()
                                                .setName("pooled-queue-small")
                                                .setAddressType("queue")
                                                .setRequestedResources(Arrays.asList(
                                                        new ResourceRequest("broker", 0.1)))
                                                .build(),
                                        new AddressPlan.Builder()
                                                .setName("pooled-queue-tiny")
                                                .setAddressType("queue")
                                                .setRequestedResources(Arrays.asList(
                                                        new ResourceRequest("broker", 0.049)))
                                                .build(),
                                        new AddressPlan.Builder()
                                                .setName("small-queue")
                                                .setAddressType("queue")
                                                .setRequestedResources(Arrays.asList(
                                                        new ResourceRequest("router", 0.2),
                                                        new ResourceRequest("broker", 0.4)))
                                                .build(),
                                        new AddressPlan.Builder()
                                                .setName("large-queue")
                                                .setAddressType("queue")
                                                .setRequestedResources(Arrays.asList(
                                                        new ResourceRequest("router", 0.2),
                                                        new ResourceRequest("broker", 1.0)))
                                                .build(),
                                        new AddressPlan.Builder()
                                                .setName("xlarge-queue")
                                                .setAddressType("queue")
                                                .setRequestedResources(Arrays.asList(
                                                        new ResourceRequest("router", 0.2),
                                                        new ResourceRequest("broker", 2.0)))
                                                .build()))
                                .build(),
                        new AddressType.Builder()
                                .setName("topic")
                                .setDescription("topic")
                                .setAddressPlans(Arrays.asList(
                                        new AddressPlan.Builder()
                                                .setName("small-topic")
                                                .setAddressType("topic")
                                                .setRequestedResources(Arrays.asList(
                                                        new ResourceRequest("router", 0.1),
                                                        new ResourceRequest("broker", 0.2)))
                                                .build(),
                                        new AddressPlan.Builder()
                                                .setName("xlarge-topic")
                                                .setAddressType("topic")
                                                .setRequestedResources(Arrays.asList(
                                                        new ResourceRequest("router", 0.1),
                                                        new ResourceRequest("broker", 1.0)))
                                                .build()))
                                .build(),
                        new AddressType.Builder()
                                .setName("subscription")
                                .setDescription("subscription")
                                .setAddressPlans(Arrays.asList(
                                        new AddressPlan.Builder()
                                                .setName("small-subscription")
                                                .setAddressType("subscription")
                                                .setRequestedResources(Arrays.asList(
                                                        new ResourceRequest("router", 0.05),
                                                        new ResourceRequest("broker", 0.1)))
                                                .build()))
                                .build()))
                .build();

        schema = new Schema.Builder()
                .setAddressSpaceTypes(Arrays.asList(type))
                .setResourceDefinitions(Arrays.asList(
                        new ResourceDefinition.Builder()
                            .setName("router")
                            .build(),
                        new ResourceDefinition.Builder()
                            .setName("broker")
                            .build(),
                        new ResourceDefinition.Builder()
	                    .setName("broker-topic")
	                    .build()))
                .build();
    }

    public AddressSpacePlan getPlan() {
        return plan;
    }

    public AddressSpaceType getType() {
        return type;
    }

    public Schema getSchema() {
        return schema;
    }
}
