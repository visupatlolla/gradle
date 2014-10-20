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
package org.gradle.nativeplatform.platform.internal

import org.gradle.internal.typeconversion.NotationParser
import spock.lang.Specification

class DefaultNativePlatformTest extends Specification {
    def archParser = Mock(NotationParser)
    def osParser = Mock(NotationParser)
    def os = Mock(OperatingSystemInternal)
    def arch = Mock(ArchitectureInternal)
    def platform = new DefaultNativePlatform("platform", arch, os, archParser, osParser)

    def "has useful string representation"() {
        expect:
        platform.displayName == "platform 'platform'"
        platform.toString() == "platform 'platform'"
    }

    def "can configure architecture"() {
        def arch = Mock(ArchitectureInternal)
        when:
        platform.architecture "ppc64"

        then:
        1 * archParser.parseNotation("ppc64") >> arch

        and:
        platform.architecture == arch
    }

    def "can configure operating system"() {
        def os = Mock(OperatingSystemInternal)
        when:
        platform.operatingSystem "the-os"

        then:
        1 * osParser.parseNotation("the-os") >> os

        and:
        platform.operatingSystem == os
    }
}
