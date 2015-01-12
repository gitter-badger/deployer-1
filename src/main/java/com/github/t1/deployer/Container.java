package com.github.t1.deployer;

import static com.github.t1.deployer.WebException.*;
import static java.util.concurrent.TimeUnit.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.inject.Inject;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import org.jboss.as.controller.client.*;
import org.jboss.as.controller.client.helpers.standalone.*;
import org.jboss.dmr.ModelNode;

import com.github.t1.log.Logged;

@Slf4j
@Logged
public class Container {
    private abstract class AbstractPlan {
        public void execute() {
            try (ServerDeploymentManager deploymentManager = ServerDeploymentManager.Factory.create(client)) {
                DeploymentPlan plan = buildPlan(deploymentManager.newDeploymentPlan()).build();

                log.debug("start executing {}", getClass().getSimpleName());
                Future<ServerDeploymentPlanResult> future = deploymentManager.execute(plan);
                log.debug("wait for {}", getClass().getSimpleName());
                ServerDeploymentPlanResult result = future.get(30, SECONDS);
                log.debug("done executing {}", getClass().getSimpleName());

                checkOutcome(plan, result);
            } catch (IOException | ExecutionException | TimeoutException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        protected abstract DeploymentPlanBuilder buildPlan(InitialDeploymentPlanBuilder plan);

        private void checkOutcome(DeploymentPlan plan, ServerDeploymentPlanResult result) {
            boolean failed = false;
            Throwable firstThrowable = null;
            for (DeploymentAction action : plan.getDeploymentActions()) {
                ServerDeploymentActionResult actionResult = result.getDeploymentActionResult(action.getId());
                Throwable deploymentException = actionResult.getDeploymentException();
                if (deploymentException != null)
                    firstThrowable = deploymentException;
                switch (actionResult.getResult()) {
                    case CONFIGURATION_MODIFIED_REQUIRES_RESTART:
                        log.warn("requries restart: {}: {}", action.getType(), action.getDeploymentUnitUniqueName());
                        break;
                    case EXECUTED:
                        log.debug("executed: {}: {}", action.getType(), action.getDeploymentUnitUniqueName());
                        break;
                    case FAILED:
                        failed = true;
                        log.error("failed: {}: {}", action.getType(), action.getDeploymentUnitUniqueName());
                        break;
                    case NOT_EXECUTED:
                        log.debug("not executed: {}: {}", action.getType(), action.getDeploymentUnitUniqueName());
                        break;
                    case ROLLED_BACK:
                        log.debug("rolled back: {}: {}", action.getType(), action.getDeploymentUnitUniqueName());
                        break;
                }
            }
            if (firstThrowable != null || failed) {
                throw new RuntimeException("failed to execute " + getClass().getSimpleName(), firstThrowable);
            }
        }
    }

    @AllArgsConstructor
    private class DeployPlan extends AbstractPlan {
        private final String contextRoot;
        private final InputStream inputStream;

        @Override
        protected DeploymentPlanBuilder buildPlan(InitialDeploymentPlanBuilder plan) {
            return plan //
                    .add(contextRoot, inputStream) //
                    .deploy(contextRoot) //
            ;
        }
    }

    @AllArgsConstructor
    private class ReplacePlan extends AbstractPlan {
        private final String contextRoot;
        private final InputStream inputStream;

        @Override
        protected DeploymentPlanBuilder buildPlan(InitialDeploymentPlanBuilder plan) {
            return plan.replace(contextRoot, inputStream);
        }
    }

    @AllArgsConstructor
    private class UndeployPlan extends AbstractPlan {
        private final String deploymentName;

        @Override
        protected DeploymentPlanBuilder buildPlan(InitialDeploymentPlanBuilder plan) {
            return plan //
                    .undeploy(deploymentName) //
                    .remove(deploymentName) //
            ;
        }
    }

    private static final OperationMessageHandler LOGGING = new OperationMessageHandler() {
        @Override
        public void handleReport(MessageSeverity severity, String message) {
            switch (severity) {
                case ERROR:
                    log.error(message);
                case WARN:
                    log.warn(message);
                    break;
                case INFO:
                    log.info(message);
                    break;
            }
        }
    };

    @Inject
    ModelControllerClient client;

    @Inject
    Repository repository;

    @SneakyThrows(IOException.class)
    private ModelNode execute(ModelNode command) {
        log.debug("execute command {}", command);
        ModelNode result = client.execute(command, LOGGING);
        log.trace("-> {}", result);
        return result;
    }

    private void checkOutcome(ModelNode result) {
        String outcome = result.get("outcome").asString();
        if (!"success".equals(outcome)) {
            throw new RuntimeException("outcome " + outcome + ": " + result.get("failure-description"));
        }
    }

    public Deployment getDeploymentByContextRoot(String contextRoot) {
        List<Deployment> all = getAllDeployments();
        Deployment deployment = find(all, contextRoot);
        log.debug("found deployment {}", deployment);
        return deployment;
    }

    private Deployment find(List<Deployment> all, String contextRoot) {
        for (Deployment deployment : all) {
            if (deployment.getContextRoot().equals(contextRoot)) {
                return deployment;
            }
        }
        throw notFound("no deployment with context root [" + contextRoot + "]");
    }

    public List<Deployment> getAllDeployments() {
        List<Deployment> list = new ArrayList<>();
        for (ModelNode cliDeploymentMatch : readAllDeployments())
            list.add(toDeployment(cliDeploymentMatch.get("result")));
        return list;
    }

    private List<ModelNode> readAllDeployments() {
        ModelNode result = execute(readDeployments());
        checkOutcome(result);
        return result.get("result").asList();
    }

    private static ModelNode readDeployments() {
        ModelNode node = new ModelNode();
        node.get("address").add("deployment", "*");
        node.get("operation").set("read-resource");
        node.get("recursive").set(true);
        return node;
    }

    private Deployment toDeployment(ModelNode cliDeployment) {
        String name = cliDeployment.get("name").asString();
        String contextRoot = getContextRoot(cliDeployment);
        CheckSum hash = CheckSum.of(cliDeployment.get("content").get(0).get("hash").asBytes());
        log.debug("{} -> {} -> {}", name, contextRoot, hash);
        return new Deployment(name, contextRoot, hash);
    }

    private String getContextRoot(ModelNode cliDeployment) {
        ModelNode subsystems = cliDeployment.get("subsystem");
        // JBoss 8 uses 'undertow' while JBoss 7 uses 'web'
        ModelNode web = (subsystems.has("web")) ? subsystems.get("web") : subsystems.get("undertow");
        ModelNode contextRoot = web.get("context-root");
        return contextRoot.isDefined() ? contextRoot.asString().substring(1) : "?";
    }

    public void deploy(String contextRoot, InputStream deployment) {
        new ReplacePlan(contextRoot, deployment).execute();
    }

    public void undeploy(String deploymentName) {
        new UndeployPlan(deploymentName).execute();
    }
}