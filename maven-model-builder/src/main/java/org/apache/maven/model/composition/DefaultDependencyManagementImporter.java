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
package org.apache.maven.model.composition;

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.*;

import org.apache.maven.api.model.*;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;

/**
 * Handles the import of dependency management from other models into the target model.
 *
 */
@Named
@Singleton
public class DefaultDependencyManagementImporter implements DependencyManagementImporter {

    @Override
    public Model importManagement(
            Model target,
            List<? extends DependencyManagement> sources,
            ModelBuildingRequest request,
            ModelProblemCollector problems) {
        if (sources != null && !sources.isEmpty()) {
            Map<String, Dependency> dependencies = new LinkedHashMap<>();

            DependencyManagement depMgmt = target.getDependencyManagement();

            if (depMgmt != null) {
                for (Dependency dependency : depMgmt.getDependencies()) {
                    dependencies.put(dependency.getManagementKey(), dependency);
                }
            } else {
                depMgmt = DependencyManagement.newInstance();
            }

            for (DependencyManagement source : sources) {
                for (Dependency dependency : source.getDependencies()) {
                    String key = dependency.getManagementKey();
                    dependencies.putIfAbsent(key, dependency);

                    if (request.isLocationTracking()) {
                        Dependency updatedDependency = updateWithImportedFrom(dependency, source);
                        dependencies.put(key, updatedDependency);
                    }
                }
            }

            return target.withDependencyManagement(depMgmt.withDependencies(dependencies.values()));
        }
        return target;
    }

    static Dependency updateWithImportedFrom(Dependency dependency, DependencyManagement bom) {
        // We are only interested in the InputSource, so the location of the <dependency> element is sufficient
        InputLocation dependencyLocation = dependency.getLocation("");
        InputLocation bomLocation = bom.getLocation("");

        if (dependencyLocation == null || bomLocation == null) {
            return dependency;
        }

        InputSource dependencySource = dependencyLocation.getSource();
        InputSource bomSource = bomLocation.getSource();

        // If the dependency and BOM have the same source, it means we found the root where the dependency is declared.
        if (dependencySource == null
                || bomSource == null
                || Objects.equals(dependencySource.getModelId(), bomSource.getModelId())) {
            return Dependency.newBuilder(dependency, true)
                    .importedFrom(bomLocation)
                    .build();
        }

        // TODO: determine function of the following code
        while (dependencySource.getImportedFrom() != null) {
            InputLocation importedFrom = dependencySource.getImportedFrom();

            // Stop if the BOM is already in the list, no update necessary
            if (Objects.equals(importedFrom.getSource().getModelId(), bomSource.getModelId())) {
                return dependency;
            }

            dependencySource = importedFrom.getSource();
        }

        // We modify the input location that is used for the whole file.
        // This is likely correct because the POM hierarchy applies to the whole POM, not just one dependency.
        // TODO What to do now?!

        // Create copy of bomLocation and set importedFrom with the value of dependency.getimportedFrom()
        return Dependency.newBuilder(dependency, true)
                .importedFrom(new InputLocation(bomLocation, dependency.getImportedFrom()))
                .build();
    }
}
