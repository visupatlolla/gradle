/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * by Szczepan Faber, created at: 11/21/12
 */
class MyTest extends AbstractIntegrationSpec {

    def "foo"() {
        settingsFile << "include 'api', 'impl'"

        file("api/src/main/java/Person.java") << """public interface Person {
    String getName();
}
"""
        file("impl/src/main/java/PersonImpl.java") << """public class PersonImpl implements Person {
    public String getName() {
        return "Szczepan";
    }
}
"""

        buildFile << """
allprojects {
    task foo
}
"""

        file("api/build.gradle") << """
apply plugin: 'java'
task foo2
"""
        file("impl/build.gradle") << """
apply plugin: 'java'
dependencies {
    compile project(":api")
}
"""

//        when:
//        def result = executer.withArguments("-i", "-m").withTasks(":api:foo", ":api:build").run()
//
//        then:
//        result

        when:
        def result2 = executer.withArguments("-i", "-m").withTasks(":impl:build").run()

        then:
        result2
    }
}