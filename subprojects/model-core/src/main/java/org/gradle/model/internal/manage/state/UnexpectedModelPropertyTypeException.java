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

package org.gradle.model.internal.manage.state;

public class UnexpectedModelPropertyTypeException extends RuntimeException{

    public UnexpectedModelPropertyTypeException(String propertyName, Class<?> owner, Class<?> expected, Class<?> actual) {
        super(String.format("Expected property '%s' for type '%s' to be of type '%s' but it actually is of type '%s'", propertyName, owner.getName(), expected.getName(), actual.getName()));
    }
}
