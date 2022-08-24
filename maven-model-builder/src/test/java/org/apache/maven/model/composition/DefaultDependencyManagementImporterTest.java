package org.apache.maven.model.composition;

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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultDependencyManagementImporterTest
{
    private final DependencyManagementImporter importer = new DefaultDependencyManagementImporter();

    @Test
    void should_copy_version_from_external_dependency_management_into_model()
    {
        Model model = new Model();
        model.setDependencies( Collections.singletonList( createDependency( "gId", "aId", null ) ) );

        DependencyManagement dependencyManagement = new DependencyManagement(); // fictitious external BOM
        dependencyManagement.addDependency(  createDependency( "gId", "aId", "1" ) );

        importer.importManagement( model, Collections.singletonList( dependencyManagement ), null, null );

        Dependency updatedDependency = model.getDependencyManagement().getDependencies().get( 0 );
        assertEquals( updatedDependency.getVersion(), "1" );
    }

    @Test
    void should_copy_location_references_from_external_dependency_management_into_model()
    {
        Model model = new Model();
        model.setDependencies( Collections.singletonList( createDependency( "gId", "aId", null ) ) );

        DependencyManagement dependencyManagement = new DependencyManagement(); // fictitious external BOM
        dependencyManagement.addDependency( withLocation(
                createDependency( "gId", "aId", "1" ),
                new InputLocation( 14, 27, createInputSource( "aId.pom", "gId:aId" )
        ) ) );

        importer.importManagement( model, Collections.singletonList( dependencyManagement ), null, null );

        Dependency updatedDependency = model.getDependencyManagement().getDependencies().get( 0 );
        assertEquals( updatedDependency.getLocation( "groupId" ).getSource().getModelId(), "gId:aId" );
        assertEquals( updatedDependency.getLocation( "artifactId" ).getSource().getModelId(), "gId:aId" );
        assertEquals( updatedDependency.getLocation( "version" ).getSource().getModelId(), "gId:aId:1" );
    }

    private Dependency createDependency(String gId, String aId, String v) {
        Dependency dependency = new Dependency();
        dependency.setGroupId( gId );
        dependency.setArtifactId( aId );
        dependency.setVersion( v );
        return dependency;
    }

    private InputSource createInputSource( String location, String modelId ) {
        InputSource inputSource = new InputSource();
        inputSource.setLocation( location );
        inputSource.setModelId( modelId );
        return inputSource;
    }

    private Dependency withLocation( Dependency dependency, InputLocation location ) {
        dependency.setLocation( "", location );
        return dependency;
    }
}