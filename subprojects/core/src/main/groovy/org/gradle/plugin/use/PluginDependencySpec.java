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

package org.gradle.plugin.use;

import org.gradle.api.Incubating;
import org.gradle.api.Nullable;

/**
 * A mutable specification of a dependency on a plugin.
 * <p>
 * Can be used to specify the version of the plugin to use.
 * </p>
 * <p>
 * See {@link PluginDependenciesSpec} for more information about declaring plugin dependencies.
 * </p>
 */
@Incubating
public interface PluginDependencySpec {

    /**
     * Specify the version of the plugin to depend on.
     * <p>
     * <pre>
     * plugins {
     *     id "org.company.myplugin" version "1.0"
     * }
     * </pre>
     * <p>
     * By default, dependencies have no (i.e. {@code null}) version.
     * <p>
     * Core plugins must not include a version number specification.
     * Community plugins must include a version number specification.
     *
     * @param version the version string ({@code null} for no specified version, which is the default)
     */
    void version(@Nullable String version);

}
