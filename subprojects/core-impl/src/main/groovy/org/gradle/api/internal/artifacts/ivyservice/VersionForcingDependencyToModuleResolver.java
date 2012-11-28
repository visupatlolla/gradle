/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ForcedModuleDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VersionForcingDependencyToModuleResolver implements DependencyToModuleVersionIdResolver {
    private final DependencyToModuleVersionIdResolver resolver;
    private final Set<Action<ForcedModuleDetails>> forcedModuleRules = new HashSet<Action<ForcedModuleDetails>>();

    public VersionForcingDependencyToModuleResolver(DependencyToModuleVersionIdResolver resolver, Set<ModuleVersionSelector> forcedModules, Set<Action<ForcedModuleDetails>> forcedModuleRules) {
        this.resolver = resolver;
        this.forcedModuleRules.add(new ForcedVersionsRule(forcedModules));
        this.forcedModuleRules.addAll(forcedModuleRules);
    }

    public static class ForcedVersionsRule implements Action<ForcedModuleDetails> {

        private final Map<String, String> forcedModules = new HashMap<String, String>();

        public ForcedVersionsRule(Iterable<? extends ModuleVersionSelector> forcedModules) {
            for (ModuleVersionSelector module : forcedModules) {
                this.forcedModules.put(key(module), module.getVersion());
            }
        }

        public void execute(ForcedModuleDetails forcedModuleDetails) {
            String key = key(forcedModuleDetails.getModule());
            if (forcedModules.containsKey(key)) {
                forcedModuleDetails.setVersion(forcedModules.get(key));
            }
        }

        private String key(ModuleVersionSelector module) {
            return module.getGroup() + ":" + module.getName();
        }
    }

    public static class DefaultForcedModuleDetails implements ForcedModuleDetails {

        private final ModuleVersionSelector module;
        private String version;

        public DefaultForcedModuleDetails(ModuleVersionSelector module) {
            this.module = module;
        }

        public ModuleVersionSelector getModule() {
            return module;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getVersion() {
            return version;
        }
    }

    public ModuleVersionIdResolveResult resolve(DependencyDescriptor dependencyDescriptor) {
        for (Action<ForcedModuleDetails> rule : forcedModuleRules) {
            ModuleVersionSelector module = new DefaultModuleVersionSelector(dependencyDescriptor.getDependencyRevisionId().getOrganisation(), dependencyDescriptor.getDependencyRevisionId().getName(), dependencyDescriptor.getDependencyRevisionId().getRevision());
            ForcedModuleDetails details = new DefaultForcedModuleDetails(module);
            rule.execute(details);
            if (details.getVersion() != null) {
                ModuleId moduleId = new ModuleId(details.getModule().getGroup(), details.getModule().getName());
                ModuleRevisionId revisionId = new ModuleRevisionId(moduleId, details.getVersion());
                DependencyDescriptor descriptor = dependencyDescriptor.clone(revisionId);
                ModuleVersionIdResolveResult result = resolver.resolve(descriptor);
                return new ForcedModuleVersionIdResolveResult(result);
            }
        }
        return resolver.resolve(dependencyDescriptor);
    }
}
