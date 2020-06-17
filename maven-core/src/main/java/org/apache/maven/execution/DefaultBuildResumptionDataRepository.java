package org.apache.maven.execution;

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

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

/**
 * This implementation of {@link BuildResumptionDataRepository} persists information in a properties file. The file is
 * stored in the build output directory under the Maven execution root.
 */
@Named
@Singleton
public class DefaultBuildResumptionDataRepository implements BuildResumptionDataRepository
{
    private static final String RESUME_PROPERTIES_FILENAME = "resume.properties";
    private static final String RESUME_FROM_PROPERTY = "resumeFrom";
    private static final String EXCLUDED_PROJECTS_PROPERTY = "excludedProjects";
    private static final String PROPERTY_DELIMITER = ", ";
    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultBuildResumptionDataRepository.class );

    @Override
    public boolean persistResumptionData( MavenProject rootProject, BuildResumptionData buildResumptionData )
            throws BuildResumptionPersistenceException
    {
        Properties properties = convertToProperties( buildResumptionData );

        Path resumeProperties = Paths.get( rootProject.getBuild().getDirectory(), RESUME_PROPERTIES_FILENAME );
        try
        {
            Files.createDirectories( resumeProperties.getParent() );
            try ( Writer writer = Files.newBufferedWriter( resumeProperties ) )
            {
                properties.store( writer, null );
            }
        }
        catch ( IOException e )
        {
            String message = "Could not create " + RESUME_PROPERTIES_FILENAME + " file.";
            throw new BuildResumptionPersistenceException( message, e );
        }

        return true;
    }

    private Properties convertToProperties( final BuildResumptionData buildResumptionData )
    {
        Properties properties = new Properties();
        properties.setProperty( RESUME_FROM_PROPERTY, buildResumptionData.getResumeFrom() );
        String excludedProjects = String.join( PROPERTY_DELIMITER, buildResumptionData.getProjectsToSkip() );
        properties.setProperty( EXCLUDED_PROJECTS_PROPERTY, excludedProjects );

        return properties;
    }

    @Override
    public void applyResumptionData( MavenExecutionRequest request, MavenProject rootProject )
    {
        Properties properties = loadResumptionFile( Paths.get( rootProject.getBuild().getDirectory() ) );
        applyResumptionProperties( request, properties );
    }

    @Override
    public void removeResumptionData( MavenProject rootProject )
    {
        Path resumeProperties = Paths.get( rootProject.getBuild().getDirectory(), RESUME_PROPERTIES_FILENAME );
        try
        {
            Files.deleteIfExists( resumeProperties );
        }
        catch ( IOException e )
        {
            LOGGER.warn( "Could not delete {} file. ", RESUME_PROPERTIES_FILENAME, e );
        }
    }

    private Properties loadResumptionFile( Path rootBuildDirectory )
    {
        Properties properties = new Properties();
        Path path = Paths.get( RESUME_PROPERTIES_FILENAME ).resolve( rootBuildDirectory );
        if ( !Files.exists( path ) )
        {
            LOGGER.warn( "The {} file does not exist. The --resume / -r feature will not work.", path );
            return properties;
        }

        try ( Reader reader = Files.newBufferedReader( path ) )
        {
            properties.load( reader );
        }
        catch ( IOException e )
        {
            LOGGER.warn( "Unable to read {}. The --resume / -r feature will not work.", path );
        }

        return properties;
    }

    // This method is made package-private for testing purposes
    void applyResumptionProperties( MavenExecutionRequest request, Properties properties )
    {
        if ( properties.containsKey( RESUME_FROM_PROPERTY ) && StringUtils.isEmpty( request.getResumeFrom() ) )
        {
            String propertyValue = properties.getProperty( RESUME_FROM_PROPERTY );
            request.setResumeFrom( propertyValue );
            LOGGER.info( "Resuming from {} due to the --resume / -r feature.", propertyValue );
        }

        if ( properties.containsKey( EXCLUDED_PROJECTS_PROPERTY ) )
        {
            String propertyValue = properties.getProperty( EXCLUDED_PROJECTS_PROPERTY );
            String[] excludedProjects = propertyValue.split( PROPERTY_DELIMITER );
            request.getExcludedProjects().addAll( Arrays.asList( excludedProjects ) );
            LOGGER.info( "Additionally excluding projects '{}' due to the --resume / -r feature.", propertyValue );
        }
    }
}
