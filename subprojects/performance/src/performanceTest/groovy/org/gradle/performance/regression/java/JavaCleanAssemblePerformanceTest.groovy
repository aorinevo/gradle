/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.regression.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario

import static org.gradle.performance.annotations.ScenarioType.TEST
import static org.gradle.performance.generator.JavaTestProjectGenerator.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProjectGenerator.LARGE_MONOLITHIC_JAVA_PROJECT
import static org.gradle.performance.generator.JavaTestProjectGenerator.MEDIUM_JAVA_COMPOSITE_BUILD
import static org.gradle.performance.generator.JavaTestProjectGenerator.MEDIUM_JAVA_PREDEFINED_COMPOSITE_BUILD
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor([
    @Scenario(type = TEST, oses = [LINUX], testProjects = [LARGE_JAVA_MULTI_PROJECT, LARGE_MONOLITHIC_JAVA_PROJECT, MEDIUM_JAVA_COMPOSITE_BUILD, MEDIUM_JAVA_PREDEFINED_COMPOSITE_BUILD])
])
class JavaCleanAssemblePerformanceTest extends AbstractCrossVersionPerformanceTest {

    def "clean assemble"() {
        given:
        runner.gradleOpts = runner.projectMemoryOptions
        runner.warmUpRuns = 2
        runner.runs = 6
        runner.tasksToRun = ["clean", "assemble"]
        runner.targetVersions = ["6.7-20200824220048+0000"]
        runner.minimumBaseVersion = runner.testProject.contains("Composite") ? "4.0" : null

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
