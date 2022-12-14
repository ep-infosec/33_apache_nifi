/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.toolkit.tls.service.client;

import org.apache.nifi.security.util.KeystoreType;
import org.apache.nifi.toolkit.tls.commandLine.CommandLineParseException;
import org.apache.nifi.toolkit.tls.commandLine.ExitCode;
import org.apache.nifi.toolkit.tls.configuration.TlsClientConfig;
import org.apache.nifi.toolkit.tls.configuration.TlsConfig;
import org.apache.nifi.toolkit.tls.service.BaseCertificateAuthorityCommandLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TlsCertificateAuthorityClientCommandLineTest {

    private TlsCertificateAuthorityClientCommandLine tlsCertificateAuthorityClientCommandLine;
    private String testToken;

    @BeforeEach
    public void setup() {
        tlsCertificateAuthorityClientCommandLine = new TlsCertificateAuthorityClientCommandLine();
        testToken = "testToken16bytes";
    }

    @Test
    public void testNoToken() {
        final CommandLineParseException e = assertThrows(CommandLineParseException.class, () -> tlsCertificateAuthorityClientCommandLine.parse());

        assertEquals(ExitCode.ERROR_TOKEN_ARG_EMPTY, e.getExitCode());
    }

    @Test
    public void testDefaults() throws CommandLineParseException, IOException {
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken);
        TlsClientConfig clientConfig = tlsCertificateAuthorityClientCommandLine.createClientConfig();

        assertEquals(TlsConfig.DEFAULT_HOSTNAME, clientConfig.getCaHostname());
        assertEquals(new TlsConfig().calcDefaultDn(InetAddress.getLocalHost().getHostName()), clientConfig.getDn());
        assertEquals(TlsCertificateAuthorityClientCommandLine.KEYSTORE + TlsConfig.DEFAULT_KEY_STORE_TYPE.toLowerCase(), clientConfig.getKeyStore());
        assertEquals(TlsConfig.DEFAULT_KEY_STORE_TYPE, clientConfig.getKeyStoreType());
        assertNull(clientConfig.getKeyStorePassword());
        assertNull(clientConfig.getKeyPassword());
        assertEquals(TlsCertificateAuthorityClientCommandLine.TRUSTSTORE + TlsConfig.DEFAULT_KEY_STORE_TYPE.toLowerCase(), clientConfig.getTrustStore());
        assertEquals(TlsConfig.DEFAULT_KEY_STORE_TYPE, clientConfig.getTrustStoreType());
        assertNull(clientConfig.getTrustStorePassword());
        assertEquals(TlsConfig.DEFAULT_KEY_SIZE, clientConfig.getKeySize());
        assertEquals(TlsConfig.DEFAULT_KEY_PAIR_ALGORITHM, clientConfig.getKeyPairAlgorithm());
        assertEquals(testToken, clientConfig.getToken());
        assertEquals(TlsConfig.DEFAULT_PORT, clientConfig.getPort());
        assertEquals(TlsCertificateAuthorityClientCommandLine.DEFAULT_CONFIG_JSON, tlsCertificateAuthorityClientCommandLine.getConfigJsonOut());
        assertNull(tlsCertificateAuthorityClientCommandLine.getConfigJsonIn());
        assertEquals(TlsCertificateAuthorityClientCommandLine.DEFAULT_CERTIFICATE_DIRECTORY, tlsCertificateAuthorityClientCommandLine.getCertificateDirectory());
    }

    @Test
    public void testKeySize() throws CommandLineParseException {
        int keySize = 1234;
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "-k", Integer.toString(keySize));
        assertEquals(keySize, tlsCertificateAuthorityClientCommandLine.getKeySize());
    }

    @Test
    public void testKeyPairAlgorithm() throws CommandLineParseException {
        String testAlgorithm = "testAlgorithm";
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "-a", testAlgorithm);
        assertEquals(testAlgorithm, tlsCertificateAuthorityClientCommandLine.getKeyAlgorithm());
    }

    @Test
    public void testHelp() {
        final CommandLineParseException e = assertThrows(CommandLineParseException.class, () -> tlsCertificateAuthorityClientCommandLine.parse("-h"));

        assertEquals(ExitCode.HELP, e.getExitCode());
    }

    @Test
    public void testCaHostname() throws CommandLineParseException, IOException {
        String testCaHostname = "testCaHostname";
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "-c", testCaHostname);
        assertEquals(testCaHostname, tlsCertificateAuthorityClientCommandLine.createClientConfig().getCaHostname());
    }

    @Test
    public void testDn() throws CommandLineParseException, IOException {
        String testDn = "testDn";
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "-D", testDn);
        assertEquals(testDn, tlsCertificateAuthorityClientCommandLine.createClientConfig().getDn());
    }

    @Test
    public void testPort() throws CommandLineParseException, IOException {
        int testPort = 2345;
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "-p", Integer.toString(testPort));
        assertEquals(testPort, tlsCertificateAuthorityClientCommandLine.createClientConfig().getPort());
    }

    @Test
    public void testKeyStoreType() throws CommandLineParseException, IOException {
        String testType = "testType";
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "-T", testType);

        TlsClientConfig clientConfig = tlsCertificateAuthorityClientCommandLine.createClientConfig();
        assertEquals(testType, clientConfig.getKeyStoreType());
        String trustStoreType = KeystoreType.JKS.toString().toLowerCase();
        assertEquals(trustStoreType, clientConfig.getTrustStoreType());
        assertEquals(TlsCertificateAuthorityClientCommandLine.KEYSTORE + testType.toLowerCase(), clientConfig.getKeyStore());
        assertEquals(TlsCertificateAuthorityClientCommandLine.TRUSTSTORE + trustStoreType, clientConfig.getTrustStore());
    }

    @Test
    public void testConfigJsonOut() throws CommandLineParseException {
        String testPath = "/1/2/3/4";
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "-f", testPath);
        assertEquals(testPath, tlsCertificateAuthorityClientCommandLine.getConfigJsonOut());
        assertNull(tlsCertificateAuthorityClientCommandLine.getConfigJsonIn());
    }

    @Test
    public void testConfigJsonOutAndUseForBoth() throws CommandLineParseException {
        String testPath = "/1/2/3/4";
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "-f", testPath, "-F");
        assertEquals(testPath, tlsCertificateAuthorityClientCommandLine.getConfigJsonOut());
        assertEquals(testPath, tlsCertificateAuthorityClientCommandLine.getConfigJsonIn());
    }

    @Test
    public void testConfigJsonIn() throws CommandLineParseException {
        String testPath = "/1/2/3/4";
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "--" + BaseCertificateAuthorityCommandLine.READ_CONFIG_JSON_ARG, testPath);
        assertEquals(BaseCertificateAuthorityCommandLine.DEFAULT_CONFIG_JSON, tlsCertificateAuthorityClientCommandLine.getConfigJsonOut());
        assertEquals(testPath, tlsCertificateAuthorityClientCommandLine.getConfigJsonIn());
    }

    @Test
    public void testConfigJsonInAndOut() throws CommandLineParseException {
        String testPath = "/1/2/3/4";
        String testIn = "/2/3/4/5";
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "-f", testPath, "--" + BaseCertificateAuthorityCommandLine.READ_CONFIG_JSON_ARG, testIn);
        assertEquals(testPath, tlsCertificateAuthorityClientCommandLine.getConfigJsonOut());
        assertEquals(testIn, tlsCertificateAuthorityClientCommandLine.getConfigJsonIn());
    }

    @Test
    public void testCertificateFile() throws CommandLineParseException {
        String testCertificateFile = "testCertificateFile";
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "-C", testCertificateFile);
        assertEquals(testCertificateFile, tlsCertificateAuthorityClientCommandLine.getCertificateDirectory());
    }

    @Test
    public void testConfigClientSAN() throws CommandLineParseException {
        tlsCertificateAuthorityClientCommandLine.parse("-t", testToken, "--" + TlsCertificateAuthorityClientCommandLine.SUBJECT_ALTERNATIVE_NAMES, "nifi.apache.org,minifi.apache.org");
        assertEquals("nifi.apache.org", tlsCertificateAuthorityClientCommandLine.getDomainAlternativeNames().get(0));
        assertEquals("minifi.apache.org", tlsCertificateAuthorityClientCommandLine.getDomainAlternativeNames().get(1));
    }
}
