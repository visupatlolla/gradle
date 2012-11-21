/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.LinkedHashSet;
import java.util.Set;

public class ProjectEvaluationConfigurer implements Action<ProjectInternal> {

    private Set<ProjectInternal> projects = new LinkedHashSet<ProjectInternal>();

    public void execute(ProjectInternal projectInternal) {
        if (projectInternal.getPath().equals(":")) {
            projectInternal.evaluate();
        } else {
            projects.add(projectInternal);
        }
    }

    public void evaluateNow(String projectPath) {
        for (ProjectInternal project : projects) {
            if (project.getPath().equals(projectPath)) {
                project.evaluate();
            }
        }
    }
}
