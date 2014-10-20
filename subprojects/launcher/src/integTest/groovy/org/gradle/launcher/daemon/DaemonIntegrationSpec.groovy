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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.DaemonGradleExecuter
import org.gradle.launcher.daemon.testing.DaemonLogsAnalyzer
import org.gradle.launcher.daemon.testing.DaemonsFixture

abstract class DaemonIntegrationSpec extends AbstractIntegrationSpec {
    String output

    @Override
    DaemonGradleExecuter getExecuter() {
        super.executer as DaemonGradleExecuter
    }

    def setup() {
        executer = new DaemonGradleExecuter(distribution, temporaryFolder)
        executer.requireIsolatedDaemons()
    }

    @Override
    protected void cleanupWhileTestFilesExist() {
        // Need to kill daemons before test files are cleaned up, as the log files and registry are used to locate the daemons and these live under
        // the test file directory.
        daemons.killAll()
    }

    void stopDaemonsNow() {
        def result = executer.withArguments("--stop", "--info").run()
        output = result.output
    }

    void buildSucceeds(String script = '') {
        file('build.gradle') << script
        def result = executer.withArguments("--info").withNoDefaultJvmArgs().run()
        output = result.output
    }

    DaemonsFixture getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }
}
