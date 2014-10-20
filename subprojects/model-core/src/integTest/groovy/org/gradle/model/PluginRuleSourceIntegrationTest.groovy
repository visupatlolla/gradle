/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl

class PluginRuleSourceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    def "plugin class can expose model rules"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Model
                    List strings() {
                      []
                    }

                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks, List strings) {
                        tasks.create("value") {
                            it.doLast {
                                println "value: $strings"
                            }
                        }
                    }

                }
            }

            apply plugin: MyPlugin

            model {
                strings {
                    add "foo"
                }
            }
        '''

        then:
        succeeds "value"

        and:
        output.contains "value: [foo]"
    }

    def "configuration in script is not executed if not needed"() {
        when:
        buildScript '''
            import org.gradle.model.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Model
                    List strings() {
                      []
                    }
                }
            }

            apply plugin: MyPlugin

            def called = false

            model {
                strings {
                    // this strategy for detecting if this was called might not work when we lock down outside access in rules
                    called = true
                    add "foo"
                }
            }

            task value {
                doFirst { println "called: $called" }
            }
        '''

        then:
        succeeds "value"

        and:
        output.contains "called: false"
    }

    def "informative error message when rules are invalid"() {
        when:
        buildScript """
            import org.gradle.model.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                class Rules {
                }
            }

            apply plugin: MyPlugin
        """

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Failed to apply plugin [class 'MyPlugin']")
        failure.assertHasCause("Type MyPlugin\$Rules is not a valid model rule source: enclosed classes must be static and non private")
    }

    def "informative error message when two plugins declare model at the same path"() {
        when:
        buildScript """
            import org.gradle.model.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Model
                    String string() { "foo" }
                }
            }

            class MyOtherPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Model
                    String string() { "foo" }
                }
            }

            apply plugin: MyPlugin
            apply plugin: MyOtherPlugin
        """

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Failed to apply plugin [class 'MyOtherPlugin']")
        failure.assertHasCause("Cannot register model creation rule 'MyOtherPlugin\$Rules#string()' for path 'string' as the rule 'MyPlugin\$Rules#string()' is already registered to create a model element at this path")
    }

    def "informative error message when two plugins declare model at the same path and model is already created"() {
        when:
        buildScript '''
            import org.gradle.model.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Model
                    String string() { "foo" }
                }
            }

            class MyOtherPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Model
                    String string() { "bar" }
                }
            }

            apply plugin: MyPlugin

            model {
                tasks {
                    $("string")
                }
            }

            task loadPlugin {
                doLast {
                    apply plugin: MyOtherPlugin
                }
            }
        '''

        then:
        fails "loadPlugin"

        and:
        failure.assertHasCause("Failed to apply plugin [class 'MyOtherPlugin']")
        failure.assertHasCause("Cannot register model creation rule 'MyOtherPlugin\$Rules#string()' for path 'string' as the rule 'MyPlugin\$Rules#string()' is already registered (and the model element has been created)")
    }

    def "informative error message when creation rule throws"() {
        when:
        buildScript '''
            import org.gradle.model.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Model
                    String string() { throw new RuntimeException("oh no!") }
                }
            }

            apply plugin: MyPlugin

            model {
                tasks {
                    $("string")
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin\$Rules#string()")
        failure.assertHasCause("oh no!")
    }

    def "informative error message when dsl mutation rule throws"() {
        when:
        buildScript '''
            import org.gradle.model.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Model
                    String string() { "foo" }
                }
            }

            apply plugin: MyPlugin

            model {
                string {
                    throw new RuntimeException("oh no!")
                }
                tasks {
                    $("string")
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: model.string")
        failure.assertHasCause("oh no!")
    }

    def "model creator must provide instance"() {
        when:
        buildScript '''
            import org.gradle.model.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Model
                    String string() {
                      null
                    }
                }
            }

            apply plugin: MyPlugin

            model {
                tasks {
                    $("string")
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("error executing model rule: MyPlugin\$Rules#string() - rule returned null")
    }

    def "plugin applied by plugin can contribute rules"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyBasePlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Mutate
                    void strings(List<String> strings) {
                      strings << "foo"
                    }
                }
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.plugins.apply(MyBasePlugin)
                }

                @RuleSource
                static class Rules {
                    @Model
                    List<String> strings() {
                      []
                    }

                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks, List strings) {
                        tasks.create("value") {
                            it.doLast {
                                println "value: $strings"
                            }
                        }
                    }
                }
            }

            apply plugin: MyPlugin
        '''

        then:
        succeeds "value"

        and:
        output.contains "value: [foo]"
    }

    def "configuration made to a project extension during afterEvaluate() is visible to rule sources"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyExtension {
                String value = "original"
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.extensions.create("myExtension", MyExtension)
                }

                @RuleSource
                static class Rules {
                    @Model
                    MyExtension myExtension(ExtensionContainer extensions) {
                        extensions.getByType(MyExtension)
                    }

                    @Model
                    String value(MyExtension myExtension) {
                        myExtension.value
                    }

                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks, String value) {
                        tasks.create("value") {
                            it.doLast {
                                println "value: $value"
                            }
                        }
                    }
                }
            }

            apply plugin: MyPlugin

            project.afterEvaluate {
                project.myExtension.value = "configured"
            }
        '''

        then:
        succeeds "value"

        and:
        output.contains "value: configured"
    }

    def "rule can depend on a concrete task type"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                }

                @RuleSource
                static class Rules {
                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks, Exec execTask) {
                        tasks.create("name") {
                            it.doLast {
                                println "name: ${execTask.name}"
                            }
                        }
                    }
                }
            }

            apply plugin: MyPlugin

            task injected(type: Exec)
        '''

        then:
        succeeds "name"

        and:
        output.contains "name: injected"
    }
}
