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
* NexusDeliverArtifactStepPlugin.java
* 
* User: Alex Honor <a href="mailto:alex@simplifyops.com">alex@simplifyops.com</a>
* Created: 05/29/14 4:39 PM
* 
*/
package com.simplifyops.rundeck.plugin.nexus

import com.dtolabs.rundeck.core.common.INodeEntry
import com.dtolabs.rundeck.core.execution.service.FileCopierException
import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException
import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.plugins.ServiceNameConstants
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.plugins.step.StepPlugin
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.net.http.URIBuilder

import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.ANY

/**
 * Example Req  http://192.168.50.20:8081/nexus/service/local/artifact/maven/redirect?r=releases&g=sardine&a=sardine&v=5.0&p=jar
 */

@Plugin(name = "nexus-deliver-artifact-step", service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = "Nexus: Deliver Artifact", description = "Copy an artifact to a destination on the remote nodes.")
public class NexusDeliverArtifactStepPlugin implements StepPlugin {

    public static enum Reason implements FailureReason {
        CopyFileFailed, GetFileFailed, FileNotFound, UnexepectedFailure

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
    @PluginProperty(title = "Destination Path", description = "Path on the remote node for the file destination. If the path ends with a /, the same filename as the source will be used.", required = true)
    private String destinationPath;
    @PluginProperty(title = "Print transfer information", description = "Log information about the file copy", defaultValue = "true")
    private boolean echo;
    @PluginProperty(title = "Nexus", description = "Nexus server URL. eg, http://repository.example.com:8081", required = true)
    private String nexus;





    @Override
    public void executeStep(final PluginStepContext context, final Map<String, Object> configuration)
        throws StepException {

        def tempFile = File.createTempFile("nexus-get-artifact", ".tmp");

        def query = [g: group, a: artifact, v: version, r: repo, p: packaging]
        if (classifier) {
            query.c = classifier
        }
        def http = new HTTPBuilder(nexus)
        http.auth.basic 'admin', 'admin123'

        http.handler.'404' = {
            throw new StepException("Artifact not found in matching query: ${query}, server: ${nexus}",
                    Reason.FileNotFound)
        }
        http.handler.failure = { resp ->
            throw new StepException("Status code: ${resp.status}. query: ${query}. server: ${nexus}",
                    Reason.UnexepectedFailure)
        }

        if (echo) {
            def uri = new URIBuilder(nexus)
                    .setPath(REDIRECT_URL)
                    .setQuery(query)
                    .toString()
            context.getLogger().log(2, "Requesting artifact from url: ${uri}")
        }
        http.get(path: REDIRECT_URL,
                query: query) { resp, responseStream ->

            if (echo) {
                context.getLogger().log(2, "Nexus request status: ${resp.status}, " +
                        "Content-type: ${resp.contentType}, Content-length: ${resp.entity.contentLength}")
            }
            def outputStream = new FileOutputStream(tempFile)
            outputStream << responseStream
            outputStream.close()
        }


        String customDestinationPath = destinationPath;
        if (destinationPath.endsWith("/")) {
            customDestinationPath = customDestinationPath + tempFile.getName();
        }
        context.getNodes().each { node ->

            try {
                if (echo) {
                    context.getLogger().log(2, "Begin copy " + tempFile.length() + " bytes to "
                            + node.getNodename() + ":" + customDestinationPath);
                }
                String path = context.getFramework().getExecutionService().fileCopyFile(
                        context.getExecutionContext(),
                        tempFile,
                        node,
                        customDestinationPath);
                if (echo) {
                    context.getLogger().log(2, "Copy completed: " + path);
                }
            } catch (FileCopierException e) {
                context.getLogger().log(0, "failed: " + e.getMessage());
                throw new StepException("Remote copy failed for node: ${node.getNodename()}", e, Reason.CopyFileFailed);
            }
        }

        tempFile.delete()
    }


}
