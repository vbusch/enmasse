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


package enmasse.mqtt;

/**
 * Provides information about client disconnection
 */
public class DisconnectionData {

    private String clientId;
    private boolean isError;

    /**
     * Constructor
     *
     * @param clientId  client identifier disconnected
     * @param isError   if there is a disconnection error
     */
    public DisconnectionData(String clientId, boolean isError) {
        this.clientId = clientId;
        this.isError = isError;
    }

    /**
     * Client identifier disconnected
     *
     * @return
     */
    public String clientId() {
        return this.clientId;
    }

    /**
     * If there is a disconnection error
     *
     * @return
     */
    public boolean isError() {
        return this.isError;
    }
}
