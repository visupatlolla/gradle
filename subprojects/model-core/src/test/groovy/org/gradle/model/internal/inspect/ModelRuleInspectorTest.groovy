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

package org.gradle.model.internal.inspect

import org.gradle.model.*
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor
import org.gradle.model.internal.manage.schema.extraction.InvalidManagedModelElementTypeException
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.registry.ModelRegistry
import spock.lang.Specification
import spock.lang.Unroll

class ModelRuleInspectorTest extends Specification {

    ModelRegistry registry = new DefaultModelRegistry()
    def registryMock = Mock(ModelRegistry)
    def inspector = new ModelRuleInspector(MethodRuleDefinitionHandler.CORE_HANDLERS)
    def dependencies = Mock(RuleSourceDependencies)

    static class ModelThing {
        final String name

        ModelThing(String name) {
            this.name = name
        }
    }

    static class EmptyClass {}

    def "can inspect class with no rules"() {
        when:
        inspector.inspect(EmptyClass, registryMock, dependencies)

        then:
        0 * registryMock._
    }

    static class SimpleModelCreationRuleInferredName {
        @Model
        static ModelThing modelPath() {
            new ModelThing("foo")
        }
    }

    def "can inspect class with simple model creation rule"() {
        when:
        inspector.inspect(SimpleModelCreationRuleInferredName, registry, dependencies)

        then:
        def state = registry.state(new ModelPath("modelPath"))
        state.status == ModelState.Status.PENDING

        def element = registry.get(ModelPath.path("modelPath"), ModelType.of(ModelThing))
        element.name == "foo"
    }

    static class ParameterizedModel {
        @Model
        List<String> strings() {
            Arrays.asList("foo")
        }

        @Model
        List<? super String> superStrings() {
            Arrays.asList("foo")
        }

        @Model
        List<? extends String> extendsStrings() {
            Arrays.asList("foo")
        }

        @Model
        List<?> wildcard() {
            Arrays.asList("foo")
        }
    }

    def "can inspect class with model creation rule for paramaterized type"() {
        when:
        inspector.inspect(ParameterizedModel, registry, dependencies)

        then:
        registry.element(ModelPath.path("strings")).promise.asReadOnly(new ModelType<List<String>>() {})
        registry.element(ModelPath.path("superStrings")).promise.asReadOnly(new ModelType<List<? super String>>() {})
        registry.element(ModelPath.path("extendsStrings")).promise.asReadOnly(new ModelType<List<? extends String>>() {})
        registry.element(ModelPath.path("wildcard")).promise.asReadOnly(new ModelType<List<?>>() {})
    }

    static class HasGenericModelRule {
        @Model
        static <T> List<T> thing() {
            []
        }
    }

    def "model creation rule cannot be generic"() {
        when:
        inspector.inspect(HasGenericModelRule, registry, dependencies)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == "$HasGenericModelRule.name#thing() is not a valid model rule method: cannot have type variables (i.e. cannot be a generic method)"
    }

    static class HasMultipleRuleAnnotations {
        @Model
        @Mutate
        static String thing() {
            ""
        }
    }

    def "model rule method cannot be annotated with multiple rule annotations"() {
        when:
        inspector.inspect(HasMultipleRuleAnnotations, registry, dependencies)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == "$HasMultipleRuleAnnotations.name#thing() is not a valid model rule method: can only be one of [annotated with @Model and returning a model element, @annotated with @Model and taking a managed model element, annotated with @Mutate, annotated with @Finalize]"
    }

    static class ConcreteGenericModelType {
        @Model
        static List<String> strings() {
            []
        }
    }

    def "type variables of model type are captured"() {
        when:
        inspector.inspect(ConcreteGenericModelType, registry, dependencies)
        def element = registry.element(new ModelPath("strings"))
        def type = element.adapter.asReadOnly(new ModelType<List<String>>() {}).type

        then:
        type.parameterized
        type.typeVariables[0] == ModelType.of(String)
    }

    static class ConcreteGenericModelTypeImplementingGenericInterface implements HasStrings<String> {
        @Model
        List<String> strings() {
            []
        }
    }

    def "type variables of model type are captured when method is generic in interface"() {
        when:
        inspector.inspect(ConcreteGenericModelTypeImplementingGenericInterface, registry, dependencies)
        def element = registry.element(new ModelPath("strings"))
        def type = element.adapter.asReadOnly(new ModelType<List<String>>() {}).type

        then:
        type.parameterized
        type.typeVariables[0] == ModelType.of(String)
    }

    static class HasRuleWithIdentityCrisis {
        @Mutate
        @Model
        void foo() {}
    }

    def "rule cannot be of more than one type"() {
        when:
        inspector.inspect(HasRuleWithIdentityCrisis, registry, dependencies)

        then:
        thrown InvalidModelRuleDeclarationException
    }

    static class GenericMutationRule {
        @Mutate
        <T> void mutate(T thing) {}
    }

    def "mutation rule cannot be generic"() {
        when:
        inspector.inspect(GenericMutationRule, registry, dependencies)

        then:
        thrown InvalidModelRuleDeclarationException
    }

    static class MutationRules {
        @Mutate
        static void mutate1(List<String> strings) {
            strings << "1"
        }

        @Mutate
        static void mutate2(List<String> strings) {
            strings << "2"
        }

        @Mutate
        static void mutate3(List<Integer> strings) {
            strings << 3
        }
    }

    // Not an exhaustive test of the mechanics of mutation rules, just testing the extraction and registration
    def "mutation rules are registered"() {
        given:
        def path = new ModelPath("strings")
        def type = new ModelType<List<String>>() {}

        // Have to make the inputs exist so the binding can be inferred by type
        // or, the inputs could be annotated with @Path
        registry.create(ModelCreators.of(ModelReference.of(path, type), []).simpleDescriptor("strings").build())

        when:
        inspector.inspect(MutationRules, registry, dependencies)

        then:
        registry.element(path).adapter.asReadOnly(type).instance.sort() == ["1", "2"]
    }

    static class MutationAndFinalizeRules {
        @Mutate
        static void mutate3(List<Integer> strings) {
            strings << 3
        }

        @Finalize
        static void finalize1(List<String> strings) {
            strings << "2"
        }

        @Mutate
        static void mutate1(List<String> strings) {
            strings << "1"
        }
    }

    // Not an exhaustive test of the mechanics of finalize rules, just testing the extraction and registration
    def "finalize rules are registered"() {
        given:
        def path = new ModelPath("strings")
        def type = new ModelType<List<String>>() {}

        // Have to make the inputs exist so the binding can be inferred by type
        // or, the inputs could be annotated with @Path
        registry.create(ModelCreators.of(ModelReference.of(path, type), []).simpleDescriptor("strings").build())

        when:
        inspector.inspect(MutationAndFinalizeRules, registry, dependencies)

        then:
        registry.element(path).adapter.asReadOnly(type).instance == ["1", "2"]
    }

    def "methods are processed ordered by their to string representation"() {
        given:
        def stringListType = new ModelType<List<String>>() {}
        def integerListType = new ModelType<List<Integer>>() {}

        registry.create(ModelCreators.of(ModelReference.of(ModelPath.path("strings"), stringListType), []).simpleDescriptor("strings").build())
        registry.create(ModelCreators.of(ModelReference.of(ModelPath.path("integers"), integerListType), []).simpleDescriptor("integers").build())

        when:
        inspector.inspect(MutationAndFinalizeRules, registryMock, dependencies)

        then:
        1 * registryMock.finalize({ it.descriptor == new MethodModelRuleDescriptor(MutationAndFinalizeRules.declaredMethods.find { it.name == "finalize1" }) })

        then:
        1 * registryMock.mutate({ it.descriptor == new MethodModelRuleDescriptor(MutationAndFinalizeRules.declaredMethods.find { it.name == "mutate1" }) })

        then:
        1 * registryMock.mutate({ it.descriptor == new MethodModelRuleDescriptor(MutationAndFinalizeRules.declaredMethods.find { it.name == "mutate3" }) })
    }

    static class InvalidModelNameViaAnnotation {
        @Model(" ")
        String foo() {
            "foo"
        }
    }

    def "invalid model name is not allowed"() {
        when:
        inspector.inspect(InvalidModelNameViaAnnotation, registry, dependencies)

        then:
        thrown InvalidModelRuleDeclarationException
    }

    static class NonManagedVoidReturning {
        @Model
        void bar(NonManaged foo) {
        }
    }

    def "type of the first argument of void returning model definition has to be @Managed annotated"() {
        when:
        inspector.inspect(NonManagedVoidReturning, registry, dependencies)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "$NonManagedVoidReturning.name#bar($NonManaged.name) is not a valid model rule method: a void returning model element creation rule has to take an instance of a $Managed.name annotated type as the first argument"
    }

    static class InvalidManagedVoidReturning {
        @Model
        void bar(InvalidManaged foo) {
        }
    }

    def "type of the first argument of void returning model definition has to be a valid managed type"() {
        when:
        inspector.inspect(InvalidManagedVoidReturning, registry, dependencies)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "Declaration of model rule $InvalidManagedVoidReturning.name#bar($InvalidManaged.name) is invalid."
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == "Invalid managed model type $InvalidManaged.name: must be defined as an interface"
    }

    static class NoArgumentVoidReturning {
        @Model
        void bar() {
        }
    }

    def "void returning model definition has to take at least one argument"() {
        when:
        inspector.inspect(NoArgumentVoidReturning, registry, dependencies)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "$NoArgumentVoidReturning.name#bar() is not a valid model rule method: a void returning model element creation rule has to take a managed model element instance as the first argument"
    }

    static class ManagedWithPropertyOfInvalidManagedTypeVoidReturning {
        @Model
        void bar(ManagedWithPropertyOfInvalidManagedType foo) {
        }
    }

    static class ManagedWithReferenceOfInvalidManagedTypeVoidReturning {
        @Model
        void bar(ManagedWithReferenceOfInvalidManagedType foo) {
        }
    }

    @Unroll
    def "void returning model definition with for a type with a property of invalid managed type - #inspected.simpleName"() {
        when:
        inspector.inspect(inspected, registry, dependencies)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "Declaration of model rule $inspected.name#bar($managedType.name) is invalid."
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == "Invalid managed model type $managedType.name: managed type of property 'invalidManaged' is invalid"
        e.cause.cause instanceof InvalidManagedModelElementTypeException
        e.cause.cause.message == "Invalid managed model type $InvalidManaged.name: must be defined as an interface"

        where:
        inspected                                             | managedType
        ManagedWithPropertyOfInvalidManagedTypeVoidReturning  | ManagedWithPropertyOfInvalidManagedType
        ManagedWithReferenceOfInvalidManagedTypeVoidReturning | ManagedWithReferenceOfInvalidManagedType
    }
}
