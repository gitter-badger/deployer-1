package com.github.t1.deployer.model;

import static javax.xml.bind.annotation.XmlAccessType.*;

import java.util.List;

import javax.xml.bind.annotation.*;

import lombok.*;
import lombok.experimental.Wither;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Value
@AllArgsConstructor
@XmlRootElement
@XmlAccessorType(FIELD)
public class Deployment implements Comparable<Deployment> {
    public static final String NEW_DEPLOYMENT_PATH = "!";
    private static final DeploymentName NEW_DEPLOYMENT_NAME = new DeploymentName(NEW_DEPLOYMENT_PATH);
    public static final Deployment NEW_DEPLOYMENT = new Deployment(NEW_DEPLOYMENT_NAME, null, null, null, null);

    @Wither
    private final DeploymentName name;
    private final ContextRoot contextRoot;
    private final CheckSum checkSum;

    @Wither
    private Version version;
    @Wither
    private List<VersionInfo> availableVersions;

    /** required by JAXB, etc. */
    @SuppressWarnings("unused")
    private Deployment() {
        this.name = null;
        this.contextRoot = null;
        this.checkSum = null;
        this.version = null;
        this.availableVersions = null;
    }

    public Deployment(ContextRoot contextRoot) {
        this(null, contextRoot, null, null, null);
    }

    public Deployment(DeploymentName name, ContextRoot contextRoot, CheckSum checkSum, Version version) {
        this(name, contextRoot, checkSum, version, null);
    }

    @Override
    public int compareTo(Deployment that) {
        return this.name.getValue().compareToIgnoreCase(that.name.getValue());
    }

    @JsonIgnore
    public boolean isNew() {
        return NEW_DEPLOYMENT_NAME.equals(name);
    }

    @Override
    public String toString() {
        return name + "(" + contextRoot + ")";
    }
}
