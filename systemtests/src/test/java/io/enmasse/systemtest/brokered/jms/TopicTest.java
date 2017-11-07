package io.enmasse.systemtest.brokered.jms;


import io.enmasse.systemtest.*;
import io.enmasse.systemtest.Destination;
import org.junit.*;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class TopicTest extends JMSTestBase {

    private AddressSpace addressSpace;

    private Hashtable<Object, Object> env;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Context context;
    private String topic = "jmsTopic";
    private Destination addressTopic;

    private String jmsUsername = "test";
    private String jmsPassword = "test";
    private String jmsClientID = "testClient";

    @Before
    public void setUp() throws Exception {
        addressSpace = new AddressSpace(
                "brokered-space-jms-topics",
                "brokered-space-jms-topics",
                AddressSpaceType.BROKERED);
        createAddressSpace(addressSpace, "none");

        addressTopic = Destination.topic(topic);
        setAddresses(addressSpace, addressTopic);

        env = setUpEnv("amqps://" + getRouteEndpoint(addressSpace).toString(), jmsUsername, jmsPassword, jmsClientID,
                new HashMap<String, String>() {{
                    put("topic." + topic, topic);
                }});
        context = new InitialContext(env);
        connectionFactory = (ConnectionFactory) context.lookup("qpidConnectionFactory");
        connection = connectionFactory.createConnection();
        connection.start();
    }

    @After
    public void tearDown() throws Exception {
        if (TestUtils.existAddressSpace(addressApiClient, addressSpace.getName())) {
            deleteAddresses(addressTopic);
        }
        if (connection != null) {
            connection.stop();
        }
        if (session != null) {
            session.close();
        }
        if (connection != null) {
            connection.close();
        }
    }

    protected Context createContextForShared() throws JMSException, NamingException {
        Hashtable env2 = setUpEnv("amqps://" + getRouteEndpoint(addressSpace).toString(), jmsUsername, jmsPassword,
                new HashMap<String, String>() {{
                    put("topic." + topic, topic);
                }});
        return new InitialContext(env2);
    }

    @Test
    public void testMessageSubscription() throws Exception {
        Logging.log.info("testMessageSubscription");
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic testTopic = (Topic) context.lookup(topic);
        MessageConsumer subscriber1 = session.createConsumer(testTopic);
        MessageProducer messageProducer = session.createProducer(testTopic);

        int count = 1000;
        List<Message> listMsgs = generateMessages(session, count);

        CompletableFuture<List<Message>> received = new CompletableFuture<>();

        List<Message> recvd = new ArrayList<>();
        AtomicInteger i = new AtomicInteger(0);
        MessageListener myListener = message -> {
            recvd.add(message);
            if (i.incrementAndGet() == count) {
                received.complete(recvd);
            }
        };
        subscriber1.setMessageListener(myListener);

        sendMessages(messageProducer, listMsgs);
        Logging.log.info("messages sent");

        assertThat(received.get(30, TimeUnit.SECONDS).size(), is(count));
        Logging.log.info("messages received");

        subscriber1.close();
        messageProducer.close();
    }

    @Test
    public void testMessageDurableSubscription() throws Exception {
        Logging.log.info("testMessageDurableSubscription");
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic testTopic = (Topic) context.lookup(topic);

        String sub1ID = "sub1";
        String sub2ID = "sub2";
        MessageConsumer subscriber1 = session.createDurableSubscriber(testTopic, sub1ID);
        MessageConsumer subscriber2 = session.createDurableSubscriber(testTopic, sub2ID);
        MessageProducer messageProducer = session.createProducer(testTopic);

        int count = 100;
        String batchPrefix = "First";
        List<Message> listMsgs = generateMessages(session, batchPrefix, count);
        sendMessages(messageProducer, listMsgs);
        Logging.log.info("First batch messages sent");

        List<Message> recvd1 = receiveMessages(subscriber1, count);
        List<Message> recvd2 = receiveMessages(subscriber2, count);

        assertThat(recvd1.size(), is(count));
        assertMessageContent(recvd1, batchPrefix);
        Logging.log.info(sub1ID + " :First batch messages received");

        assertThat(recvd2.size(), is(count));
        assertMessageContent(recvd2, batchPrefix);
        Logging.log.info(sub2ID + " :First batch messages received");

        subscriber1.close();
        Logging.log.info(sub1ID + " : closed");

        batchPrefix = "Second";
        listMsgs = generateMessages(session, batchPrefix, count);
        sendMessages(messageProducer, listMsgs);
        Logging.log.info("Second batch messages sent");

        recvd2 = receiveMessages(subscriber2, count);
        assertThat(recvd2.size(), is(count));
        assertMessageContent(recvd2, batchPrefix);
        Logging.log.info(sub2ID + " :Second batch messages received");

        subscriber1 = session.createDurableSubscriber(testTopic, sub1ID);
        Logging.log.info(sub1ID + " :connected");

        recvd1 = receiveMessages(subscriber1, count);
        assertThat(recvd1.size(), is(count));
        assertMessageContent(recvd1, batchPrefix);
        Logging.log.info(sub1ID + " :Second batch messages received");

        subscriber1.close();
        subscriber2.close();

        session.unsubscribe(sub1ID);
        session.unsubscribe(sub2ID);
    }

    @Test
    public void testMessageDurableSubscriptionTransacted() throws Exception {
        Logging.log.info("testMessageDurableSubscriptionTransacted");
        session = connection.createSession(true, Session.SESSION_TRANSACTED);
        Topic testTopic = (Topic) context.lookup(topic);

        String sub1ID = "sub1";
        String sub2ID = "sub2";
        MessageConsumer subscriber1 = session.createDurableSubscriber(testTopic, sub1ID);
        MessageConsumer subscriber2 = session.createDurableSubscriber(testTopic, sub2ID);
        MessageProducer messageProducer = session.createProducer(testTopic);

        int count = 100;
        List<Message> listMsgs = generateMessages(session, count);
        sendMessages(messageProducer, listMsgs);
        session.commit();
        Logging.log.info("messages sent");

        List<Message> recvd1 = receiveMessages(subscriber1, count);
        session.commit();
        List<Message> recvd2 = receiveMessages(subscriber2, count);
        session.commit();

        Logging.log.info(sub1ID + " :messages received");
        Logging.log.info(sub2ID + " :messages received");

        assertThat(recvd1.size(), is(count));
        assertThat(recvd2.size(), is(count));

        subscriber1.close();
        subscriber2.close();

        session.unsubscribe(sub1ID);
        session.unsubscribe(sub2ID);
    }

    @Test
    public void testSharedDurableSubscription() throws JMSException, NamingException {
        Logging.log.info("testSharedDurableSubscription");

        Context context1 = createContextForShared();
        ConnectionFactory connectionFactory1 = (ConnectionFactory) context1.lookup("qpidConnectionFactory");
        Connection connection1 = connectionFactory1.createConnection();
        Context context2 = createContextForShared();
        ConnectionFactory connectionFactory2 = (ConnectionFactory) context2.lookup("qpidConnectionFactory");
        Connection connection2 = connectionFactory2.createConnection();
        connection1.start();
        connection2.start();

        Session session = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Topic testTopic = (Topic) context1.lookup(topic);

        String subID = "sharedConsumer123";
        MessageConsumer subscriber1 = session.createSharedDurableConsumer(testTopic, subID);
        Logging.log.info("sub1 DONE");
        MessageConsumer subscriber2 = session2.createSharedDurableConsumer(testTopic, subID);
        Logging.log.info("sub1 DONE");
        MessageProducer messageProducer = session.createProducer(testTopic);
        Logging.log.info("producer DONE");
        messageProducer.setDeliveryMode(DeliveryMode.PERSISTENT);

        int count = 10;
        List<Message> listMsgs = generateMessages(session, count);
        sendMessages(messageProducer, listMsgs);
        Logging.log.info("messages sent");

        List<Message> recvd1 = receiveMessages(subscriber1, count, 1);
        List<Message> recvd2 = receiveMessages(subscriber2, count, 1);

        Logging.log.info(subID + " :messages received");
        Logging.log.info(subID + " :messages received");


        assertThat(recvd1.size() + recvd2.size(), is(2 * count));

        subscriber1.close();
        subscriber2.close();
        session.unsubscribe(subID);
        session2.unsubscribe(subID);
        connection1.stop();
        connection2.stop();
        session.close();
        session2.close();
        connection1.close();
        connection2.close();
    }

    @Test
    public void testSharedNonDurableSubscription() throws JMSException, NamingException, InterruptedException, ExecutionException, TimeoutException {
        Logging.log.info("testSharedNonDurableSubscription");

        Context context1 = createContextForShared();
        ConnectionFactory connectionFactory1 = (ConnectionFactory) context1.lookup("qpidConnectionFactory");
        Connection connection1 = connectionFactory1.createConnection();
        Context context2 = createContextForShared();
        ConnectionFactory connectionFactory2 = (ConnectionFactory) context2.lookup("qpidConnectionFactory");
        Connection connection2 = connectionFactory2.createConnection();
        connection1.start();
        connection2.start();

        Session session = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Topic testTopic = (Topic) context1.lookup(topic);
        String subID = "sharedConsumer123";
        MessageConsumer subscriber1 = session.createSharedConsumer(testTopic, subID);
        MessageConsumer subscriber2 = session2.createSharedConsumer(testTopic, subID);
        MessageConsumer subscriber3 = session2.createSharedConsumer(testTopic, subID);
        MessageProducer messageProducer = session.createProducer(testTopic);
        messageProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        int count = 10;
        List<Message> listMsgs = generateMessages(session, count);
        List<CompletableFuture<List<Message>>> results = receiveMessagesAsync(count, subscriber1, subscriber2, subscriber3);
        sendMessages(messageProducer, listMsgs);
        Logging.log.info("messages sent");

        assertThat("Each message should be received only by one consumer",
                results.get(0).get(20, TimeUnit.SECONDS).size() +
                        results.get(1).get(20, TimeUnit.SECONDS).size() +
                        results.get(2).get(20, TimeUnit.SECONDS).size(),
                is(count));
        Logging.log.info("messages received");

        connection1.stop();
        connection2.stop();
        subscriber1.close();
        subscriber2.close();
        session.close();
        session2.close();
        connection1.close();
        connection2.close();
    }
}
