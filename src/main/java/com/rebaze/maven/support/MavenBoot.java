package com.rebaze.maven.support;

import com.google.inject.AbstractModule;
import org.apache.maven.Maven;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.event.DefaultEventSpyContext;
import org.apache.maven.cli.logging.Slf4jLoggerManager;
import org.apache.maven.cli.logging.Slf4jStdoutLogger;
import org.apache.maven.eventspy.internal.EventSpyDispatcher;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.extension.internal.CoreExports;
import org.apache.maven.extension.internal.CoreExtensionEntry;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.toolchain.building.ToolchainsBuilder;
import org.codehaus.plexus.*;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.logging.LoggerManager;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import java.io.File;
import java.util.*;

/**
 * A custom maven booter for quick maven extension testing.
 */
public class MavenBoot
{
    private ILoggerFactory slf4jLoggerFactory = LoggerFactory.getILoggerFactory();
    private LoggerManager plexusLoggerManager = new Slf4jLoggerManager();
    private org.slf4j.Logger slf4jLogger = new Slf4jStdoutLogger();;

    private PlexusContainer container()
        throws Exception
    {
        ClassWorld classWorld = new ClassWorld( "plexus.core", Thread.currentThread().getContextClassLoader() );

        ClassRealm coreRealm = null;
        coreRealm = classWorld.getClassRealm( "plexus.core" );
        if ( coreRealm == null )
        {
            coreRealm = classWorld.getRealms().iterator().next();
        }

        List<File> extClassPath = new ArrayList<File>();

        CoreExtensionEntry coreEntry = CoreExtensionEntry.discoverFrom( coreRealm );

        // here we load extensions configured before.
        List<CoreExtensionEntry> extensions = loadCoreExtensions( coreRealm, coreEntry.getExportedArtifacts() );

        ClassRealm containerRealm = setupContainerRealm( classWorld, coreRealm, extClassPath, extensions );

        ContainerConfiguration cc = new DefaultContainerConfiguration()
            .setClassWorld( classWorld )
            .setRealm( containerRealm )
            .setClassPathScanning( PlexusConstants.SCANNING_INDEX )
            .setAutoWiring( true )
            .setName( "maven" );

        Set<String> exportedArtifacts = new HashSet<String>( coreEntry.getExportedArtifacts() );
        Set<String> exportedPackages = new HashSet<String>( coreEntry.getExportedPackages() );
        for ( CoreExtensionEntry extension : extensions )
        {
            exportedArtifacts.addAll( extension.getExportedArtifacts() );
            exportedPackages.addAll( extension.getExportedPackages() );
        }

        final CoreExports exports = new CoreExports( containerRealm, exportedArtifacts, exportedPackages );

        DefaultPlexusContainer container = new DefaultPlexusContainer( cc, new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind( ILoggerFactory.class ).toInstance( slf4jLoggerFactory );
                bind( CoreExports.class ).toInstance( exports );
            }
        } );

        // NOTE: To avoid inconsistencies, we'll use the TCCL exclusively for lookups
        container.setLookupRealm( null );

        container.setLoggerManager( plexusLoggerManager );

        for ( CoreExtensionEntry extension : extensions )
        {
            container.discoverComponents( extension.getClassRealm() );
        }

        container.getLoggerManager().setThresholds( 0 );

        Thread.currentThread().setContextClassLoader( container.getContainerRealm() );

        EventSpyDispatcher eventSpyDispatcher = container.lookup( EventSpyDispatcher.class );

        DefaultEventSpyContext eventSpyContext = new DefaultEventSpyContext();
        Map<String, Object> data = eventSpyContext.getData();
        Properties userProperties = new Properties();
        Properties systemProperties = new Properties();
        data.put( "plexus", container );
        data.put( "workingDirectory", "target/mavenrun/" );
        data.put( "systemProperties", systemProperties );
        data.put( "userProperties", userProperties );
        data.put( "versionProperties", getVersionProperties() );
        eventSpyDispatcher.init( eventSpyContext );

        // refresh logger in case container got customized by spy
        slf4jLogger = slf4jLoggerFactory.getLogger( this.getClass().getName() );

        Maven maven = container.lookup( Maven.class );

        MavenExecutionRequestPopulator executionRequestPopulator = container.lookup( MavenExecutionRequestPopulator.class );

        ModelProcessor modelProcessor =  container.lookup( ModelProcessor.class );

        Map<String, ConfigurationProcessor> configurationProcessors = container.lookupMap( ConfigurationProcessor.class );

        ToolchainsBuilder toolchainsBuilder = container.lookup( ToolchainsBuilder.class );

        DefaultSecDispatcher dispatcher = ( DefaultSecDispatcher ) container.lookup( SecDispatcher.class, "maven" );

        return container;
    }

    private Properties getVersionProperties()
    {
        Properties p = new Properties(  );
        p.setProperty( "buildNumber","1");
        p.setProperty( "timestamp",System.currentTimeMillis()+"");
        p.setProperty( "version","0");
        p.setProperty( "distributionId","RMaven");
        p.setProperty( "distributionShortName","RMaven");
        p.setProperty( "distributionName","RMaven");
        return p;
    }

    private List<CoreExtensionEntry> loadCoreExtensions( ClassRealm coreRealm, Set<String> exportedArtifacts )
    {
        return Collections.emptyList();
    }

    private ClassRealm setupContainerRealm( ClassWorld classWorld, ClassRealm coreRealm, List<File> extClassPath,
        List<CoreExtensionEntry> extensions )
        throws Exception
    {
        if ( !extClassPath.isEmpty() || !extensions.isEmpty() )
        {
            ClassRealm extRealm = classWorld.newRealm( "maven.ext", null );

            extRealm.setParentRealm( coreRealm );


            for ( File file : extClassPath )
            {
                extRealm.addURL( file.toURI().toURL() );
            }

            for ( CoreExtensionEntry entry : reverse( extensions ) )
            {
                Set<String> exportedPackages = entry.getExportedPackages();
                ClassRealm realm = entry.getClassRealm();
                for ( String exportedPackage : exportedPackages )
                {
                    extRealm.importFrom( realm, exportedPackage );
                }
                if ( exportedPackages.isEmpty() )
                {
                    // sisu uses realm imports to establish component visibility
                    extRealm.importFrom( realm, realm.getId() );
                }
            }

            return extRealm;
        }

        return coreRealm;
    }

    private static <T> List<T> reverse( List<T> list )
    {
        List<T> copy = new ArrayList<T>( list );
        Collections.reverse( copy );
        return copy;
    }

    /**
     * Main interaction.
     *
     * @param request
     * @return
     * @throws Exception
     */
    public MavenExecutionResult maven( MavenExecutionRequest request ) throws Exception
    {
        PlexusContainer container = container();
        Maven maven = container.lookup( Maven.class );
        return maven.execute( request );
    }
}
