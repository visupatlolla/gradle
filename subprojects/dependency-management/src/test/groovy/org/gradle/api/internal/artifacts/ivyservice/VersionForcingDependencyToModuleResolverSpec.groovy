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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import spock.lang.Specification

class VersionForcingDependencyToModuleResolverSpec extends Specification {
    def requested = new DefaultModuleVersionSelector("group", "module", "version")
    def dependency = Mock(DependencyMetaData) {
        getRequested() >> requested
    }
    def result = Mock(BuildableComponentIdResolveResult)
    def target = Mock(DependencyToComponentIdResolver)
    def rule = Mock(Action)
    def resolver = new VersionForcingDependencyToModuleResolver(target, rule)

    def "passes through dependency when it does not match any rule"() {
        given:
        rule.execute(_) >> { DependencyResolveDetails details ->
        }

        when:
        resolver.resolve(dependency, result)

        then:
        1 * target.resolve(dependency, result)
    }

    def "replaces dependency by rule"() {
        def substitutedDependency = Stub(DependencyMetaData)

        given:
        rule.execute(_) >> { DependencyResolveDetails details ->
            details.useVersion("new")
        }

        when:
        resolver.resolve(dependency, result)

        then:
        1 * dependency.withRequestedVersion(new DefaultModuleVersionSelector("group", "module", "new")) >> substitutedDependency
        1 * target.resolve(substitutedDependency, result)
    }

    def "explosive rule yields failure result that provides context"() {
        given:
        def failure = new RuntimeException("broken")
        rule.execute(_) >> { DependencyResolveDetails details ->
            throw failure
        }

        when:
        resolver.resolve(dependency, result)

        then:
        1 * result.failed(_) >> { ModuleVersionResolveException e ->
            assert e.cause == failure
        }
    }
}
