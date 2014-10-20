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

package org.gradle.nativeplatform.internal

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.nativeplatform.DependentSourceSet
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.nativeplatform.*
import org.gradle.nativeplatform.internal.resolve.NativeBinaryResolveResult
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.DefaultArchitecture
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.platform.base.ComponentSpecIdentifier
import org.gradle.platform.base.internal.BinaryNamingScheme
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class NativeBinarySpecTest extends Specification {
    def flavor1 = new DefaultFlavor("flavor1")
    def id = new DefaultComponentSpecIdentifier("project", "name")
    def sourceSet = new DefaultFunctionalSourceSet("testFunctionalSourceSet", new DirectInstantiator())
    def component = new TestNativeComponentSpec(id, sourceSet)

    def toolChain1 = Stub(NativeToolChainInternal) {
        getName() >> "ToolChain1"
    }
    def platform1 = Stub(NativePlatform) {
        getArchitecture() >> new DefaultArchitecture("i386", ArchitectureInternal.InstructionSet.X86, 64)
    }
    def buildType1 = Stub(BuildType) {
        getName() >> "BuildType1"
    }
    def resolver = Mock(NativeDependencyResolver)

    def "binary uses source from its owner component"() {
        given:
        def binary = testBinary(component)
        def sourceSet = Stub(LanguageSourceSet)

        when:
        component.sources.add(sourceSet)

        then:
        binary.source.contains(sourceSet)
    }

    def "binary uses all source sets from a functional source set"() {
        given:
        def binary = testBinary(component)
        def functionalSourceSet = new DefaultFunctionalSourceSet("func", new DirectInstantiator())
        def sourceSet1 = Stub(LanguageSourceSet) {
            getName() >> "ss1"
        }
        def sourceSet2 = Stub(LanguageSourceSet) {
            getName() >> "ss2"
        }

        when:
        functionalSourceSet.add(sourceSet1)
        functionalSourceSet.add(sourceSet2)

        and:
        binary.source functionalSourceSet

        then:
        binary.source.contains(sourceSet1)
        binary.source.contains(sourceSet2)
    }

    def "uses resolver to resolve lib to dependency"() {
        def binary = testBinary(component, flavor1)
        def lib = new Object()
        def dependency = Stub(NativeDependencySet)

        when:
        binary.lib(lib)

        and:
        1 * resolver.resolve({NativeBinaryResolveResult result ->
            result.allResolutions*.input == [lib]
        }) >> { NativeBinaryResolveResult result ->
            result.allResolutions[0].nativeDependencySet = dependency
        }

        then:
        binary.libs// == [dependency]
    }

    def "binary libs include source set dependencies"() {
        def binary = testBinary(component)
        def lib = new Object()
        def dependency = Stub(NativeDependencySet)

        when:
        def sourceSet = Stub(DependentSourceSet) {
            getLibs() >> [lib]
        }
        binary.source sourceSet

        1 * resolver.resolve({NativeBinaryResolveResult result ->
            result.allResolutions*.input == [lib]
        }) >> { NativeBinaryResolveResult result ->
            result.allResolutions[0].nativeDependencySet = dependency
        }

        then:
        binary.getLibs(sourceSet) == [dependency]
    }

    def "order of libraries is maintained"() {
        def binary = testBinary(component)
        def libraryBinary = Mock(NativeLibraryBinary)
        def dependency1 = Stub(NativeDependencySet)
        def dependency2 = Stub(NativeDependencySet)
        def dependency3 = Stub(NativeDependencySet)

        when:
        binary.lib(dependency1)
        binary.lib(libraryBinary)
        binary.lib(dependency3)

        and:
        1 * resolver.resolve({NativeBinaryResolveResult result ->
            result.allResolutions*.input == [dependency1, libraryBinary, dependency3]
        }) >> { NativeBinaryResolveResult result ->
            result.allResolutions[0].nativeDependencySet = dependency1
            result.allResolutions[1].nativeDependencySet = dependency2
            result.allResolutions[2].nativeDependencySet = dependency3
        }

        then:
        binary.libs as List == [dependency1, dependency2, dependency3]
    }

    def "library added to binary is ordered before library for source set"() {
        def binary = testBinary(component)
        def lib1 = new Object()
        def dep1 = Stub(NativeDependencySet)
        def lib2 = new Object()
        def dep2 = Stub(NativeDependencySet)
        def sourceLib = new Object()
        def sourceDep = Stub(NativeDependencySet)

        when:
        binary.lib(lib1)
        def sourceSet = Stub(DependentSourceSet) {
            getLibs() >> [sourceLib]
        }
        binary.source sourceSet
        binary.lib(lib2)

        and:
        1 * resolver.resolve({NativeBinaryResolveResult result ->
            result.allResolutions*.input == [lib1, lib2, sourceLib]
        }) >> { NativeBinaryResolveResult result ->
            result.allResolutions[0].nativeDependencySet = dep1
            result.allResolutions[1].nativeDependencySet = dep2
            result.allResolutions[2].nativeDependencySet = sourceDep
        }

        then:
        binary.libs as List == [dep1, dep2, sourceDep]
    }

    def testBinary(NativeComponentSpec owner, Flavor flavor = new DefaultFlavor(DefaultFlavor.DEFAULT)) {
        return new TestNativeBinarySpec(owner, flavor, toolChain1, Stub(PlatformToolProvider), platform1, buildType1, new DefaultBinaryNamingScheme("baseName", "", []), resolver)
    }

    class TestNativeComponentSpec extends AbstractNativeComponentSpec {
        TestNativeComponentSpec(ComponentSpecIdentifier id, FunctionalSourceSet sourceSet) {
            super(id, sourceSet)
        }

        String getDisplayName() {
            return "test component"
        }
    }

    class TestNativeBinarySpec extends AbstractNativeBinarySpec {
        def owner

        TestNativeBinarySpec(NativeComponentSpec owner, Flavor flavor, NativeToolChainInternal toolChain, PlatformToolProvider toolProvider, NativePlatform targetPlatform, BuildType buildType,
                   BinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
            super(owner, flavor, toolChain, toolProvider, targetPlatform, buildType, namingScheme, resolver)
            this.owner = owner
        }

        String getOutputFileName() {
            return null
        }

        File getPrimaryOutput() {
            File binaryOutputDir = getBinaryOutputDir();
            return new File(binaryOutputDir, getOutputFileName());
        }
    }
}
