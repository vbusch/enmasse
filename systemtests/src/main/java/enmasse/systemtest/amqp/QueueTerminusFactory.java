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

package enmasse.systemtest.amqp;

import enmasse.systemtest.amqp.TerminusFactory;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;

public class QueueTerminusFactory implements TerminusFactory {
    @Override
    public Source getSource(String address) {
        Source source = new Source();
        source.setAddress(address);
        return source;
    }

    @Override
    public Target getTarget(String address) {
        Target target = new Target();
        target.setAddress(address);
        return target;
    }
}
