package com.github.t1.deployer.app;

import static com.github.t1.deployer.TestData.*;
import static com.github.t1.deployer.model.Deployment.*;
import static com.github.t1.deployer.repository.ArtifactoryMock.*;
import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import io.dropwizard.testing.junit.DropwizardClientRule;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;

import lombok.extern.java.Log;

import org.glassfish.hk2.api.*;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.filter.LoggingFilter;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.Matchers;

import com.github.t1.deployer.app.file.DeploymentListFile;
import com.github.t1.deployer.app.html.DeploymentHtmlWriter;
import com.github.t1.deployer.container.*;
import com.github.t1.deployer.model.*;
import com.github.t1.deployer.repository.Repository;
import com.github.t1.deployer.tools.*;
import com.github.t1.rest.RestResource;

@Log
public class DeploymentsIT {
    private static DeploymentContainer container = mock(DeploymentContainer.class);
    private static Repository repository = mock(Repository.class);
    private static Audit audit = mock(Audit.class);
    private static DeploymentListFile deploymentListFile = mock(DeploymentListFile.class);
    private static Principal principal = mock(Principal.class);

    private static DeploymentContainer interceptedContainer = InterceptorMock.intercept(container).with(interceptor());

    private static DeploymentOperationInterceptor interceptor() {
        DeploymentOperationInterceptor interceptor = new DeploymentOperationInterceptor();
        interceptor.audit = audit;
        interceptor.deploymentsList = deploymentListFile;
        interceptor.principal = principal;
        return interceptor;
    }

    @ClassRule
    public static DropwizardClientRule deployer = new DropwizardClientRule( //
            new Deployments(), //
            new LoggingFilter(log, true), //
            new DeploymentHtmlWriter(), //
            new AbstractBinder() {
                @Override
                protected void configure() {
                    bind(repository).to(Repository.class);
                    bind(interceptedContainer).to(DeploymentContainer.class);
                    bind(audit).to(Audit.class);
                    bind(principal).to(Principal.class);

                    final UriInfo uriInfo = mock(UriInfo.class);
                    bindUriBuilder(uriInfo);

                    bind(new FactoryInstance<>(new Factory<DeploymentResource>() {
                        @Override
                        public DeploymentResource provide() {
                            // User.setCurrent(new User("the-prince").withPrivilege("deploy", "redeploy", "undeploy"));
                            DeploymentResource result = new DeploymentResource();
                            result.container = interceptedContainer;
                            result.repository = repository;
                            result.uriInfo = uriInfo;
                            return result;
                        }

                        @Override
                        public void dispose(DeploymentResource instance) {}
                    })).to(new TypeLiteral<javax.enterprise.inject.Instance<DeploymentResource>>() {});

                    bind(deploymentListFile).to(DeploymentListFile.class);
                }

                private void bindUriBuilder(final UriInfo uriInfo) {
                    UriBuilder uriBuilder = mock(UriBuilder.class);
                    when(uriInfo.getBaseUriBuilder()).thenReturn(uriBuilder);
                    when(uriBuilder.path(Matchers.any(Class.class))).thenReturn(uriBuilder);
                    when(uriBuilder.path(anyString())).thenReturn(uriBuilder);
                    when(uriBuilder.build()).thenReturn(URI.create("http://no.where"));
                    when(uriBuilder.matrixParam(anyString(), Matchers.any(Object[].class))).thenReturn(uriBuilder);
                }
            });

    @Before
    public void before() {
        reset(container, repository, audit, principal);
        when(principal.getName()).thenReturn("jbossadmin");
    }

    @After
    public void after() {
        verifyNoMoreInteractions(audit);
    }

    @Rule
    public TestWatcher logRule = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            log.info("starting: " + description.getMethodName());
        }

        @Override
        protected void finished(Description description) {
            log.info("finishing: " + description.getMethodName());
        }
    };

    private WebTarget deploymentsWebTarget(ContextRoot contextRoot) {
        return deploymentsWebTarget().matrixParam("context-root", contextRoot);
    }

    private WebTarget deploymentsWebTarget() {
        return deployer() //
                .register(new LoggingFilter(log, false)) //
                .path("deployments");
    }

    private WebTarget deployer() {
        URI baseUri = deployer.baseUri();
        // URI baseUri = URI.create("http://localhost:8080/deployer/");
        return ClientBuilder.newClient().target(baseUri);
    }

    private RestResource deploymentsRestResource(ContextRoot contextRoot) {
        return deploymentsRestResource().matrix("context-root", contextRoot);
    }

    private RestResource deploymentsRestResource() {
        return deployerRestResource() //
                // .register(new LoggingFilter(log, false)) //
                .path("deployments");
    }

    private RestResource deployerRestResource() {
        URI baseUri = deployer.baseUri();
        // URI baseUri = URI.create("http://localhost:8080/deployer/");
        return new RestResource(baseUri);
    }


    private void given(ContextRoot... contextRoots) {
        givenDeployments(repository, contextRoots);
        givenDeployments(container, contextRoots);
    }

    @Test
    public void shouldGetAllDeployments() {
        given(FOO, BAR);

        Response response = deployer() //
                .path("deployments/*") //
                .request(APPLICATION_JSON_TYPE) //
                .get();

        assertStatus(OK, response);
        List<Deployment> deployments = response.readEntity(new GenericType<List<Deployment>>() {});
        assertEquals(2, deployments.size());

        Deployment foo = deployments.get(0);
        assertEquals(FOO, foo.getContextRoot());
        assertEquals(CURRENT_FOO_VERSION, foo.getVersion());

        Deployment bar = deployments.get(1);
        assertEquals(BAR, bar.getContextRoot());
        assertEquals(CURRENT_BAR_VERSION, bar.getVersion());
    }

    @Test
    public void shouldGetDeploymentByContextRoot() {
        given(FOO, BAR);

        Deployment deployment = deploymentsRestResource(FOO) //
                .accept(Deployment.class) //
                .get();

        assertDeployment(FOO, deployment);
    }

    @Test
    @Ignore("sub resources after matrix params don't seem to work in Dropwizard")
    public void shouldGetAvailableVersions() {
        given(FOO, BAR);

        Response response = deploymentsWebTarget(FOO) //
                .path("available-versions") //
                .request(APPLICATION_JSON_TYPE) //
                .get();

        assertStatus(OK, response);
        List<Version> versions = response.readEntity(new GenericType<List<Version>>() {});
        assertEquals(FOO_VERSIONS.toString(), versions.toString());
    }

    @Test
    public void shouldDeploy() {
        givenDeployments(repository, FOO, BAR);
        givenDeployments(container, BAR);

        WebTarget uri = deploymentsWebTarget(FOO);
        Response response = uri //
                .request(APPLICATION_JSON_TYPE) //
                .put(Entity.json(deploymentJson(FOO, CURRENT_FOO_VERSION)));

        assertStatus(CREATED, response);
        assertEquals(uri.getUri(), response.getLocation());
        verify(container).deploy(deploymentFor(FOO), inputStreamFor(FOO, CURRENT_FOO_VERSION));
        verify(audit).allow("deploy", FOO, CURRENT_FOO_VERSION);
    }

    @Test
    public void shouldUpgrade() {
        given(FOO, BAR);

        WebTarget uri = deploymentsWebTarget(FOO);
        Response response = uri //
                .request(APPLICATION_JSON_TYPE) //
                .put(Entity.json(deploymentJson(FOO, NEWEST_FOO_VERSION)));

        assertStatus(NO_CONTENT, response);
        verify(audit).allow("redeploy", FOO, NEWEST_FOO_VERSION);
        verify(container).redeploy(deploymentFor(FOO, NEWEST_FOO_VERSION), inputStreamFor(FOO, NEWEST_FOO_VERSION));
    }

    @Test
    public void shouldUndeploy() {
        given(FOO, BAR);

        Response response = deploymentsWebTarget(FOO) //
                .request(APPLICATION_JSON_TYPE) //
                .delete();

        assertStatus(NO_CONTENT, response);
        verify(audit).allow("undeploy", FOO, CURRENT_FOO_VERSION);
    }

    @Test
    public void shouldPostDeploy() {
        givenDeployments(repository, FOO, BAR);
        givenDeployments(container, BAR);

        Response response = deploymentsWebTarget().request() //
                .post(Entity.form(new Form("action", "deploy") //
                        .param("checksum", fakeChecksumFor(FOO, CURRENT_FOO_VERSION).toString()) //
                        ));

        assertStatus(OK, response); // redirected
        verify(container).deploy(deploymentFor(FOO), inputStreamFor(FOO, CURRENT_FOO_VERSION));
        verify(audit).allow("deploy", FOO, CURRENT_FOO_VERSION);
    }

    @Test
    public void shouldPostRedeploy() {
        given(FOO, BAR);

        Response response = deploymentsWebTarget().request() //
                .post(Entity.form(deploymentForm("redeploy", FOO, NEWEST_FOO_VERSION)));

        assertStatus(OK, response); // redirected
        verify(audit).allow("redeploy", FOO, NEWEST_FOO_VERSION);
        verify(container).redeploy(deploymentFor(FOO, NEWEST_FOO_VERSION), inputStreamFor(FOO, NEWEST_FOO_VERSION));
    }

    @Test
    public void shouldPostUndeploy() {
        given(FOO, BAR);

        // RestResource resource = deploymentsRestResource(FOO).post();
        Response response = deploymentsWebTarget(FOO) //
                .request() //
                .post(Entity.form(deploymentForm("undeploy", FOO, NEWEST_FOO_VERSION)));

        assertStatus(OK, response); // redirected
        verify(audit).allow("undeploy", FOO, CURRENT_FOO_VERSION);
    }

    @Test
    public void shouldGetDeploymentsForm() {
        Response response = deployer() //
                .path("deployments").path(NEW_DEPLOYMENT_PATH) //
                .request(TEXT_HTML) //
                .get();

        assertStatus(OK, response);
        assertThat(
                response.readEntity(String.class),
                allOf(containsString("Add Deployment"), containsString("<form"),
                        containsString("name=\"action\" value=\"deploy\"")));
    }
}
