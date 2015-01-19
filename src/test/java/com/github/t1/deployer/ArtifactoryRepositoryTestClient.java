package com.github.t1.deployer;

import io.dropwizard.testing.junit.DropwizardClientRule;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

public class ArtifactoryRepositoryTestClient {
    private static final CheckSum CHECKSUM = CheckSum.ofHexString("120cdac84b8119934b394116bba32409894379a0");
    public static final URI ARTIFACTORY = URI.create("http://localhost:8081/artifactory");
    public static final URI ARTIFACTORY_MOCK = URI.create("http://localhost:8080/deployer/artifactory");

    private static final class Dropwizard extends DropwizardClientRule {
        private Dropwizard() {
            super(new ArtifactoryMock());
        }

        public Dropwizard start() throws Throwable {
            super.before();
            return this;
        }

        public void stop() {
            super.after();
        }
    }

    public static void main(String[] args) throws Throwable {
        Dropwizard dropwizard = new Dropwizard().start();
        URI uri = UriBuilder.fromUri(dropwizard.baseUri()).path("artifactory").build();

        ArtifactoryRepository repo = new ArtifactoryRepository(uri, null);

        try {
            List<Deployment> list = repo.availableVersionsFor(CHECKSUM);
            for (Deployment deployment : list) {
                System.out.println("-> " + deployment);
            }
        } finally {
            dropwizard.stop();
        }
    }
}