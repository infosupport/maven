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
package org.apache.maven.it;

import java.io.File;
import java.io.IOException;

import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.util.ResourceExtractor;
import org.junit.jupiter.api.Test;

public class MavenITmng7045DropUselessAndOutdatedCdiApiTest extends AbstractMavenIntegrationTestCase {

    public MavenITmng7045DropUselessAndOutdatedCdiApiTest() {
        super("[3.8.3,)");
    }

    @Test
    public void testShouldNotLeakCdiApi() throws IOException, VerificationException {
        // in test Groovy 4.x is used which requires JDK 1.8, so simply skip it for older JDKs
        requiresJavaVersion("[1.8,)");

        File testDir = ResourceExtractor.simpleExtractResources(getClass(), "/mng-7045");
        Verifier verifier = newVerifier(testDir.getAbsolutePath());

        verifier.addCliArgument("process-classes");
        verifier.execute();
        verifier.verifyErrorFreeLog();
    }
}
