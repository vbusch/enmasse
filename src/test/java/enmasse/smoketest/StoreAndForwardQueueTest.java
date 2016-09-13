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

package enmasse.smoketest;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class StoreAndForwardQueueTest extends VertxTestBase {

    @Test
    public void testQueue() throws InterruptedException, ExecutionException, TimeoutException {
        String dest = "myqueue";
        EnMasseClient client = createClient(false);
        List<String> msgs = Arrays.asList("foo", "bar", "baz");

        Future<Integer> numSent = client.sendMessages(dest, msgs);
        Future<List<String>> received = client.recvMessages(dest, msgs.size());

        assertThat(numSent.get(1, TimeUnit.MINUTES), is(msgs.size()));
        assertThat(received.get(1, TimeUnit.MINUTES).size(), is(msgs.size()));
    }
}

