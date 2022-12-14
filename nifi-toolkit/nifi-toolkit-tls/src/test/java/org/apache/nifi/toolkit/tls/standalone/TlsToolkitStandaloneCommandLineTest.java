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

package org.apache.nifi.toolkit.tls.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.nifi.toolkit.tls.commandLine.CommandLineParseException;
import org.apache.nifi.toolkit.tls.commandLine.ExitCode;
import org.apache.nifi.toolkit.tls.configuration.InstanceDefinition;
import org.apache.nifi.toolkit.tls.configuration.InstanceIdentifier;
import org.apache.nifi.toolkit.tls.configuration.StandaloneConfig;
import org.apache.nifi.toolkit.tls.configuration.TlsConfig;
import org.apache.nifi.toolkit.tls.properties.NiFiPropertiesWriter;
import org.apache.nifi.toolkit.tls.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TlsToolkitStandaloneCommandLineTest {
    private TlsToolkitStandaloneCommandLine tlsToolkitStandaloneCommandLine;

    final String CHANGEIT = "changeit";
    final String keyPass = CHANGEIT;
    final String keystorePass = CHANGEIT;
    final String wrongPass = "wrongpass";
    private File outputFolder = null;
    final String keystoreFile = Objects.requireNonNull(getClass().getClassLoader().getResource("keystore.jks")).getFile();

    @BeforeEach
    public void setup(@TempDir Path tempDir) throws IOException {
        final SecureRandom secureRandom = new SecureRandom();
        tlsToolkitStandaloneCommandLine = new TlsToolkitStandaloneCommandLine(new PasswordUtil(secureRandom));
        final Path outputDir = Files.createTempDirectory(tempDir, "splitKeystoreOutputDir");
        outputFolder = outputDir.toFile();
    }

    @Test
    public void testHelp() {
        final CommandLineParseException e = assertThrows(CommandLineParseException.class, () -> tlsToolkitStandaloneCommandLine.parse("-h"));

        assertEquals(ExitCode.HELP, e.getExitCode());
    }

    @Test
    public void testUnknownArg() {
        final CommandLineParseException e = assertThrows(CommandLineParseException.class, () -> tlsToolkitStandaloneCommandLine.parse("--unknownArg"));

        assertEquals(ExitCode.ERROR_PARSING_COMMAND_LINE, e.getExitCode());
    }

    @Test
    public void testKeyAlgorithm() throws CommandLineParseException {
        String testKeyAlgorithm = "testKeyAlgorithm";
        tlsToolkitStandaloneCommandLine.parse("-a", testKeyAlgorithm);
        assertEquals(testKeyAlgorithm, tlsToolkitStandaloneCommandLine.createConfig().getKeyPairAlgorithm());
    }

    @Test
    public void testKeySizeArgNotInteger() {
        final CommandLineParseException e = assertThrows(CommandLineParseException.class, () -> tlsToolkitStandaloneCommandLine.parse("-k", "badVal"));

        assertEquals(ExitCode.ERROR_PARSING_INT_ARG, e.getExitCode());
    }

    @Test
    public void testKeySize() throws CommandLineParseException {
        int testKeySize = 4096;
        tlsToolkitStandaloneCommandLine.parse("-k", Integer.toString(testKeySize));
        assertEquals(testKeySize, tlsToolkitStandaloneCommandLine.createConfig().getKeySize());
    }

    @Test
    public void testSigningAlgorithm() throws CommandLineParseException {
        String testSigningAlgorithm = "testSigningAlgorithm";
        tlsToolkitStandaloneCommandLine.parse("-s", testSigningAlgorithm);
        assertEquals(testSigningAlgorithm, tlsToolkitStandaloneCommandLine.createConfig().getSigningAlgorithm());
    }

    @Test
    public void testSAN() throws CommandLineParseException {
        String dnsSAN = "nifi.apache.org";
        tlsToolkitStandaloneCommandLine.parse("--subjectAlternativeNames", dnsSAN);
        assertEquals(dnsSAN, tlsToolkitStandaloneCommandLine.createConfig().getDomainAlternativeNames().get(0));
    }

    @Test
    public void testDaysNotInteger() {
        final CommandLineParseException e = assertThrows(CommandLineParseException.class, () -> tlsToolkitStandaloneCommandLine.parse("-d", "badVal"));

        assertEquals(ExitCode.ERROR_PARSING_INT_ARG, e.getExitCode());
    }

    @Test
    public void testDays() throws CommandLineParseException {
        int testDays = 29;
        tlsToolkitStandaloneCommandLine.parse("-d", Integer.toString(testDays));
        assertEquals(testDays, tlsToolkitStandaloneCommandLine.createConfig().getDays());
    }

    @Test
    public void testKeyStoreType() throws CommandLineParseException {
        String testKeyStoreType = "testKeyStoreType";
        tlsToolkitStandaloneCommandLine.parse("-T", testKeyStoreType);
        assertEquals(testKeyStoreType, tlsToolkitStandaloneCommandLine.getKeyStoreType());
    }

    @Test
    public void testOutputDirectory() throws CommandLineParseException {
        String testPath = File.separator + "fake" + File.separator + "path" + File.separator + "doesnt" + File.separator + "exist";
        tlsToolkitStandaloneCommandLine.parse("-o", testPath);
        assertEquals(testPath, tlsToolkitStandaloneCommandLine.createConfig().getBaseDir().getPath());
    }

    @Test
    public void testHostnames() throws CommandLineParseException {
        String nifi1 = "nifi1";
        String nifi2 = "nifi2";

        tlsToolkitStandaloneCommandLine.parse("-n", nifi1 + " , " + nifi2);

        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertEquals(2, instanceDefinitions.size());
        assertEquals(nifi1, instanceDefinitions.get(0).getHostname());
        assertEquals(nifi2, instanceDefinitions.get(1).getHostname());
    }

    @Test
    public void testNifiPropertiesFile() throws CommandLineParseException, IOException {
        tlsToolkitStandaloneCommandLine.parse("-f", TlsToolkitStandaloneTest.TEST_NIFI_PROPERTIES);
        assertEquals(TlsToolkitStandaloneTest.FAKE_VALUE, getProperties().get(TlsToolkitStandaloneTest.NIFI_FAKE_PROPERTY));
    }

    @Test
    public void testNifiPropertiesFileDefault() throws CommandLineParseException, IOException {
        tlsToolkitStandaloneCommandLine.parse();
        assertNull(getProperties().get(TlsToolkitStandaloneTest.NIFI_FAKE_PROPERTY));
    }

    @Test
    public void testBadNifiPropertiesFile() {
        final CommandLineParseException e = assertThrows(CommandLineParseException.class, () -> tlsToolkitStandaloneCommandLine.parse("-f", "/this/file/should/not/exist.txt"));

        assertEquals(ExitCode.ERROR_READING_NIFI_PROPERTIES, e.getExitCode());
    }

    @Test
    public void testNotSameKeyAndKeystorePassword() throws CommandLineParseException {
        tlsToolkitStandaloneCommandLine.parse("-g", "-n", TlsConfig.DEFAULT_HOSTNAME);
        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertEquals(1, instanceDefinitions.size());
        assertNotEquals(instanceDefinitions.get(0).getKeyStorePassword(), instanceDefinitions.get(0).getKeyPassword());
    }

    @Test
    public void testSameKeyAndKeystorePassword() throws CommandLineParseException {
        tlsToolkitStandaloneCommandLine.parse("-n", TlsConfig.DEFAULT_HOSTNAME);
        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertEquals(1, instanceDefinitions.size());
        assertEquals(instanceDefinitions.get(0).getKeyStorePassword(), instanceDefinitions.get(0).getKeyPassword());
    }

    @Test
    public void testSameKeyAndKeystorePasswordWithKeystorePasswordSpecified() throws CommandLineParseException {
        String testPassword = "testPassword";
        tlsToolkitStandaloneCommandLine.parse("-S", testPassword, "-n", TlsConfig.DEFAULT_HOSTNAME);
        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertEquals(1, instanceDefinitions.size());
        assertEquals(testPassword, instanceDefinitions.get(0).getKeyStorePassword());
        assertEquals(testPassword, instanceDefinitions.get(0).getKeyPassword());
    }

    @Test
    public void testSameKeyAndKeystorePasswordWithKeyPasswordSpecified() throws CommandLineParseException {
        String testPassword = "testPassword";
        tlsToolkitStandaloneCommandLine.parse("-K", testPassword, "-n", TlsConfig.DEFAULT_HOSTNAME);
        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertNotEquals(instanceDefinitions.get(0).getKeyStorePassword(), instanceDefinitions.get(0).getKeyPassword());
        assertEquals(1, instanceDefinitions.size());
        assertEquals(testPassword, instanceDefinitions.get(0).getKeyPassword());
    }

    @Test
    public void testKeyStorePasswordArg() throws CommandLineParseException {
        String testPassword = "testPassword";
        tlsToolkitStandaloneCommandLine.parse("-S", testPassword, "-n", TlsConfig.DEFAULT_HOSTNAME);
        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertEquals(1, instanceDefinitions.size());
        assertEquals(testPassword, instanceDefinitions.get(0).getKeyStorePassword());
    }

    @Test
    public void testMultipleKeystorePasswordArgs() throws CommandLineParseException {
        String testPassword1 = "testPassword1";
        String testPassword2 = "testPassword2";
        tlsToolkitStandaloneCommandLine.parse("-n", "nifi1,nifi2", "-S", testPassword1, "-S", testPassword2);
        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertEquals(2, instanceDefinitions.size());
        assertEquals(testPassword1, instanceDefinitions.get(0).getKeyStorePassword());
        assertEquals(testPassword2, instanceDefinitions.get(1).getKeyStorePassword());
    }

    @Test
    public void testKeyPasswordArg() throws CommandLineParseException {
        String testPassword = "testPassword";
        tlsToolkitStandaloneCommandLine.parse("-K", testPassword, "-n", TlsConfig.DEFAULT_HOSTNAME);
        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertEquals(1, instanceDefinitions.size());
        assertEquals(testPassword, instanceDefinitions.get(0).getKeyPassword());
    }

    @Test
    public void testMultipleKeyPasswordArgs() throws CommandLineParseException {
        String testPassword1 = "testPassword1";
        String testPassword2 = "testPassword2";
        tlsToolkitStandaloneCommandLine.parse("-n", "nifi1,nifi2", "-K", testPassword1, "-K", testPassword2);
        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertEquals(2, instanceDefinitions.size());
        assertEquals(testPassword1, instanceDefinitions.get(0).getKeyPassword());
        assertEquals(testPassword2, instanceDefinitions.get(1).getKeyPassword());
    }

    @Test
    public void testTruststorePasswordArg() throws CommandLineParseException {
        String testPassword = "testPassword";
        tlsToolkitStandaloneCommandLine.parse("-P", testPassword, "-n", TlsConfig.DEFAULT_HOSTNAME);
        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertEquals(1, instanceDefinitions.size());
        assertEquals(testPassword, instanceDefinitions.get(0).getTrustStorePassword());
    }

    @Test
    public void testMultipleTruststorePasswordArgs() throws CommandLineParseException {
        String testPassword1 = "testPassword1";
        String testPassword2 = "testPassword2";
        tlsToolkitStandaloneCommandLine.parse("-n", "nifi1,nifi2", "-P", testPassword1, "-P", testPassword2);
        List<InstanceDefinition> instanceDefinitions = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions();
        assertEquals(2, instanceDefinitions.size());
        assertEquals(testPassword1, instanceDefinitions.get(0).getTrustStorePassword());
        assertEquals(testPassword2, instanceDefinitions.get(1).getTrustStorePassword());
    }

    @Test
    public void testNifiDnPrefix() throws CommandLineParseException {
        String testPrefix = "O=apache, CN=";
        tlsToolkitStandaloneCommandLine.parse("-n", "nifi", "--nifiDnPrefix", testPrefix);
        StandaloneConfig config = tlsToolkitStandaloneCommandLine.createConfig();
        assertEquals(testPrefix, config.getDnPrefix());
    }

    @Test
    public void testNifiDnSuffix() throws CommandLineParseException {
        String testSuffix = ", O=apache, OU=nifi";
        tlsToolkitStandaloneCommandLine.parse("-n", "nifi", "--nifiDnSuffix", testSuffix);
        StandaloneConfig config = tlsToolkitStandaloneCommandLine.createConfig();
        assertEquals(testSuffix, config.getDnSuffix());
    }

    @Test
    public void testClientDnDefault() throws CommandLineParseException {
        tlsToolkitStandaloneCommandLine.parse();
        assertEquals(Collections.emptyList(), tlsToolkitStandaloneCommandLine.createConfig().getClientDns());
    }

    @Test
    public void testClientDnSingle() throws CommandLineParseException {
        String testCn = "OU=NIFI,CN=testuser";
        tlsToolkitStandaloneCommandLine.parse("-C", testCn);
        List<String> clientDns = tlsToolkitStandaloneCommandLine.createConfig().getClientDns();
        assertEquals(1, clientDns.size());
        assertEquals(testCn, clientDns.get(0));
    }

    @Test
    public void testClientDnMulti() throws CommandLineParseException {
        String testCn = "OU=NIFI,CN=testuser";
        String testCn2 = "OU=NIFI,CN=testuser2";
        tlsToolkitStandaloneCommandLine.parse("-C", testCn, "-C", testCn2);
        StandaloneConfig standaloneConfig = tlsToolkitStandaloneCommandLine.createConfig();
        List<String> clientDns = standaloneConfig.getClientDns();
        assertEquals(2, clientDns.size());
        assertEquals(testCn, clientDns.get(0));
        assertEquals(testCn2, clientDns.get(1));
        assertEquals(2, standaloneConfig.getClientPasswords().size());
    }

    @Test
    public void testClientPasswordMulti() throws CommandLineParseException {
        String testCn = "OU=NIFI,CN=testuser";
        String testCn2 = "OU=NIFI,CN=testuser2";
        String testPass1 = "testPass1";
        String testPass2 = "testPass2";
        tlsToolkitStandaloneCommandLine.parse("-C", testCn, "-C", testCn2, "-B", testPass1, "-B", testPass2);
        StandaloneConfig standaloneConfig = tlsToolkitStandaloneCommandLine.createConfig();
        List<String> clientDns = standaloneConfig.getClientDns();
        assertEquals(2, clientDns.size());
        assertEquals(testCn, clientDns.get(0));
        assertEquals(testCn2, clientDns.get(1));
        List<String> clientPasswords = standaloneConfig.getClientPasswords();
        assertEquals(2, clientPasswords.size());
        assertEquals(testPass1, clientPasswords.get(0));
        assertEquals(testPass2, clientPasswords.get(1));
    }

    @Test
    public void testNoGlobalOrder() throws CommandLineParseException {
        String hostname1 = "other0[4-6]";
        String hostname2 = "nifi3(2)";
        tlsToolkitStandaloneCommandLine.parse("-n", hostname1, "-n", hostname2);
        Map<InstanceIdentifier, InstanceDefinition> definitionMap = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions().stream()
                .collect(Collectors.toMap(InstanceDefinition::getInstanceIdentifier, Function.identity()));
        assertEquals(5, definitionMap.size());

        InstanceDefinition nifi3_1 = definitionMap.get(new InstanceIdentifier("nifi3", 1));
        assertNotNull(nifi3_1);
        assertEquals(1, nifi3_1.getInstanceIdentifier().getNumber());
        assertEquals(1, nifi3_1.getNumber());

        InstanceDefinition nifi3_2 = definitionMap.get(new InstanceIdentifier("nifi3", 2));
        assertNotNull(nifi3_2);
        assertEquals(2, nifi3_2.getInstanceIdentifier().getNumber());
        assertEquals(2, nifi3_2.getNumber());

        InstanceDefinition other04 = definitionMap.get(new InstanceIdentifier("other04", 1));
        assertNotNull(other04);
        assertEquals(1, other04.getInstanceIdentifier().getNumber());
        assertEquals(1, other04.getNumber());

        InstanceDefinition other05 = definitionMap.get(new InstanceIdentifier("other05", 1));
        assertNotNull(other05);
        assertEquals(1, other05.getInstanceIdentifier().getNumber());
        assertEquals(1, other05.getNumber());

        InstanceDefinition other06 = definitionMap.get(new InstanceIdentifier("other06", 1));
        assertNotNull(other06);
        assertEquals(1, other06.getInstanceIdentifier().getNumber());
        assertEquals(1, other06.getNumber());
    }

    @Test
    public void testGlobalOrder() throws CommandLineParseException {
        String hostname1 = "other0[4-6]";
        String hostname2 = "nifi3(2)";
        String globalOrder1 = "nifi[1-5](2),other[01-4]";
        String globalOrder2 = "other[05-10]";
        tlsToolkitStandaloneCommandLine.parse("-n", hostname1, "-n", hostname2, "-G", globalOrder1, "-G", globalOrder2);
        Map<InstanceIdentifier, InstanceDefinition> definitionMap = tlsToolkitStandaloneCommandLine.createConfig().getInstanceDefinitions().stream()
                .collect(Collectors.toMap(InstanceDefinition::getInstanceIdentifier, Function.identity()));
        assertEquals(5, definitionMap.size());

        InstanceDefinition nifi3_1 = definitionMap.get(new InstanceIdentifier("nifi3", 1));
        assertNotNull(nifi3_1);
        assertEquals(1, nifi3_1.getInstanceIdentifier().getNumber());
        assertEquals(5, nifi3_1.getNumber());

        InstanceDefinition nifi3_2 = definitionMap.get(new InstanceIdentifier("nifi3", 2));
        assertNotNull(nifi3_2);
        assertEquals(2, nifi3_2.getInstanceIdentifier().getNumber());
        assertEquals(6, nifi3_2.getNumber());

        InstanceDefinition other04 = definitionMap.get(new InstanceIdentifier("other04", 1));
        assertNotNull(other04);
        assertEquals(1, other04.getInstanceIdentifier().getNumber());
        assertEquals(14, other04.getNumber());

        InstanceDefinition other05 = definitionMap.get(new InstanceIdentifier("other05", 1));
        assertNotNull(other05);
        assertEquals(1, other05.getInstanceIdentifier().getNumber());
        assertEquals(15, other05.getNumber());

        InstanceDefinition other06 = definitionMap.get(new InstanceIdentifier("other06", 1));
        assertNotNull(other06);
        assertEquals(1, other06.getInstanceIdentifier().getNumber());
        assertEquals(16, other06.getNumber());
    }

    @Test
    public void testBadGlobalOrder() {
        assertThrows(IllegalArgumentException.class, () -> tlsToolkitStandaloneCommandLine.parse("-n", "notInGlobalOrder", "-G", "nifi[1-3]"));
    }

    @Test
    public void testDefaultOutputPathRoot() {
        Path root = Paths.get(".").toAbsolutePath().getRoot().resolve(".");
        String calculateDefaultOutputDirectory = TlsToolkitStandaloneCommandLine.calculateDefaultOutputDirectory(root);
        assertEquals(root.toAbsolutePath().getRoot().toString(), calculateDefaultOutputDirectory);
    }

    @Test
    public void testDefaultOutputPath() {
        Path path = Paths.get(".");
        String calculateDefaultOutputDirectory = TlsToolkitStandaloneCommandLine.calculateDefaultOutputDirectory(path);
        assertEquals("../" + path.toAbsolutePath().normalize().getFileName().toString(), calculateDefaultOutputDirectory);
    }

    private Properties getProperties() throws IOException {
        NiFiPropertiesWriter niFiPropertiesWriter = tlsToolkitStandaloneCommandLine.createConfig().getNiFiPropertiesWriterFactory().create();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        niFiPropertiesWriter.writeNiFiProperties(byteArrayOutputStream);
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
        return properties;
    }

    @Test
    public void testSplitKeystore() throws Exception {
        tlsToolkitStandaloneCommandLine.parse("-splitKeystore", keystoreFile, "-S", keystorePass, "-K", keyPass, "-o", outputFolder.getPath());
        StandaloneConfig standaloneConfig = tlsToolkitStandaloneCommandLine.createSplitKeystoreConfig();

        assertTrue(standaloneConfig.isSplitKeystore());
        assertEquals(keyPass, standaloneConfig.getKeyPassword());
        assertEquals(keystorePass, standaloneConfig.getKeyStorePassword());
        TlsToolkitStandalone toolkit = new TlsToolkitStandalone();
        toolkit.splitKeystore(standaloneConfig);

        assertEquals(3, Objects.requireNonNull(outputFolder.listFiles()).length);

        // Validity checking of the output is done in TlsHelperTest
        for (File file : Objects.requireNonNull(outputFolder.listFiles())) {
            assertTrue(file.length() > 0);
        }
    }

    @Test
    public void testSplitKeystoreMissingPasswords() {
        assertThrows(CommandLineParseException.class, () -> tlsToolkitStandaloneCommandLine.parse("-splitKeystore", keystoreFile, "-o", outputFolder.getPath()));
    }

    @Test
    public void testSplitKeystoreWithSameKeystoreAndKeyPassword() throws Exception {
        tlsToolkitStandaloneCommandLine.parse("-splitKeystore", keystoreFile, "-S", keystorePass,  "-o", outputFolder.getPath());
        StandaloneConfig standaloneConfig = tlsToolkitStandaloneCommandLine.createSplitKeystoreConfig();

        TlsToolkitStandalone toolkit = new TlsToolkitStandalone();
        toolkit.splitKeystore(standaloneConfig);
    }

    @Test
    public void testSplitKeystoreWrongKeyPass() throws Exception {
        tlsToolkitStandaloneCommandLine.parse("-splitKeystore", keystoreFile, "-S", keystorePass, "-K", wrongPass, "-o", outputFolder.getPath());
        StandaloneConfig standaloneConfig = tlsToolkitStandaloneCommandLine.createSplitKeystoreConfig();

        TlsToolkitStandalone toolkit = new TlsToolkitStandalone();

        assertThrows(UnrecoverableKeyException.class, () -> toolkit.splitKeystore(standaloneConfig));
    }

    @Test
    public void testSplitKeystoreWrongKeystorePass() throws Exception {
        tlsToolkitStandaloneCommandLine.parse("-splitKeystore", keystoreFile, "-S", wrongPass, "-K", keyPass, "-o", outputFolder.getPath());
        StandaloneConfig standaloneConfig = tlsToolkitStandaloneCommandLine.createSplitKeystoreConfig();

        TlsToolkitStandalone toolkit = new TlsToolkitStandalone();

        assertThrows(IOException.class, () -> toolkit.splitKeystore(standaloneConfig));
    }

    @Test
    public void testSplitKeystoreNoKeystore() {
        assertThrows(CommandLineParseException.class, () -> tlsToolkitStandaloneCommandLine.parse("-splitKeystore", "-S", keystorePass, "-K", keyPass, "-o", outputFolder.getPath()));
    }

    @Test
    public void testSplitKeystoreEmptyKeystore() throws Exception {
        tlsToolkitStandaloneCommandLine.parse(
                "-splitKeystore", new File("src/test/resources/empty-keystore.jks").getPath(), "-S", keystorePass, "-K", keyPass, "-o", outputFolder.getPath());
        StandaloneConfig standaloneConfig = tlsToolkitStandaloneCommandLine.createSplitKeystoreConfig();

        assertTrue(standaloneConfig.isSplitKeystore());
        assertEquals(keyPass, standaloneConfig.getKeyPassword());
        assertEquals(keystorePass, standaloneConfig.getKeyStorePassword());
        TlsToolkitStandalone toolkit = new TlsToolkitStandalone();

        assertThrows(KeyStoreException.class, () -> toolkit.splitKeystore(standaloneConfig));
    }
}
