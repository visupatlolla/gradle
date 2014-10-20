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

import org.gradle.model.RuleSource
import spock.lang.Specification
import spock.lang.Unroll

class ModelRuleSourceDetectorTest extends Specification {

    private ModelRuleSourceDetector detector = new ModelRuleSourceDetector()

    static class HasOneSource {
        @RuleSource
        static class Source {}

        static class NotSource {}
    }

    static class HasTwoSources {
        @RuleSource
        static class SourceOne {}

        @RuleSource
        static class SourceTwo {}

        static class NotSource {}
    }

    @RuleSource
    static class IsASource {
    }

    @Unroll
    def "find model rule sources - #clazz"() {
        expect:
        detector.getDeclaredSources(clazz) == expected.toSet()

        where:
        clazz         | expected
        String        | []
        HasOneSource  | [HasOneSource.Source]
        HasTwoSources | [HasTwoSources.SourceOne, HasTwoSources.SourceTwo]
        IsASource     | [IsASource]
    }

    @Unroll
    def "has model sources - #clazz"() {
        expect:
        detector.hasModelSources(clazz) == expected

        where:
        clazz         | expected
        String        | false
        HasOneSource  | true
        IsASource     | true
    }
}
