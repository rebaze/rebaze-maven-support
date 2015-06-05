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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;

/**
 * Connects access to the local repository with auxis.
 */
@Named("filterableLocalRepositoryFactory")
public class FilterableLocalRepositoryFactory implements LocalRepositoryManagerFactory {

    @Inject
    private List<LocalRepositoryManagerFactory> localRepositoryManagerFactories;

    @Inject
    private Logger logger;

    volatile private FilterableLocalRepositoryManager m_instance;

    synchronized public LocalRepositoryManager newInstance(RepositorySystemSession session, LocalRepository repository) throws NoLocalRepositoryManagerException {
        m_instance = new FilterableLocalRepositoryManager(evictDelegateManager().newInstance(session, repository), repository, session);
        logger.info("Set Local repository instance: " + m_instance );
        return m_instance;
    }

    private LocalRepositoryManagerFactory evictDelegateManager() {
        LocalRepositoryManagerFactory secondLeader = null;
        for (LocalRepositoryManagerFactory locals : localRepositoryManagerFactories) {
            if (locals.getClass().equals(FilterableLocalRepositoryFactory.class))
                continue;
            if (secondLeader == null) {
                secondLeader = locals;
            }
            else {
                if (secondLeader.getPriority() < locals.getPriority()) {
                    secondLeader = locals;
                }
            }
        }
        return secondLeader;
    }

    public synchronized FilterableLocalRepositoryManager getFilterableLocalRepository() {
        return m_instance;
    }

    public float getPriority() {
        return 100;
    }

}
