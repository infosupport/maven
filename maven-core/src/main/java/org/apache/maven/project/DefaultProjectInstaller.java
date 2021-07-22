package org.apache.maven.project;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.eclipse.aether.impl.Installer;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * TODO: add comments
 */
@Named
@Singleton
public class DefaultProjectInstaller implements ProjectInstaller
{
    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultProjectInstaller.class );

    private final Installer installer;

    @Inject
    public DefaultProjectInstaller( Installer installer )
    {
        this.installer = installer;
    }

    public void install( final ProjectBuildingRequest buildingRequest, final MavenProject project )
            throws NoFileAssignedException, ArtifactInstallerException
    {
        if ( buildingRequest == null )
        {
            throw new IllegalArgumentException( "The parameter buildingRequest is not allowed to be null." );
        }

        Artifact artifact = project.getArtifact();
        String packaging = project.getPackaging();
        File pomFile = project.getFile();

        List<Artifact> attachedArtifacts = project.getAttachedArtifacts();

        // TODO: push into transformation
        boolean isPomArtifact = "pom".equals( packaging );

        if ( isPomArtifact )
        {
            if ( pomFile != null )
            {
                ProjectArtifact mainArtifact = new ProjectArtifact( project );
                installMavenArtifacts( buildingRequest, Collections.singletonList( mainArtifact ) );
            }
        }
        else
        {
            if ( pomFile != null )
            {
                ProjectArtifactMetadata metadata = new ProjectArtifactMetadata( artifact, pomFile );
                artifact.addMetadata( metadata );
            }

            File file = artifact.getFile();

            // Here, we have a temporary solution to MINSTALL-3 (isDirectory() is true if it went through compile
            // but not package). We are designing in a proper solution for Maven 2.1
            if ( file != null && file.isFile() )
            {
                installMavenArtifacts( buildingRequest, Collections.singletonList( artifact ) );
            }
            else if ( !attachedArtifacts.isEmpty() )
            {
                throw new NoFileAssignedException( "The packaging plugin for this project did not assign "
                        + "a main file to the project but it has attachments. Change packaging to 'pom'." );
            }
            else
            {
                throw new NoFileAssignedException( "The packaging for this project did not assign a file to the "
                        + "build artifact" );
            }
        }

        for ( Artifact attached : attachedArtifacts )
        {
            LOGGER.debug( "Installing artifact: {}", attached.getId() );
            installMavenArtifacts( buildingRequest, Collections.singletonList( attached ) );
        }

        // TODO: Should this method rather return all the Artifacts and only call installMavenArtifacts once with it?
    }

    private void installMavenArtifacts( ProjectBuildingRequest buildingRequest, Collection<Artifact> mavenArtifacts )
            throws ArtifactInstallerException
    {
        // prepare installRequest
        InstallRequest request = new InstallRequest();

        // transform artifacts
        for ( Artifact mavenArtifact : mavenArtifacts )
        {
            request.addArtifact( RepositoryUtils.toArtifact( mavenArtifact ) );

            for ( ArtifactMetadata metadata : mavenArtifact.getMetadataList() )
            {
                if ( metadata instanceof ProjectArtifactMetadata )
                {
                    org.eclipse.aether.artifact.Artifact pomArtifact = new SubArtifact(
                            RepositoryUtils.toArtifact( mavenArtifact ), "", "pom" );
                    pomArtifact = pomArtifact.setFile( ( (ProjectArtifactMetadata) metadata ).getFile() );
                    request.addArtifact( pomArtifact );
                }
                else if ( // GroupRepositoryMetadata
                        metadata instanceof ArtifactRepositoryMetadata )
                {
                    // eaten, handled by repo system
                }
//                else if ( metadata instanceof GroupRepositoryMetadata )
//                {
                    // TODO - implement following to resolve MNG-7055?
//                }
            }
        }

        // install
        try
        {
            installer.install( buildingRequest.getRepositorySession(), request );
        }
        catch ( InstallationException e )
        {
            throw new ArtifactInstallerException( e.getMessage(), e );
        }
    }
}
