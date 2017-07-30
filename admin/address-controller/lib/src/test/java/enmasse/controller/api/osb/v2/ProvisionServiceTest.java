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

package enmasse.controller.api.osb.v2;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import enmasse.controller.api.osb.v2.lastoperation.LastOperationResponse;
import enmasse.controller.api.osb.v2.lastoperation.LastOperationState;
import enmasse.controller.api.osb.v2.provision.ProvisionRequest;
import enmasse.controller.api.osb.v2.provision.ProvisionResponse;
import io.enmasse.address.model.Address;
import io.enmasse.address.model.Status;
import io.enmasse.address.model.types.standard.StandardType;
import org.jboss.resteasy.util.HttpResponseCodes;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;

public class ProvisionServiceTest extends OSBTestBase {

    public static final String QUEUE_SERVICE_ID_STRING = QUEUE_SERVICE_ID.toString();
    public static final String QUEUE_PLAN_ID_STRING = QUEUE_PLAN_ID.toString();

    public static final String ADDRESS = "my-queue";
    public static final String TRANSACTIONAL = "transactional";

    @Test(expected = UnprocessableEntityException.class)
    public void testSyncProvisioningRequest() throws Exception {
        provisioningService.provisionService("123", false, new ProvisionRequest(QUEUE_SERVICE_ID, QUEUE_PLAN_ID, ORGANIZATION_ID, SPACE_ID));
    }

    @Test(expected = BadRequestException.class)
    public void testInvalidServiceUuid() throws Exception {
        provisioningService.provisionService("123", true, new ProvisionRequest(QUEUE_SERVICE_ID, QUEUE_PLAN_ID, ORGANIZATION_ID, SPACE_ID));
    }

    @Test(expected = BadRequestException.class)
    public void testInvalidPlan() throws Exception {
        provisioningService.provisionService("123", true, new ProvisionRequest(QUEUE_SERVICE_ID, TOPIC_PLAN_ID, ORGANIZATION_ID, SPACE_ID));
    }

    @Test(expected = BadRequestException.class)
    public void testInvalidServiceInstandeUuid() throws Exception {
        provisioningService.provisionService("123", true, new ProvisionRequest(QUEUE_SERVICE_ID, QUEUE_PLAN_ID, ORGANIZATION_ID, SPACE_ID));
    }

    @Test
    public void testProvision() throws Exception {
        ProvisionRequest provisionRequest = new ProvisionRequest(QUEUE_SERVICE_ID, QUEUE_PLAN_ID, ORGANIZATION_ID, SPACE_ID);
        provisionRequest.putParameter("name", ADDRESS);
        provisionRequest.putParameter("transactional", "true");
        Response response = provisioningService.provisionService(SERVICE_INSTANCE_ID, true, provisionRequest);
        ProvisionResponse provisionResponse = (ProvisionResponse) response.getEntity();

        assertThat(response.getStatus(), is(HttpResponseCodes.SC_ACCEPTED));
//        assertThat(provisionResponse.getDashboardUrl(), notNullValue());
        assertThat(provisionResponse.getOperation(), notNullValue());

        Address destination = new Address.Builder()
                .setName(ADDRESS)
                .setAddress(ADDRESS)
                .setAddressSpace("unknown")
                .setUuid(SERVICE_INSTANCE_ID)
                .setStatus(new Status(false))
                .setType(StandardType.QUEUE)
                .setPlan(StandardType.QUEUE.getPlans().get(0))
                .build();
        assertThat(instanceApi.getAddresses(), is(new HashSet<>(Collections.singletonList(destination))));

        LastOperationResponse lastOperationResponse = getLastOperationResponse(SERVICE_INSTANCE_ID, QUEUE_SERVICE_ID_STRING, QUEUE_PLAN_ID_STRING, provisionResponse.getOperation());
        assertThat(lastOperationResponse.getState(), is(LastOperationState.IN_PROGRESS));

        instanceApi.setAllInstancesReady(true);

        lastOperationResponse = getLastOperationResponse(SERVICE_INSTANCE_ID, QUEUE_SERVICE_ID_STRING, QUEUE_PLAN_ID_STRING, provisionResponse.getOperation());
        assertThat(lastOperationResponse.getState(), is(LastOperationState.IN_PROGRESS));

        instanceApi.getAddressApis().iterator().next().setAllAddressesReady(true);

        lastOperationResponse = getLastOperationResponse(SERVICE_INSTANCE_ID, QUEUE_SERVICE_ID_STRING, QUEUE_PLAN_ID_STRING, provisionResponse.getOperation());
        assertThat(lastOperationResponse.getState(), is(LastOperationState.SUCCEEDED));

        destination = new Address.Builder()
                .setName(ADDRESS)
                .setAddress(ADDRESS)
                .setAddressSpace("unknown")
                .setUuid(SERVICE_INSTANCE_ID)
                .setStatus(new Status(true))
                .setType(StandardType.QUEUE)
                .setPlan(StandardType.QUEUE.getPlans().get(0))
                .build();

        assertThat(instanceApi.getAddresses(), is(new HashSet<>(Collections.singletonList(destination))));
    }

    private LastOperationResponse getLastOperationResponse(String serviceInstanceId, String serviceId, String planId, String operation) throws Exception {
        Response response = lastOperationService.getLastOperationStatus(serviceInstanceId, serviceId, planId, operation);
        return (LastOperationResponse) response.getEntity();
    }


    @Test
    public void testProvisionTwiceWithDifferentPrameters() throws Exception {
        provisioningService.provisionService(SERVICE_INSTANCE_ID, true, new ProvisionRequest(QUEUE_SERVICE_ID, QUEUE_PLAN_ID, ORGANIZATION_ID, SPACE_ID));
        exceptionGrabber.expect(ConflictException.class);
        provisioningService.provisionService(SERVICE_INSTANCE_ID, true, new ProvisionRequest(ServiceType.TOPIC.uuid(), TOPIC_PLAN_ID, ORGANIZATION_ID, SPACE_ID));
    }

    @Test
    public void testProvisionTwiceWithSameParameters() throws Exception {
        ProvisionRequest provisionRequest = new ProvisionRequest(QUEUE_SERVICE_ID, QUEUE_PLAN_ID, ORGANIZATION_ID, SPACE_ID);
        provisioningService.provisionService(SERVICE_INSTANCE_ID, true, provisionRequest);
        Response response = provisioningService.provisionService(SERVICE_INSTANCE_ID, true, provisionRequest);
        assertThat(response.getStatus(), is(HttpResponseCodes.SC_OK));
    }

    @Test(expected = GoneException.class)
    public void testDeprovisionNonexistingServiceInstance() throws Exception {
        provisioningService.deprovisionService(SERVICE_INSTANCE_ID, QUEUE_SERVICE_ID_STRING, QUEUE_PLAN_ID_STRING);
    }

    @Test(expected = BadRequestException.class)
    public void testDeprovisionWithoutServiceId() throws Exception {
        provisioningService.deprovisionService(SERVICE_INSTANCE_ID, null, QUEUE_PLAN_ID_STRING);
    }

    @Test(expected = BadRequestException.class)
    public void testDeprovisionWithoutPlanId() throws Exception {
        provisioningService.deprovisionService(SERVICE_INSTANCE_ID, QUEUE_SERVICE_ID_STRING, null);
    }

    @Test
    public void testDeprovision() throws Exception {
        provisionService(SERVICE_INSTANCE_ID);
        Response response = provisioningService.deprovisionService(SERVICE_INSTANCE_ID, QUEUE_SERVICE_ID_STRING, QUEUE_PLAN_ID_STRING);
        assertThat(response.getStatus(), is(HttpResponseCodes.SC_OK));
        assertThat(instanceApi.getAddresses(), is(Collections.EMPTY_SET));
    }

    @Test
    public void testDeprovisionTwice() throws Exception {
        provisionService(SERVICE_INSTANCE_ID);
        provisioningService.deprovisionService(SERVICE_INSTANCE_ID, QUEUE_SERVICE_ID_STRING, QUEUE_PLAN_ID_STRING);

        exceptionGrabber.expect(GoneException.class);
        provisioningService.deprovisionService(SERVICE_INSTANCE_ID, QUEUE_SERVICE_ID_STRING, QUEUE_PLAN_ID_STRING);
    }

    @Test
    public void testDeprovisionGivenMultipleOrganizations() throws Exception {
        String serviceId11 = provisionService(randomUUID(), ORGANIZATION_ID, SPACE_ID);
        provisionService(randomUUID(), ORGANIZATION_ID, SPACE_ID);

        String organizationId2 = randomUUID();
        String spaceId2 = randomUUID();
        String serviceId21 = provisionService(randomUUID(), organizationId2, spaceId2);
        provisionService(randomUUID(), organizationId2, spaceId2);

        Response response = provisioningService.deprovisionService(serviceId21, QUEUE_SERVICE_ID_STRING, QUEUE_PLAN_ID_STRING);
        assertThat(response.getStatus(), is(HttpResponseCodes.SC_OK));
        assertThat(instanceApi.getAddressUuids(), not(hasItem(serviceId21)));

        response = provisioningService.deprovisionService(serviceId11, QUEUE_SERVICE_ID_STRING, QUEUE_PLAN_ID_STRING);
        assertThat(response.getStatus(), is(HttpResponseCodes.SC_OK));
        assertThat(instanceApi.getAddressUuids(), not(hasItem(serviceId11)));
    }

    private String randomUUID() {
        return UUID.randomUUID().toString();
    }


}
