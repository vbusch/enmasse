package io.enmasse.systemtest.brokered;

import io.enmasse.systemtest.AddressSpace;
import io.enmasse.systemtest.AddressSpaceType;
import io.enmasse.systemtest.Destination;
import io.enmasse.systemtest.MultiTenantTestBase;
import io.enmasse.systemtest.amqp.AmqpClient;
import io.enmasse.systemtest.amqp.AmqpClientFactory;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class QueueTest extends MultiTenantTestBase {

    /**
     * related github issue: #387
     */
    @Test
    public void messageGroupTest() throws Exception {
        AddressSpace addressSpace = new AddressSpace(
                "brokered-message-group",
                "brokered-message-group",
                AddressSpaceType.BROKERED);
        createAddressSpace(addressSpace, "none");
        Destination dest = Destination.queue("messageGroupQueue");
        setAddresses(addressSpace, dest);

        AmqpClientFactory amqpFactory = createAmqpClientFactory(addressSpace);
        AmqpClient client = amqpFactory.createQueueClient(addressSpace);
        client.getConnectOptions().setUsername("test").setPassword("test");

        int msgsCount = 20;
        int msgCountGroupA = 15;
        int msgCountGroupB = 5;
        List<Message> listOfMessages = new ArrayList<>();
        for (int i = 0; i < msgsCount; i++) {
            Message msg = Message.Factory.create();
            msg.setAddress(dest.getAddress());
            msg.setBody(new AmqpValue(dest.getAddress()));
            msg.setSubject("subject");
            msg.setGroupId(((i + 1) % 4 != 0) ? "group A" : "group B");
            listOfMessages.add(msg);
        }

        Future<List<Message>> receivedGroupA = client.recvMessages(dest.getAddress(), msgCountGroupA);
        Future<List<Message>> receivedGroupB = client.recvMessages(dest.getAddress(), msgCountGroupB);
        Thread.sleep(2000);

        Future<Integer> sent = client.sendMessages(dest.getAddress(),
                listOfMessages.toArray(new Message[listOfMessages.size()]));

        assertThat(sent.get(1, TimeUnit.MINUTES), is(msgsCount));
        assertThat(receivedGroupA.get(1, TimeUnit.MINUTES).size(), is(msgCountGroupA));
        assertThat(receivedGroupB.get(1, TimeUnit.MINUTES).size(), is(msgCountGroupB));

        for (Message m : receivedGroupA.get()) {
            assertEquals(m.getGroupId(), "group A");
        }

        for (Message m : receivedGroupB.get()) {
            assertEquals(m.getGroupId(), "group B");
        }
    }
}
