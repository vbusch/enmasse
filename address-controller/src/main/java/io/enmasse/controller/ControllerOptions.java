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

package io.enmasse.controller;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;

public final class ControllerOptions {

    private static final String SERVICEACCOUNT_PATH = "/var/run/secrets/kubernetes.io/serviceaccount";

    private final String masterUrl;
    private final String namespace;
    private final String token;

    private final String certDir;
    private final File templateDir;
    private final AuthServiceInfo noneAuthService;
    private final AuthServiceInfo standardAuthService;
    private final boolean enableRbac;

    private final String environment;

    private ControllerOptions(String masterUrl, String namespace, String token,
                              File templateDir, String certDir,
                              AuthServiceInfo noneAuthService, AuthServiceInfo standardAuthService, boolean enableRbac, String environment) {
        this.masterUrl = masterUrl;
        this.namespace = namespace;
        this.token = token;
        this.templateDir = templateDir;
        this.certDir = certDir;
        this.noneAuthService = noneAuthService;
        this.standardAuthService = standardAuthService;
        this.enableRbac = enableRbac;
        this.environment = environment;
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getToken() {
        return token;
    }

    public Optional<File> getTemplateDir() {
        return Optional.ofNullable(templateDir);
    }

    public String getCertDir() {
        return certDir;
    }

    public Optional<AuthServiceInfo> getNoneAuthService() {
        return Optional.ofNullable(noneAuthService);
    }

    public Optional<AuthServiceInfo> getStandardAuthService() {
        return Optional.ofNullable(standardAuthService);
    }

    public boolean isEnableRbac() {
        return enableRbac;
    }

    public String getEnvironment() {
        return environment;
    }

    public static ControllerOptions fromEnv(Map<String, String> env) throws IOException {

        String masterHost = getEnvOrThrow(env, "KUBERNETES_SERVICE_HOST");
        String masterPort = getEnvOrThrow(env, "KUBERNETES_SERVICE_PORT");

        String namespace = getEnv(env, "NAMESPACE")
                .orElseGet(() -> readFile(new File(SERVICEACCOUNT_PATH, "namespace")));

        String token = getEnv(env, "TOKEN")
                .orElseGet(() -> readFile(new File(SERVICEACCOUNT_PATH, "token")));

        File templateDir = getEnv(env, "TEMPLATE_DIR")
                .map(File::new)
                .orElse(new File("/enmasse-templates"));

        if (!templateDir.exists()) {
            templateDir = null;
        }

        AuthServiceInfo noneAuthService = getAuthService(env, "NONE_AUTHSERVICE_SERVICE_HOST", "NONE_AUTHSERVICE_SERVICE_PORT").orElse(null);
        AuthServiceInfo standardAuthService = getAuthService(env, "STANDARD_AUTHSERVICE_SERVICE_HOST", "STANDARD_AUTHSERVICE_SERVICE_PORT_AMQPS").orElse(null);

        String certDir = getEnv(env, "CERT_DIR").orElse("/address-controller-cert");

        boolean enableRbac = getEnv(env, "ENABLE_RBAC").map(Boolean::parseBoolean).orElse(false);

        String environment = getEnv(env, "ENVIRONMENT").orElse("development");

        return new ControllerOptions(String.format("https://%s:%s", masterHost, masterPort),
                namespace,
                token,
                templateDir,
                certDir,
                noneAuthService,
                standardAuthService,
                enableRbac,
                environment);
    }


    private static Optional<AuthServiceInfo> getAuthService(Map<String, String> env, String hostEnv, String portEnv) {

        return getEnv(env, hostEnv)
                .map(host -> new AuthServiceInfo(host, Integer.parseInt(getEnvOrThrow(env, portEnv))));
    }

    private static Optional<String> getEnv(Map<String, String> env, String envVar) {
        return Optional.ofNullable(env.get(envVar));
    }

    private static String getEnvOrThrow(Map<String, String> env, String envVar) {
        String var = env.get(envVar);
        if (var == null) {
            throw new IllegalArgumentException(String.format("Unable to find value for required environment var '%s'", envVar));
        }
        return var;
    }

    private static String readFile(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
