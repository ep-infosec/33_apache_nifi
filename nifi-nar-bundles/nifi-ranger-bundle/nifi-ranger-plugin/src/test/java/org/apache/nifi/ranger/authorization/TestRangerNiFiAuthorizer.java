/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.nifi.ranger.authorization;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.nifi.authorization.AuthorizationRequest;
import org.apache.nifi.authorization.AuthorizationResult;
import org.apache.nifi.authorization.Authorizer;
import org.apache.nifi.authorization.AuthorizerConfigurationContext;
import org.apache.nifi.authorization.AuthorizerInitializationContext;
import org.apache.nifi.authorization.RequestAction;
import org.apache.nifi.authorization.Resource;
import org.apache.nifi.authorization.UserContextKeys;
import org.apache.nifi.authorization.exception.AuthorizerCreationException;
import org.apache.nifi.util.MockPropertyValue;
import org.apache.nifi.util.NiFiProperties;
import org.apache.ranger.authorization.hadoop.config.RangerPluginConfig;
import org.apache.ranger.plugin.policyengine.RangerAccessRequest;
import org.apache.ranger.plugin.policyengine.RangerAccessRequestImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResourceImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestRangerNiFiAuthorizer {

    private MockRangerNiFiAuthorizer authorizer;
    private RangerBasePluginWithPolicies rangerBasePlugin;
    private AuthorizerConfigurationContext configurationContext;
    private NiFiProperties nifiProperties;

    private final String serviceType = "nifi";
    private final String appId = "nifiAppId";

    private RangerAccessResult allowedResult;
    private RangerAccessResult notAllowedResult;

    private Map<String, String> authorizersXmlContent = null;

    @BeforeEach
    public void setup() {
        // have to initialize this system property before anything else
        File krb5conf = new File("src/test/resources/krb5.conf");
        assertTrue(krb5conf.exists());
        System.setProperty("java.security.krb5.conf", krb5conf.getAbsolutePath());

        // rest the authentication to simple in case any tests set it to kerberos
        final Configuration securityConf = new Configuration();
        securityConf.set(RangerNiFiAuthorizer.HADOOP_SECURITY_AUTHENTICATION, "simple");
        UserGroupInformation.setConfiguration(securityConf);

        // initialize the content of authorizers.xml in case tests added further entries to it
        authorizersXmlContent = Stream.of(new String[][] {
                {RangerNiFiAuthorizer.RANGER_SECURITY_PATH_PROP, "src/test/resources/ranger/ranger-nifi-security.xml"},
                {RangerNiFiAuthorizer.RANGER_AUDIT_PATH_PROP, "src/test/resources/ranger/ranger-nifi-audit.xml"},
                {RangerNiFiAuthorizer.RANGER_APP_ID_PROP, appId},
                {RangerNiFiAuthorizer.RANGER_SERVICE_TYPE_PROP, serviceType}
        }).collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
        configurationContext = createMockConfigContext();
        rangerBasePlugin = Mockito.mock(RangerBasePluginWithPolicies.class);

        final RangerPluginConfig pluginConfig = new RangerPluginConfig(serviceType, null, appId, null, null, null);
        when(rangerBasePlugin.getConfig()).thenReturn(pluginConfig);

        authorizer = new MockRangerNiFiAuthorizer(rangerBasePlugin);
        authorizer.onConfigured(configurationContext);

        assertFalse(UserGroupInformation.isSecurityEnabled());

        allowedResult = Mockito.mock(RangerAccessResult.class);
        when(allowedResult.getIsAllowed()).thenReturn(true);

        notAllowedResult = Mockito.mock(RangerAccessResult.class);
        when(notAllowedResult.getIsAllowed()).thenReturn(false);
    }

    private AuthorizerConfigurationContext createMockConfigContext() {
        AuthorizerConfigurationContext configurationContext = Mockito.mock(AuthorizerConfigurationContext.class);

        for (Map.Entry<String, String> entry : authorizersXmlContent.entrySet()) {
            when(configurationContext.getProperty(eq(entry.getKey())))
                    .thenReturn(new MockPropertyValue(entry.getValue()));
        }

        when(configurationContext.getProperties()).thenReturn(authorizersXmlContent);

        return configurationContext;
    }

    @Test
    public void testOnConfigured() {
        verify(rangerBasePlugin, times(1)).init();

        assertEquals(appId, authorizer.mockRangerBasePlugin.getAppId());
        assertEquals(serviceType, authorizer.mockRangerBasePlugin.getServiceType());
    }

    @Test
    public void testKerberosEnabledWithoutKeytab() {
        when(configurationContext.getProperty(eq(RangerNiFiAuthorizer.RANGER_KERBEROS_ENABLED_PROP)))
                .thenReturn(new MockPropertyValue("true"));

        nifiProperties = Mockito.mock(NiFiProperties.class);
        when(nifiProperties.getKerberosServicePrincipal()).thenReturn("");

        authorizer = new MockRangerNiFiAuthorizer(rangerBasePlugin);
        authorizer.setNiFiProperties(nifiProperties);

        assertThrows(AuthorizerCreationException.class, () ->authorizer.onConfigured(configurationContext));
    }

    @Test
    public void testKerberosEnabledWithoutPrincipal() {
        when(configurationContext.getProperty(eq(RangerNiFiAuthorizer.RANGER_KERBEROS_ENABLED_PROP)))
                .thenReturn(new MockPropertyValue("true"));

        nifiProperties = Mockito.mock(NiFiProperties.class);
        when(nifiProperties.getKerberosServiceKeytabLocation()).thenReturn("");

        authorizer = new MockRangerNiFiAuthorizer(rangerBasePlugin);
        authorizer.setNiFiProperties(nifiProperties);

        assertThrows(AuthorizerCreationException.class, () -> authorizer.onConfigured(configurationContext));
    }

    @Test
    public void testKerberosEnabledWithoutKeytabOrPrincipal() {
        when(configurationContext.getProperty(eq(RangerNiFiAuthorizer.RANGER_KERBEROS_ENABLED_PROP)))
                .thenReturn(new MockPropertyValue("true"));

        nifiProperties = Mockito.mock(NiFiProperties.class);
        when(nifiProperties.getKerberosServiceKeytabLocation()).thenReturn("");
        when(nifiProperties.getKerberosServicePrincipal()).thenReturn("");

        authorizer = new MockRangerNiFiAuthorizer(rangerBasePlugin);
        authorizer.setNiFiProperties(nifiProperties);

        assertThrows(AuthorizerCreationException.class, () -> authorizer.onConfigured(configurationContext));
    }

    @Test
    public void testKerberosEnabled() {
        when(configurationContext.getProperty(eq(RangerNiFiAuthorizer.RANGER_KERBEROS_ENABLED_PROP)))
                .thenReturn(new MockPropertyValue("true"));

        nifiProperties = Mockito.mock(NiFiProperties.class);
        when(nifiProperties.getKerberosServiceKeytabLocation()).thenReturn("test");
        when(nifiProperties.getKerberosServicePrincipal()).thenReturn("test");

        authorizer = new MockRangerNiFiAuthorizer(rangerBasePlugin);
        authorizer.setNiFiProperties(nifiProperties);

        assertThrows(AuthorizerCreationException.class, () -> authorizer.onConfigured(configurationContext));
    }

    @Test
    public void testApprovedWithDirectAccess() {
        final String systemResource = "/system";
        final RequestAction action = RequestAction.WRITE;
        final String user = "admin";
        final String clientIp = "192.168.1.1";

        final Map<String,String> userContext = new HashMap<>();
        userContext.put(UserContextKeys.CLIENT_ADDRESS.name(), clientIp);

        // the incoming NiFi request to test
        final AuthorizationRequest request = new AuthorizationRequest.Builder()
                .resource(new MockResource(systemResource, systemResource))
                .action(action)
                .identity(user)
                .resourceContext(new HashMap<>())
                .userContext(userContext)
                .accessAttempt(true)
                .anonymous(false)
                .build();

        // the expected Ranger resource and request that are created
        final RangerAccessResourceImpl resource = new RangerAccessResourceImpl();
        resource.setValue(RangerNiFiAuthorizer.RANGER_NIFI_RESOURCE_NAME, systemResource);

        final RangerAccessRequestImpl expectedRangerRequest = new RangerAccessRequestImpl();
        expectedRangerRequest.setResource(resource);
        expectedRangerRequest.setAction(request.getAction().name());
        expectedRangerRequest.setAccessType(request.getAction().name());
        expectedRangerRequest.setUser(request.getIdentity());
        expectedRangerRequest.setClientIPAddress(clientIp);

        // a non-null result processor should be used for direct access
        when(rangerBasePlugin.isAccessAllowed(
                argThat(new RangerAccessRequestMatcher(expectedRangerRequest)))
        ).thenReturn(allowedResult);

        final AuthorizationResult result = authorizer.authorize(request);
        assertEquals(AuthorizationResult.approved().getResult(), result.getResult());
    }

    @Test
    public void testApprovedWithNonDirectAccess() {
        final String systemResource = "/system";
        final RequestAction action = RequestAction.WRITE;
        final String user = "admin";

        // the incoming NiFi request to test
        final AuthorizationRequest request = new AuthorizationRequest.Builder()
                .resource(new MockResource(systemResource, systemResource))
                .action(action)
                .identity(user)
                .resourceContext(new HashMap<>())
                .accessAttempt(false)
                .anonymous(false)
                .build();

        // the expected Ranger resource and request that are created
        final RangerAccessResourceImpl resource = new RangerAccessResourceImpl();
        resource.setValue(RangerNiFiAuthorizer.RANGER_NIFI_RESOURCE_NAME, systemResource);

        final RangerAccessRequestImpl expectedRangerRequest = new RangerAccessRequestImpl();
        expectedRangerRequest.setResource(resource);
        expectedRangerRequest.setAction(request.getAction().name());
        expectedRangerRequest.setAccessType(request.getAction().name());
        expectedRangerRequest.setUser(request.getIdentity());

        // no result processor should be provided used non-direct access
        when(rangerBasePlugin.isAccessAllowed(
                argThat(new RangerAccessRequestMatcher(expectedRangerRequest)))
        ).thenReturn(allowedResult);

        final AuthorizationResult result = authorizer.authorize(request);
        assertEquals(AuthorizationResult.approved().getResult(), result.getResult());
    }

    @Test
    public void testResourceNotFound() {
        final String systemResource = "/system";
        final RequestAction action = RequestAction.WRITE;
        final String user = "admin";

        // the incoming NiFi request to test
        final AuthorizationRequest request = new AuthorizationRequest.Builder()
                .resource(new MockResource(systemResource, systemResource))
                .action(action)
                .identity(user)
                .resourceContext(new HashMap<>())
                .accessAttempt(true)
                .anonymous(false)
                .build();

        // the expected Ranger resource and request that are created
        final RangerAccessResourceImpl resource = new RangerAccessResourceImpl();
        resource.setValue(RangerNiFiAuthorizer.RANGER_NIFI_RESOURCE_NAME, systemResource);

        final RangerAccessRequestImpl expectedRangerRequest = new RangerAccessRequestImpl();
        expectedRangerRequest.setResource(resource);
        expectedRangerRequest.setAction(request.getAction().name());
        expectedRangerRequest.setAccessType(request.getAction().name());
        expectedRangerRequest.setUser(request.getIdentity());

        // no result processor should be provided used non-direct access
        when(rangerBasePlugin.isAccessAllowed(
                argThat(new RangerAccessRequestMatcher(expectedRangerRequest)),
                isNotNull())
        ).thenReturn(notAllowedResult);

        // return false when checking if a policy exists for the resource
        when(rangerBasePlugin.doesPolicyExist(systemResource, action)).thenReturn(false);

        final AuthorizationResult result = authorizer.authorize(request);
        assertEquals(AuthorizationResult.resourceNotFound().getResult(), result.getResult());
    }

    @Test
    public void testDenied() {
        final String systemResource = "/system";
        final RequestAction action = RequestAction.WRITE;
        final String user = "admin";

        // the incoming NiFi request to test
        final AuthorizationRequest request = new AuthorizationRequest.Builder()
                .resource(new MockResource(systemResource, systemResource))
                .action(action)
                .identity(user)
                .resourceContext(new HashMap<>())
                .accessAttempt(true)
                .anonymous(false)
                .build();

        // the expected Ranger resource and request that are created
        final RangerAccessResourceImpl resource = new RangerAccessResourceImpl();
        resource.setValue(RangerNiFiAuthorizer.RANGER_NIFI_RESOURCE_NAME, systemResource);

        final RangerAccessRequestImpl expectedRangerRequest = new RangerAccessRequestImpl();
        expectedRangerRequest.setResource(resource);
        expectedRangerRequest.setAction(request.getAction().name());
        expectedRangerRequest.setAccessType(request.getAction().name());
        expectedRangerRequest.setUser(request.getIdentity());

        // no result processor should be provided used non-direct access
        when(rangerBasePlugin.isAccessAllowed(
                argThat(new RangerAccessRequestMatcher(expectedRangerRequest)))
        ).thenReturn(notAllowedResult);

        // return true when checking if a policy exists for the resource
        when(rangerBasePlugin.doesPolicyExist(systemResource, action)).thenReturn(true);

        final AuthorizationResult result = authorizer.authorize(request);
        assertEquals(AuthorizationResult.denied().getResult(), result.getResult());
    }

    @Test
    public void testRangerAdminApproved() {
        final String acceptableIdentity = "ranger-admin";
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX, acceptableIdentity);

        final String requestIdentity = "ranger-admin";
        runRangerAdminTest(RangerNiFiAuthorizer.RESOURCES_RESOURCE, requestIdentity, AuthorizationResult.approved().getResult());
    }

    @Test
    public void testRangerAdminApprovedMultipleAcceptableIdentities() {
        final String acceptableIdentity1 = "ranger-admin1";
        final String acceptableIdentity2 = "ranger-admin2";
        final String acceptableIdentity3 = "ranger-admin3";
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX, acceptableIdentity1);
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX + " 2", acceptableIdentity2);
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX + " 3", acceptableIdentity3);

        final String requestIdentity = "ranger-admin2";
        runRangerAdminTest(RangerNiFiAuthorizer.RESOURCES_RESOURCE, requestIdentity, AuthorizationResult.approved().getResult());
    }

    @Test
    public void testRangerAdminApprovedMultipleAcceptableIdentities2() {
        final String acceptableIdentity1 = "ranger-admin1";
        final String acceptableIdentity2 = "ranger-admin2";
        final String acceptableIdentity3 = "ranger-admin3";
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX, acceptableIdentity1);
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX + " 2", acceptableIdentity2);
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX + " 3", acceptableIdentity3);

        final String requestIdentity = "ranger-admin3";
        runRangerAdminTest(RangerNiFiAuthorizer.RESOURCES_RESOURCE, requestIdentity, AuthorizationResult.approved().getResult());
    }

    @Test
    public void testRangerAdminDenied() {
        final String acceptableIdentity = "ranger-admin";
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX, acceptableIdentity);

        final String requestIdentity = "ranger-admin";
        runRangerAdminTest("/flow", requestIdentity, AuthorizationResult.denied().getResult());
    }

    @Test
    public void testRangerAdminDeniedMultipleAcceptableIdentities() {
        final String acceptableIdentity1 = "ranger-admin1";
        final String acceptableIdentity2 = "ranger-admin2";
        final String acceptableIdentity3 = "ranger-admin3";
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX, acceptableIdentity1);
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX + " 2", acceptableIdentity2);
        authorizersXmlContent.put(RangerNiFiAuthorizer.RANGER_ADMIN_IDENTITY_PROP_PREFIX + " 3", acceptableIdentity3);

        final String requestIdentity = "ranger-admin4";
        runRangerAdminTest(RangerNiFiAuthorizer.RESOURCES_RESOURCE, requestIdentity, AuthorizationResult.denied().getResult());
    }

    private void runRangerAdminTest(final String resourceIdentifier, final String requestIdentity, final AuthorizationResult.Result expectedResult) {
        configurationContext = createMockConfigContext();

        rangerBasePlugin = Mockito.mock(RangerBasePluginWithPolicies.class);

        final RangerPluginConfig pluginConfig = new RangerPluginConfig(serviceType, null, appId, null, null, null);
        when(rangerBasePlugin.getConfig()).thenReturn(pluginConfig);

        authorizer = new MockRangerNiFiAuthorizer(rangerBasePlugin);
        authorizer.onConfigured(configurationContext);

        final RequestAction action = RequestAction.WRITE;

        // the incoming NiFi request to test
        final AuthorizationRequest request = new AuthorizationRequest.Builder()
                .resource(new MockResource(resourceIdentifier, resourceIdentifier))
                .action(action)
                .identity(requestIdentity)
                .resourceContext(new HashMap<>())
                .accessAttempt(true)
                .anonymous(false)
                .build();

        // the expected Ranger resource and request that are created
        final RangerAccessResourceImpl resource = new RangerAccessResourceImpl();
        resource.setValue(RangerNiFiAuthorizer.RANGER_NIFI_RESOURCE_NAME, resourceIdentifier);

        final RangerAccessRequestImpl expectedRangerRequest = new RangerAccessRequestImpl();
        expectedRangerRequest.setResource(resource);
        expectedRangerRequest.setAction(request.getAction().name());
        expectedRangerRequest.setAccessType(request.getAction().name());
        expectedRangerRequest.setUser(request.getIdentity());

        // return true when checking if a policy exists for the resource
        when(rangerBasePlugin.doesPolicyExist(resourceIdentifier, action)).thenReturn(true);

        // a non-null result processor should be used for direct access
        when(rangerBasePlugin.isAccessAllowed(
                argThat(new RangerAccessRequestMatcher(expectedRangerRequest)))
        ).thenReturn(notAllowedResult);

        final AuthorizationResult result = authorizer.authorize(request);
        assertEquals(expectedResult, result.getResult());
    }

    @Test
    @Disabled
    public void testIntegration() {
        final AuthorizerInitializationContext initializationContext = Mockito.mock(AuthorizerInitializationContext.class);
        final AuthorizerConfigurationContext configurationContext = Mockito.mock(AuthorizerConfigurationContext.class);

        when(configurationContext.getProperty(eq(RangerNiFiAuthorizer.RANGER_SECURITY_PATH_PROP)))
                .thenReturn(new MockPropertyValue("src/test/resources/ranger/ranger-nifi-security.xml"));

        when(configurationContext.getProperty(eq(RangerNiFiAuthorizer.RANGER_AUDIT_PATH_PROP)))
                .thenReturn(new MockPropertyValue("src/test/resources/ranger/ranger-nifi-audit.xml"));

        Authorizer authorizer = new RangerNiFiAuthorizer();
        try {
            authorizer.initialize(initializationContext);
            authorizer.onConfigured(configurationContext);

            final AuthorizationRequest request = new AuthorizationRequest.Builder()
                    .resource(new Resource() {
                        @Override
                        public String getIdentifier() {
                            return "/system";
                        }

                        @Override
                        public String getName() {
                            return "/system";
                        }

                        @Override
                        public String getSafeDescription() {
                            return "system";
                        }
                    })
                    .action(RequestAction.WRITE)
                    .identity("admin")
                    .resourceContext(new HashMap<>())
                    .accessAttempt(true)
                    .anonymous(false)
                    .build();


            final AuthorizationResult result = authorizer.authorize(request);

            assertEquals(AuthorizationResult.denied().getResult(), result.getResult());

        } finally {
            authorizer.preDestruction();
        }
    }

    /**
     * Extend RangerNiFiAuthorizer to inject a mock base plugin for testing.
     */
    private static class MockRangerNiFiAuthorizer extends RangerNiFiAuthorizer {

        RangerBasePluginWithPolicies mockRangerBasePlugin;

        public MockRangerNiFiAuthorizer(RangerBasePluginWithPolicies mockRangerBasePlugin) {
            this.mockRangerBasePlugin = mockRangerBasePlugin;
        }

        @Override
        protected RangerBasePluginWithPolicies createRangerBasePlugin(String serviceType, String appId) {
            when(mockRangerBasePlugin.getAppId()).thenReturn(appId);
            when(mockRangerBasePlugin.getServiceType()).thenReturn(serviceType);
            return mockRangerBasePlugin;
        }
    }

    /**
     * Resource implementation for testing.
     */
    private static class MockResource implements Resource {

        private final String identifier;
        private final String name;

        public MockResource(String identifier, String name) {
            this.identifier = identifier;
            this.name = name;
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getSafeDescription() {
            return name;
        }
    }

    /**
     * Custom Mockito matcher for RangerAccessRequest objects.
     */
    private static class RangerAccessRequestMatcher implements ArgumentMatcher<RangerAccessRequest> {

        private final RangerAccessRequest request;

        public RangerAccessRequestMatcher(RangerAccessRequest request) {
            this.request = request;
        }

        @Override
        public boolean matches(RangerAccessRequest argument) {
            if (argument == null) {
                return false;
            }

            final boolean clientIpsMatch = (argument.getClientIPAddress() == null && request.getClientIPAddress() == null)
                    || (argument.getClientIPAddress() != null && request.getClientIPAddress() != null && argument.getClientIPAddress().equals(request.getClientIPAddress()));

            return argument.getResource().equals(request.getResource())
                    && argument.getAccessType().equals(request.getAccessType())
                    && argument.getAction().equals(request.getAction())
                    && argument.getUser().equals(request.getUser())
                    && clientIpsMatch;
        }
    }

}
