/*
 * Copyright 2017 Red Hat Inc.
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
package io.enmasse.address.model.v1.address;

import io.enmasse.address.model.*;
import io.enmasse.address.model.Endpoint;
import io.enmasse.address.model.types.standard.StandardAddressSpaceType;
import io.enmasse.address.model.types.standard.StandardType;
import io.enmasse.address.model.v1.CodecV1;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class SerializationTest {

    @Test
    public void testSerializeAddress() throws IOException {
        String uuid = UUID.randomUUID().toString();
        Address address = new Address.Builder()
                .setName("a1")
                .setAddress("addr1")
                .setType(StandardType.QUEUE)
                .setPlan(StandardType.QUEUE.getPlans().get(1))
                .setUuid(uuid)
                .build();

        byte [] serialized = CodecV1.getMapper().writeValueAsBytes(address);

        Address deserialized = CodecV1.getMapper().readValue(serialized, Address.class);

        assertThat(deserialized, is(address));
        assertThat(deserialized.getName(), is(address.getName()));
        assertThat(deserialized.getAddressSpace(), is(address.getAddressSpace()));
        assertThat(deserialized.getType(), is(address.getType()));
        assertThat(deserialized.getUuid(), is(address.getUuid()));
        assertThat(deserialized.getPlan().getName(), is(address.getPlan().getName()));
        assertThat(deserialized.getAddress(), is(address.getAddress()));
    }

    @Test
    public void testDeserializeAddressWithDefaults() throws IOException {
        String json = "{" +
                "\"apiVersion\":\"v1\"," +
                "\"kind\":\"Address\"," +
                "\"metadata\":{" +
                "  \"name\":\"myqueue\"" +
                "}," +
                "\"spec\": {" +
                "  \"type\":\"queue\"" +
                "}" +
                "}";

        Address address = CodecV1.getMapper().readValue(json, Address.class);
        assertThat(address.getName(), is("myqueue"));
        assertThat(address.getAddress(), is("myqueue"));
        assertThat(address.getAddressSpace(), is("default"));
        assertThat(address.getUuid(), is(not("")));
        assertThat(address.getPlan().getName(), is(StandardType.QUEUE.getDefaultPlan().getName()));
    }

    @Test
    public void testSerializeAddressList() throws IOException {
        Address addr1 = new Address.Builder()
                .setName("addr1")
                .setType(StandardType.QUEUE)
                .build();

        Address addr2 = new Address.Builder()
                .setName("a2")
                .setAddress("addr2")
                .setType(StandardType.ANYCAST)
                .build();


        AddressList list = new AddressList(Sets.newSet(addr1, addr2));

        String serialized = CodecV1.getMapper().writeValueAsString(list);
        List<Address> deserialized = CodecV1.getMapper().readValue(serialized, AddressList.class);

        assertThat(deserialized, is(list));
    }

    @Test
    public void testSerializeAddressSpace() throws IOException {
        AddressSpace addressSpace = new AddressSpace.Builder()
                .setName("myspace")
                .setNamespace("mynamespace")
                .setPlan(new StandardAddressSpaceType().getDefaultPlan())
                .setType(new StandardAddressSpaceType())
                .setStatus(new Status(true).appendMessage("hello"))
                .setEndpointList(Arrays.asList(new Endpoint.Builder()
                        .setName("myendpoint")
                        .setService("messaging")
                        .setCertProvider(new CertProvider("secret", "mysecret"))
                        .build()))
                .build();

        String serialized = CodecV1.getMapper().writeValueAsString(addressSpace);
        AddressSpace deserialized = CodecV1.getMapper().readValue(serialized, AddressSpace.class);

        assertThat(deserialized.getName(), is(addressSpace.getName()));
        assertThat(deserialized.getNamespace(), is(addressSpace.getNamespace()));
        assertThat(deserialized.getType().getName(), is(addressSpace.getType().getName()));
        assertThat(deserialized.getPlan().getName(), is(addressSpace.getPlan().getName()));
        assertThat(deserialized.getStatus().isReady(), is(addressSpace.getStatus().isReady()));
        assertThat(deserialized.getStatus().getMessages(), is(addressSpace.getStatus().getMessages()));
        assertThat(deserialized.getEndpoints().size(), is(addressSpace.getEndpoints().size()));
        assertThat(deserialized.getEndpoints().get(0).getName(), is(addressSpace.getEndpoints().get(0).getName()));
        assertThat(deserialized.getEndpoints().get(0).getService(), is(addressSpace.getEndpoints().get(0).getService()));
        assertThat(deserialized.getEndpoints().get(0).getCertProvider().get().getName(), is(addressSpace.getEndpoints().get(0).getCertProvider().get().getName()));
        assertThat(deserialized.getEndpoints().get(0).getCertProvider().get().getSecretName(), is(addressSpace.getEndpoints().get(0).getCertProvider().get().getSecretName()));
        assertThat(addressSpace, is(deserialized));
    }

    @Test
    public void testDeserializeAddressSpaceWithDefaults() throws IOException {
        String json = "{" +
                "\"apiVersion\":\"v1\"," +
                "\"kind\":\"AddressSpace\"," +
                "\"metadata\":{" +
                "  \"name\":\"myspace\"" +
                "}," +
                "\"spec\": {" +
                "  \"type\":\"standard\"" +
                "}" +
                "}";

        AddressSpace addressSpace = CodecV1.getMapper().readValue(json, AddressSpace.class);
        assertThat(addressSpace.getName(), is("myspace"));
        assertThat(addressSpace.getNamespace(), is("enmasse-myspace"));
        assertThat(addressSpace.getPlan().getName(), is(new StandardAddressSpaceType().getDefaultPlan().getName()));
    }

    @Test
    public void testSerializeAddressSpaceList() throws IOException {
        AddressSpace a1 = new AddressSpace.Builder()
                .setName("myspace")
                .setNamespace("mynamespace")
                .setPlan(new StandardAddressSpaceType().getDefaultPlan())
                .setType(new StandardAddressSpaceType())
                .setStatus(new Status(true).appendMessage("hello"))
                .setEndpointList(Arrays.asList(new Endpoint.Builder()
                        .setName("myendpoint")
                        .setService("messaging")
                        .build()))
                .build();

        AddressSpace a2 = new AddressSpace.Builder()
                .setName("mysecondspace")
                .setNamespace("myothernamespace")
                .setPlan(new StandardAddressSpaceType().getDefaultPlan())
                .setType(new StandardAddressSpaceType())
                .setStatus(new Status(false))
                .setEndpointList(Arrays.asList(new Endpoint.Builder()
                        .setName("bestendpoint")
                        .setService("mqtt")
                        .build()))
                .build();

        AddressSpaceList list = new AddressSpaceList();
        list.add(a1);
        list.add(a2);

        String serialized = CodecV1.getMapper().writeValueAsString(list);

        AddressSpaceList deserialized = CodecV1.getMapper().readValue(serialized, AddressSpaceList.class);

        assertAddressSpace(deserialized, a1);
        assertAddressSpace(deserialized, a2);
    }

    private void assertAddressSpace(AddressSpaceList deserialized, AddressSpace expected) {
        AddressSpace found = null;
        for (AddressSpace addressSpace : deserialized) {
            if (addressSpace.getName().equals(expected.getName())) {
                found = addressSpace;
                break;
            }

        }
        assertNotNull(found);

        assertThat(found.getName(), is(expected.getName()));
        assertThat(found.getNamespace(), is(expected.getNamespace()));
        assertThat(found.getType().getName(), is(expected.getType().getName()));
        assertThat(found.getPlan().getName(), is(expected.getPlan().getName()));
        assertThat(found.getStatus().isReady(), is(expected.getStatus().isReady()));
        assertThat(found.getStatus().getMessages(), is(expected.getStatus().getMessages()));
    }
}
