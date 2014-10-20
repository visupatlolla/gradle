/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.jvm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture

class ResourceOnlyJvmLibraryIntegrationTest extends AbstractIntegrationSpec {
    def "can define a library containing resources only"() {
        buildFile << """
plugins {
    id 'jvm-component'
    id 'jvm-resources'
}
jvm {
    libraries {
        myLib
    }
}

task check << {
    def myLib = jvm.libraries.myLib
    assert myLib instanceof JvmLibrarySpec

    assert myLib.sources.size() == 1
    assert myLib.sources.resources instanceof JvmResourceSet
    assert myLib.sources as Set == [sources.myLib.resources] as Set

    binaries.withType(JarBinarySpec) { jvmBinary ->
        assert jvmBinary.source == myLib.source
    }
}
"""

        expect:
        run 'check'
    }

    def "can build a library containing resources only"() {
        file("src/myLib/resources/org/gradle/thing.txt") << "hi"

        buildFile << """
plugins {
    id 'jvm-component'
    id 'jvm-resources'
}
jvm {
    libraries {
        myLib
    }
}
"""

        when:
        run 'assemble'

        then:
        def jar = jarFile('build/jars/myLibJar/myLib.jar')
        jar.hasDescendants("org/gradle/thing.txt")
        jar.assertFilePresent("org/gradle/thing.txt", "hi")
    }

    def "generated binary includes resources from all resource sets"() {
        file("src/myLib/resources/thing.txt") << "hi"
        file("src/myLib/other/org/gradle/thing.txt") << "hi"

        buildFile << """
plugins {
    id 'jvm-component'
    id 'jvm-resources'
}
jvm {
    libraries {
        myLib {
            sources {
                other(JvmResourceSet)
            }
        }
    }
}
"""

        when:
        run 'assemble'

        then:
        def jar = jarFile('build/jars/myLibJar/myLib.jar')
        jar.hasDescendants("thing.txt", "org/gradle/thing.txt")
        jar.assertFilePresent("thing.txt", "hi")
        jar.assertFilePresent("org/gradle/thing.txt", "hi")
    }

    private JarTestFixture jarFile(String s) {
        new JarTestFixture(file(s))
    }
}
