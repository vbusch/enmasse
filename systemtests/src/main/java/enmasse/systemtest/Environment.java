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

package enmasse.systemtest;

public class Environment {
    private final String user = System.getenv("OPENSHIFT_USER");
    private final String token = System.getenv("OPENSHIFT_TOKEN");
    private final String url = System.getenv("OPENSHIFT_MASTER_URL");
    private final String namespace = System.getenv("OPENSHIFT_NAMESPACE");
    private final String useTls = System.getenv("OPENSHIFT_USE_TLS");
    private final String messagingCert = System.getenv("OPENSHIFT_SERVER_CERT");
    private final boolean multitenant = Boolean.parseBoolean(System.getenv("OPENSHIFT_MULTITENANT"));

    public String openShiftUrl() {
        return url;
    }

    public String openShiftToken() {
        return token;
    }

    public String openShiftUser() {
        return user;
    }

    public boolean useTLS() {
        return (useTls != null && useTls.toLowerCase().equals("true"));
    }

    public String messagingCert() {
        return this.messagingCert;
    }

    public String namespace() {
        return namespace;
    }

    public boolean isMultitenant() {
        return multitenant;
    }
}
