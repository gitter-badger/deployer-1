package com.github.t1.deployer;

import static javax.ws.rs.core.Response.Status.*;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

import javax.annotation.PreDestroy;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;

import lombok.*;

public class ArtifactoryRepository implements Repository {
    private final Client webClient = ClientBuilder.newClient();
    private final WebTarget artifactory;

    public ArtifactoryRepository(URI baseUri) {
        this.artifactory = webClient.target(baseUri).path("artifactory");
    }

    @PreDestroy
    void close() {
        webClient.close();
    }

    @Data
    @NoArgsConstructor
    @org.codehaus.jackson.annotate.JsonIgnoreProperties(ignoreUnknown = true)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class FolderInfo {
        List<FolderInfoChild> children;
    }

    @Data
    @NoArgsConstructor
    @org.codehaus.jackson.annotate.JsonIgnoreProperties(ignoreUnknown = true)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class FolderInfoChild {
        boolean folder;
        URI uri;
    }

    @Override
    public List<Version> availableVersionsFor(CheckSum checkSum) {
        URI uri = searchByChecksum(checkSum).getUri();
        UriBuilder uriBuilder = UriBuilder.fromUri(uri).replacePath(versionsFolder(uri));

        Response response = webClient.target(uriBuilder) //
                .request().get();

        if (OK.getStatusCode() != response.getStatus())
            throw new RuntimeException("error from repository: " + response.getStatusInfo() + ": " + uriBuilder);
        return versionsIn(response.readEntity(FolderInfo.class));
    }

    private String versionsFolder(URI uri) {
        Path path = path(uri);
        int length = path.getNameCount();
        return path.subpath(0, length - 2).toString();
    }

    private List<Version> versionsIn(FolderInfo folderInfo) {
        List<Version> result = new ArrayList<>();
        for (FolderInfoChild child : folderInfo.getChildren()) {
            if (child.isFolder()) {
                String version = child.getUri().getPath();
                if (version.startsWith("/"))
                    version = version.substring(1);
                result.add(new Version(version));
            }
        }
        return result;
    }

    @Data
    @NoArgsConstructor
    @org.codehaus.jackson.annotate.JsonIgnoreProperties(ignoreUnknown = true)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChecksumSearchResult {
        List<ChecksumSearchResultItem> results;
    }

    @Data
    @NoArgsConstructor
    @org.codehaus.jackson.annotate.JsonIgnoreProperties(ignoreUnknown = true)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChecksumSearchResultItem {
        URI uri;
        URI downloadUri;
    }

    private ChecksumSearchResultItem searchByChecksum(CheckSum checkSum) {
        Response response = artifactory //
                .path("/api/search/checksum") //
                .queryParam("md5", checkSum.hexString()) //
                .request() //
                .get();

        if (OK.getStatusCode() != response.getStatus())
            throw new RuntimeException("error from repository: " + response.getStatusInfo());
        List<ChecksumSearchResultItem> results = response.readEntity(ChecksumSearchResult.class).getResults();
        if (results.size() == 0)
            throw new RuntimeException("checksum not found in repository: " + checkSum);
        if (results.size() > 1)
            throw new RuntimeException("checksum not unique in repository: " + checkSum);
        return results.get(0);
    }

    /**
     * It's not really nice to get the version out of the repo path, but where else would I get it? Even the
     * <code>X-Result-Detail</code> header doesn't provide it.
     */
    @Override
    public Version getVersionByChecksum(CheckSum checkSum) {
        URI result = searchByChecksum(checkSum).getUri();
        Path path = path(result);
        int length = path.getNameCount();
        return new Version(path.getName(length - 2).toString());
    }

    private Path path(URI result) {
        return Paths.get(result.getPath());
    }

    @Override
    @SneakyThrows(IOException.class)
    public InputStream getArtifactInputStream(CheckSum checkSum) {
        URI uri = searchByChecksum(checkSum).getDownloadUri();
        return uri.toURL().openStream();
    }
}
