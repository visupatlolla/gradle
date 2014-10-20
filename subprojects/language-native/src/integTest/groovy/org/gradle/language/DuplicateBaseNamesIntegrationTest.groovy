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

package org.gradle.language

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.language.fixtures.app.DuplicateAssemblerBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateCBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateCppBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateMixedSameBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateObjectiveCBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateObjectiveCppBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateWindowsResourcesBaseNamesTestApp
import org.gradle.nativeplatform.fixtures.app.SourceFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VisualCpp

// TODO add coverage for mixed sources
class DuplicateBaseNamesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def "can have sourcefiles with same base name but different directories"() {
        setup:
        testApp.writeSources(file("src/main"))
        buildFile.text = ""
        testApp.plugins.each{ plugin ->
            buildFile << "apply plugin: '$plugin'\n"
        }

        buildFile << """
        binaries.all{
            linker.args "-v"
        }
        """
        buildFile << """
            model {
                platforms {
                    x86 {
                        architecture "i386"
                    }
                }
            }
            executables {
                main {}
            }

            """
        expect:
        succeeds "mainExecutable"
        executable("build/binaries/mainExecutable/main").exec().out == expectedOutput
        where:
        testApp                                              |   expectedOutput
        new DuplicateCBaseNamesTestApp()                     |    "foo1foo2"
        new DuplicateCppBaseNamesTestApp()                   |    "foo1foo2"
        new DuplicateAssemblerBaseNamesTestApp(AbstractInstalledToolChainIntegrationSpec.toolChain)    |    "foo1foo2"
        new DuplicateMixedSameBaseNamesTestApp(AbstractInstalledToolChainIntegrationSpec.toolChain)    |    "fooFromC\nfooFromCpp\nfooFromAsm\n"
    }

    /**
     * TODO: need filter declaration to get this passed. Remove filter once
     * story-language-source-sets-filter-source-files-by-file-extension
     * is implemented
     * */
    def "can have sourcefiles with same base name in same directory"() {
        setup:
        def testApp = new DuplicateMixedSameBaseNamesTestApp(AbstractInstalledToolChainIntegrationSpec.toolChain)


        testApp.getSourceFiles().each {  SourceFile sourceFile ->
            file("src/main/all/${sourceFile.name}") << sourceFile.content
        }

        testApp.headerFiles.each { SourceFile sourceFile ->
            file("src/main/headers/${sourceFile.name}") << sourceFile.content
        }

        buildFile.text = ""
        testApp.plugins.each { plugin ->
            buildFile << "apply plugin: '$plugin'\n"
        }

        buildFile << """
executables {
    main {
        sources {"""

        testApp.functionalSourceSets.each { name, filterPattern ->
                buildFile << """
            $name {
                source {
                    include '$filterPattern'
                    srcDirs "src/main/all"
                }
            }"""
        }

        buildFile << """
        }
    }
}"""

        buildFile << """

        binaries.all {
            linker.args "-v"
        }

        model {
            platforms {
                x86 {
                    architecture "i386"
                }
            }
        }

        """
        expect:
        succeeds "mainExecutable"
        executable("build/binaries/mainExecutable/main").exec().out == "fooFromC\nfooFromCpp\nfooFromAsm\n"
    }

    //TODO Rene: inline with testcase above once we got coverage for objective-c and objective-cpp on windows
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "can have objectiveC and objectiveCpp source files with same name in different directories"(){
        setup:
        testApp.writeSources(file("src/main"))
        buildFile.text = ""
        testApp.plugins.each{ plugin ->
            buildFile << "apply plugin: '$plugin'\n"
        }
        buildFile << testApp.extraConfiguration

        buildFile << """
            executables {
                main {}
            }

            """
        expect:
        succeeds "mainExecutable"
        executable("build/binaries/mainExecutable/main").exec().out == "foo1foo2"
        where:
        testApp << [ new DuplicateObjectiveCBaseNamesTestApp(), new DuplicateObjectiveCppBaseNamesTestApp() ]
    }

    @RequiresInstalledToolChain(VisualCpp)
    def "windows-resources can have sourcefiles with same base name but different directories"() {
        setup:
        def testApp = new DuplicateWindowsResourcesBaseNamesTestApp();
        testApp.writeSources(file("src/main"))
        buildFile.text = ""
        testApp.plugins.each{ plugin ->
            buildFile << "apply plugin: '$plugin'\n"
        }
        buildFile <<"""
            binaries.all {
                linker.args "user32.lib"
            }
            executables {
                main {}
            }
            """
        expect:
        succeeds "mainExecutable"
        executable("build/binaries/mainExecutable/main").exec().out == "foo1foo2"
    }
}