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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil

class CustomComponentPluginIntegrationTest extends AbstractIntegrationSpec {
    def "setup"() {
        buildFile << """
import org.gradle.model.*
import org.gradle.model.collection.*

interface SampleComponent extends ComponentSpec {}
class DefaultSampleComponent extends BaseComponentSpec implements SampleComponent {}
"""
    }

    def "plugin declares custom component"() {
        when:
        buildWithCustomComponentPlugin()
        and:
        buildFile << """
task checkModel << {
    assert project.componentSpecs.size() == 1
    def sampleLib = project.componentSpecs.sampleLib
    assert sampleLib instanceof SampleComponent
    assert sampleLib.projectPath == project.path
    assert sampleLib.displayName == "DefaultSampleComponent 'sampleLib'"
}
"""
        then:
        succeeds "checkModel"
    }

    def "can register custom component model without creating"() {
        when:
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }
            }
        }

        apply plugin:MySamplePlugin

        task checkModel << {
            assert project.componentSpecs.size() == 0
        }
"""

        then:
        succeeds "checkModel"
    }

    def "custom component listed in components report"() {
        given:
        buildWithCustomComponentPlugin()
        when:
        succeeds "components"
        then:
        output.contains(TextUtil.toPlatformLineSeparators(""":components

------------------------------------------------------------
Root project
------------------------------------------------------------

DefaultSampleComponent 'sampleLib'
----------------------------------

Source sets
    No source sets.

Binaries
    No binaries.

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL"""))
    }

    def "can have component declaration and creation in separate plugins"() {
        when:
        buildFile << """
        class MyComponentDeclarationModel implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }
            }
        }

        class MyComponentCreationPlugin implements Plugin<Project> {
            void apply(final Project project) {
                project.apply(plugin:MyComponentDeclarationModel)
            }

            @RuleSource
            static class Rules {
                @Mutate
                void createSampleComponentComponents(CollectionBuilder<SampleComponent> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }

            }
        }

        apply plugin:MyComponentCreationPlugin

        task checkModel << {
             assert project.componentSpecs.size() == 1
             def sampleLib = project.componentSpecs.sampleLib
             assert sampleLib instanceof SampleComponent
             assert sampleLib.projectPath == project.path
             assert sampleLib.displayName == "DefaultSampleComponent 'sampleLib'"
        }
"""
        then:
        succeeds "checkModel"
    }

    def "Can define and create multiple component types in the same plugin"(){
        when:
        buildFile << """
        interface SampleLibrary extends LibrarySpec {}
        class DefaultSampleLibrary extends BaseComponentSpec implements SampleLibrary {}

        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }

                @ComponentType
                void registerAnother(ComponentTypeBuilder<SampleLibrary> builder) {
                    builder.defaultImplementation(DefaultSampleLibrary)
                }

                @Mutate
                void createSampleComponentInstances(CollectionBuilder<SampleComponent> componentSpecs) {
                    componentSpecs.create("sampleComponent")
                }

                @Mutate
                void createSampleLibraryInstances(CollectionBuilder<SampleLibrary> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }
            }
        }

        apply plugin:MySamplePlugin

        task checkModel << {
             assert project.componentSpecs.size() == 2

             def sampleComponent = project.componentSpecs.sampleComponent
             assert sampleComponent instanceof SampleComponent
             assert sampleComponent.projectPath == project.path
             assert sampleComponent.displayName == "DefaultSampleComponent 'sampleComponent'"

             def sampleLib = project.componentSpecs.sampleLib
             assert sampleLib instanceof SampleLibrary
             assert sampleLib.projectPath == project.path
             assert sampleLib.displayName == "DefaultSampleLibrary 'sampleLib'"
        }
"""
        then:
        succeeds "checkModel"
    }

    def "reports failure for invalid component type method"() {
        given:
        settingsFile << """rootProject.name = 'custom-component'"""
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder, String illegalOtherParameter) {
                }
            }
        }

        apply plugin:MySamplePlugin
"""

        when:
        fails "tasks"

        then:
        failure.assertHasDescription "A problem occurred evaluating root project 'custom-component'."
        failure.assertHasCause "Failed to apply plugin [class 'MySamplePlugin']"
        failure.assertHasCause "MySamplePlugin\$Rules#register(org.gradle.platform.base.ComponentTypeBuilder<SampleComponent>, java.lang.String) is not a valid component model rule method."
        failure.assertHasCause "Method annotated with @ComponentType must have a single parameter of type 'org.gradle.platform.base.ComponentTypeBuilder'."
    }

    def "cannot register same component type multiple times"(){
        given:
        buildWithCustomComponentPlugin()
        and:
        buildFile << """
        class MyOtherPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules1 {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }
            }
        }

        apply plugin:MyOtherPlugin
"""
        when:
        fails "tasks"
        then:
        failure.assertHasDescription "A problem occurred configuring root project 'custom-component'."
        failure.assertHasCause "Exception thrown while executing model rule: MyOtherPlugin\$Rules1#register(org.gradle.platform.base.ComponentTypeBuilder<SampleComponent>)"
        failure.assertHasCause "Cannot register a factory for type SampleComponent because a factory for this type was already registered by MySamplePlugin\$Rules#register(org.gradle.platform.base.ComponentTypeBuilder<SampleComponent>)."
    }

    def buildWithCustomComponentPlugin() {
        settingsFile << """rootProject.name = 'custom-component'"""
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }
                @Mutate
                void createSampleComponentComponents(CollectionBuilder<SampleComponent> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }
            }
        }

        apply plugin:MySamplePlugin
        """
    }
}
