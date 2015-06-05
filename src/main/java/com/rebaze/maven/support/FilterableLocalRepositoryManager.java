/*******************************************************************************
 * Copyright (c) 2015 Rebaze GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache Software License v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/
 * <p/>
 * Contributors:
 * Rebaze
 *******************************************************************************/
package com.rebaze.maven.support;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Delegating repository manager that passes forward all requests. It only records requested
 * artifacts in a recording logfile.
 *
 */
public class FilterableLocalRepositoryManager implements LocalRepositoryManager {

    private final LocalRepositoryManager m_delegate;
    private boolean m_readonly = false;
    private boolean m_blocked = false;

    public FilterableLocalRepositoryManager( LocalRepositoryManager delegate, LocalRepository repository, RepositorySystemSession session ) {
        m_delegate = delegate;
    }

    public void setReadonly(boolean readonly) {
        m_readonly = readonly;
    }

    public void setBlocked(boolean blocked) {
        m_blocked = blocked;
    }

    @Override
    public LocalArtifactResult find(RepositorySystemSession repositorySystemSession, LocalArtifactRequest localArtifactRequest) {
        if (m_blocked)
        {
            LocalArtifactResult res = new LocalArtifactResult( localArtifactRequest );
            res.setAvailable( false );
            res.setFile( null );
            res.setRepository( null );
            return res;
        }
        return m_delegate.find(repositorySystemSession, localArtifactRequest);
    }

    @Override
    public void add(RepositorySystemSession repositorySystemSession, LocalArtifactRegistration localArtifactRegistration) {
        if (!m_blocked  && !m_readonly )
        {
            m_delegate.add(repositorySystemSession, localArtifactRegistration);
        }
    }

    @Override
    public LocalMetadataResult find(RepositorySystemSession repositorySystemSession, LocalMetadataRequest localMetadataRequest) {
        if (m_blocked)
        {
            LocalMetadataResult res = new LocalMetadataResult( localMetadataRequest );
            res.setStale( false );
            res.setFile( null );
            return res;
        }
        return m_delegate.find( repositorySystemSession, localMetadataRequest );
    }

    @Override
    public void add(RepositorySystemSession repositorySystemSession, LocalMetadataRegistration localMetadataRegistration) {
        if (!m_blocked  && !m_readonly )
        {
            m_delegate.add( repositorySystemSession, localMetadataRegistration );
        }
    }

    @Override
    public LocalRepository getRepository() {
        return m_delegate.getRepository();
    }

    @Override
    public String getPathForLocalArtifact(Artifact artifact) {
        // safe according to interface documentation.
        return m_delegate.getPathForLocalArtifact(artifact);
    }

    @Override
    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository remoteRepository, String s) {
        // safe according to interface documentation.
        return m_delegate.getPathForRemoteArtifact(artifact, remoteRepository, s);
    }

    @Override
    public String getPathForLocalMetadata(Metadata metadata) {
        // safe according to interface documentation.
        return m_delegate.getPathForLocalMetadata(metadata);
    }

    @Override
    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository remoteRepository, String s) {
        // safe according to interface documentation.
        return m_delegate.getPathForRemoteMetadata(metadata, remoteRepository, s);
    }
}
