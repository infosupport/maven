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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;

/**
 * Handles the import of dependency management from other models into the target model.
 *
 * @author Benjamin Bentmann
 */
@Named
@Singleton
public class DefaultDependencyManagementImporter
    implements DependencyManagementImporter
{

    @Override
    public void importManagement( Model target, List<? extends DependencyManagement> sources,
                                  ModelBuildingRequest request, ModelProblemCollector problems )
    {
        if ( sources != null && !sources.isEmpty() )
        {
            Map<String, Dependency> dependencies = new LinkedHashMap<>();

            DependencyManagement depMgmt = target.getDependencyManagement();

            if ( depMgmt != null )
            {
                for ( Dependency dependency : depMgmt.getDependencies() )
                {
                    dependencies.put( dependency.getManagementKey(), dependency );
                }
            }
            else
            {
                depMgmt = new DependencyManagement();
                target.setDependencyManagement( depMgmt );
            }

            for ( DependencyManagement source : sources )
            {
                for ( Dependency dependency : source.getDependencies() )
                {
                    String key = dependency.getManagementKey();
                    if ( !dependencies.containsKey( key ) )
                    {
                        if ( request.isLocationTracking() )
                        {
                            dependencies.put( key, withUpdatedReferences( dependency, depMgmt ) );
                        }
                        else
                        {
                            dependencies.put( key, dependency );
                        }
                    }
                }
            }

            depMgmt.setDependencies( new ArrayList<>( dependencies.values() ) );
        }
    }

    // TODO this whole block should be Modello-generated!
    private static final List<String> DEPENDENCY_FIELDS = Arrays.asList( "", "groupId", "artifactId", "version", "type", "classifier", "scope", "systemPath", "exclusions", "optional" );

    /**
     * Return an updated copy of dependency, populated with the trail of dependency imports that lead to this dependency
     * being present.
     * @param dependency The dependency from the project object model
     * @param source The dependency management that brought the dependency into view
     * @return A copy of the original dependency; {@link Dependency#location} and {@link InputLocation#referencedBy}
     * contain the path of dependency management blocks that determine how the dependency is eventually built up.
     */
    private Dependency withUpdatedReferences( Dependency dependency, DependencyManagement source ) {
        final Dependency result = dependency.clone();

        DEPENDENCY_FIELDS.forEach( field -> {
            // We do not know or care which part of the dependencyManagement block exactly was used.
            final InputLocation dependencyDeclarationLocation = source.getLocation( "" );
            final InputLocation fieldLocation = dependency.getLocation( field );

            if ( fieldLocation != null )
            {
                // Check if the location for this field is 'referenced by' another location. If this is not the case,
                // the field (and hence the dependency) are listed in the 'source' dependency management. We initiate
                // the trail by populating the first 'referencedBy' pointer.
                if ( fieldLocation.getReferencedBy() == null )
                {
                    // Make a copy of the location, so we do not mutate other places where this dependency may be in
                    // use.
                    final InputLocation copyOfTargetLocation = fieldLocation.clone();
                    copyOfTargetLocation.setReferencedBy( dependencyDeclarationLocation );
                    result.setLocation( field, copyOfTargetLocation );
                    return;
                }

                // The location of the field of the dependency is referenced from else. We chase down the trail of
                // references to find the end of it.
                InputLocation referringDependencyLocation = fieldLocation;
                InputLocation ownerOfReferringDependencyLocation = fieldLocation;
                while ( referringDependencyLocation.getReferencedBy() != null )
                {
                    ownerOfReferringDependencyLocation = referringDependencyLocation;
                    referringDependencyLocation =  referringDependencyLocation.getReferencedBy();
                }

                // The location that refers to the dependency is actually the place where the dependency is declared.
                // Make a copy of it (again, to prevent other uses of the dependency from being mutated) and update
                // referenced by so we know why it was imported.
                final InputLocation copyOfTargetLocation = referringDependencyLocation.clone();
                copyOfTargetLocation.setReferencedBy( dependencyDeclarationLocation );
                ownerOfReferringDependencyLocation.setReferencedBy( copyOfTargetLocation );
            }
        } );

        return result;
    }
}
