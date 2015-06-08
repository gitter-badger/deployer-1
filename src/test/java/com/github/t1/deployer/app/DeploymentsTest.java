package com.github.t1.deployer.app;

import static com.github.t1.deployer.TestData.*;
import static com.github.t1.deployer.repository.ArtifactoryMock.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import javax.xml.bind.JAXB;

import lombok.AllArgsConstructor;

import org.glassfish.hk2.api.Factory;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;

import com.github.t1.deployer.container.DeploymentContainer;
import com.github.t1.deployer.model.*;
import com.github.t1.deployer.repository.Repository;
import com.github.t1.deployer.tools.FactoryInstance;

@RunWith(MockitoJUnitRunner.class)
public class DeploymentsTest {
    @InjectMocks
    Deployments deployments;
    @Mock
    Repository repository;
    @Mock
    DeploymentContainer container;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private final List<Deployment> installedDeployments = new ArrayList<>();

    @Before
    public void setup() {
        when(container.getAllDeployments()).thenReturn(installedDeployments);
        deployments.deploymentResources = new FactoryInstance<>(new Factory<DeploymentResource>() {
            @Override
            public DeploymentResource provide() {
                DeploymentResource result = new DeploymentResource();
                result.container = container;
                result.repository = repository;
                return result;
            }

            @Override
            public void dispose(DeploymentResource instance) {}
        });
    }

    @AllArgsConstructor
    private class OngoingDeploymentStub {
        Deployment deployment;

        public void availableVersions(String... versions) {
            Map<Version, CheckSum> map = new LinkedHashMap<>();
            for (String versionString : versions) {
                Version version = new Version(versionString);
                CheckSum checkSum = fakeChecksumFor(deployment.getContextRoot(), version);
                map.put(version, checkSum);
            }
            when(repository.availableVersionsFor(deployment.getCheckSum())).thenReturn(map);
        }
    }

    private OngoingDeploymentStub givenDeployment(ContextRoot contextRoot) {
        Deployment deployment = deploymentFor(contextRoot);
        installedDeployments.add(deployment);
        when(repository.getByChecksum(fakeChecksumFor(contextRoot))).thenReturn(deploymentFor(contextRoot));
        when(container.getDeploymentWith(contextRoot)).thenReturn(deployment);
        return new OngoingDeploymentStub(deploymentFor(contextRoot, fakeVersionFor(contextRoot)));
    }

    private static String deploymentXml(ContextRoot contextRoot) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" //
                + "<deployment>\n" //
                + "    <name>" + nameFor(contextRoot) + "</name>\n" //
                + "    <contextRoot>" + contextRoot + "</contextRoot>\n" //
                // base64 as the @XmlSchemaType(name = "hexBinary") doesn't seem to work
                + "    <checkSum>" + fakeChecksumFor(contextRoot).base64() + "</checkSum>\n" //
                + "    <version>" + fakeVersionFor(contextRoot) + "</version>\n" //
                + "</deployment>\n";
    }

    private void assertVersions(ContextRoot contextRoot, Map<Version, CheckSum> actual, String... versions) {
        assertEquals(versions.length, actual.size());
        int i = 0;
        for (Entry<Version, CheckSum> entry : actual.entrySet()) {
            Version expected = new Version(versions[i]);
            assertEquals("version#" + i, expected, entry.getKey());
            assertEquals("checkSum#" + i, fakeChecksumFor(contextRoot, expected), entry.getValue());
            i++;
        }
    }

    // TODO test JSON marshalling
    @Test
    @Ignore
    @SuppressWarnings("unused")
    public void shouldMarshalDeploymentAsJson() throws Exception {
        Deployment deployment = deploymentFor(FOO);

        String json = null; // new ObjectMapper().writeValueAsString(deployment);

        assertEquals(deploymentJson(FOO), json);
    }

    // TODO test JSON unmarshaling
    @Test
    @Ignore
    @SuppressWarnings("unused")
    public void shouldUnmarshalDeploymentFromJson() throws Exception {
        String json = deploymentJson(FOO);

        Deployment deployment = null; // new ObjectMapper().readValue(json, Deployment.class);

        assertDeployment(FOO, deployment);
    }

    @Test
    public void shouldMarshalDeploymentAsXml() {
        Deployment deployment = deploymentFor(FOO);
        StringWriter xml = new StringWriter();

        JAXB.marshal(deployment, xml);

        assertEquals(deploymentXml(FOO), xml.toString());
    }

    @Test
    public void shouldUnmarshalDeploymentFromXml() {
        String xml = deploymentXml(FOO);

        Deployment deployment = JAXB.unmarshal(new StringReader(xml), Deployment.class);

        assertDeployment(FOO, deployment);
    }

    @Test
    public void shouldGetAllDeployments() {
        givenDeployment(FOO);
        givenDeployment(BAR);

        List<Deployment> list = deployments.getAllDeployments();

        assertEquals(2, list.size());
        assertDeployment(FOO, list.get(0));
        assertDeployment(BAR, list.get(1));
    }

    @Test
    public void shouldGetDeploymentByContextRootMatrix() {
        givenDeployment(FOO).availableVersions("1.3.1");

        DeploymentResource deployment = deployments.deploymentSubResourceByContextRoot(FOO);

        assertEquals("1.3.1", deployment.getVersion().toString());
        assertVersions(deployment.getContextRoot(), deployment.getAvailableVersions(), "1.3.1");
    }

    @Test
    public void shouldGetDeploymentVersions() {
        String[] versions = { "1.2.6", "1.2.7", "1.2.8-SNAPSHOT", "1.3.0", "1.3.1", "1.3.2" };
        givenDeployment(FOO).availableVersions(versions);

        DeploymentResource deployment = deployments.deploymentSubResourceByContextRoot(FOO);

        assertVersions(FOO, deployment.getAvailableVersions(), versions);
    }
}
