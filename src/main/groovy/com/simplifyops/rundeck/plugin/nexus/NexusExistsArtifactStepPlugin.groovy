/*
 * Copyright 2014 Simplify Ops, Inc. (http://simplifyops.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*
* NexusExistsArtifactStepPlugin.java
* 
* User: Alex Honor <a href="mailto:alex@simplifyops.com">alex@simplifyops.com</a>
* Created: 05/29/14 4:39 PM
* 
*/
package com.simplifyops.rundeck.plugin.nexus

import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope
import com.dtolabs.rundeck.plugins.ServiceNameConstants
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.plugins.step.StepPlugin
import groovyx.net.http.HTTPBuilder

import static groovyx.net.http.Method.HEAD

/**
 * Example Req  http://192.168.50.20:8081/nexus/service/local/artifact/maven/redirect?r=releases&g=sardine&a=sardine&v=5.0&p=jar
 */

@Plugin(name = "nexus-exists-artifact-step", service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = "Nexus: Exists Artifact", description = "Check if artifact exists in nexus.")
public class NexusExistsArtifactStepPlugin implements StepPlugin {

    public static enum Reason implements FailureReason {
        UnexepectedFailure, FileNotFound
    }
    static final String REDIRECT_URL = "/nexus/service/local/artifact/maven/redirect"



    @PluginProperty(title = "Group", description = "Artifact group ID.", required = true)
    private String group;
    @PluginProperty(title = "Artifact", description = "Artifact ID.", required = true)
    private String artifact;
    @PluginProperty(title = "Version", description = "Artifact version.", required = true)
    private String version;
    @PluginProperty(title = "Packaging", description = "Artifact packaging.", required = true)
    private String packaging;
    @PluginProperty(title = "Classifer", description = "Artifact classifier.", required = false)
    private String classifier;
    @PluginProperty(title = "Repo", description = "Repository name.", required = true)
    private String repo;
    @PluginProperty(title = "Nexus", description = "Nexus server URL. eg, http://repository.example.com:8081", required = true, scope=PropertyScope.Project)
    private String nexus;
    @PluginProperty(title = "Nexus User", description = "Nexus login name", required = true, scope=PropertyScope.Project)
    private String nexusUser;
    @PluginProperty(title = "Nexus Password", description = "Nexus login password", required = true, scope=PropertyScope.Project)
    private String nexusPassword;





    @Override
    public void executeStep(final PluginStepContext context, final Map<String, Object> configuration)
    throws StepException {

        def query = [g: group, a: artifact, v: version, r: repo, p: packaging]
        if (classifier) {
            query.c = classifier
        }
        def http = new HTTPBuilder(nexus)
        if (nexusUser && nexusPassword) {
            http.auth.basic nexusUser, nexusPassword
        }

        http.request(HEAD) { req ->
            uri.path = REDIRECT_URL
            uri.query = query

            response.success = { resp  ->
                println("Artifact exists.")
                println("------------Headers------------")
                resp.allHeaders.each {context.getLogger().log(2, "${it}")}
            }
            response.'404' = {
                throw new StepException("Artifact not found matching query: ${query}, server: ${nexus}",
                        Reason.FileNotFound)
            }
            response.failure = { resp ->
                throw new StepException("Status code: ${resp.status}. query: ${query}. server: ${nexus}",
                        Reason.UnexepectedFailure)
            }
        }
    }
}
