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
* NexusRegisterArtifactDeliveryStepPlugin.java
* 
* User: Alex Honor <a href="mailto:alex@simplifyops.com">alex@simplifyops.com</a>
* Created: 05/29/14 4:39 PM
* 
*/
package com.simplifyops.rundeck.plugin.nexus

import com.dtolabs.rundeck.core.common.NodeEntryImpl
import com.dtolabs.rundeck.core.common.NodeSetImpl
import com.dtolabs.rundeck.core.dispatcher.DataContextUtils
import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope
import com.dtolabs.rundeck.core.resources.format.ResourceXMLFormatGenerator
import com.dtolabs.rundeck.plugins.ServiceNameConstants
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.plugins.step.StepPlugin
import groovyx.net.http.URIBuilder

/**
 * Example Req  http://192.168.50.20:8081/nexus/service/local/artifact/maven/redirect?r=releases&g=sardine&a=sardine&v=5.0&p=jar
 */

@Plugin(name = "nexus-register-delivery-step", service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = "Nexus: Register Artifact Delivery", description = "Register the delivery of artifact as metadata for the nodes.")
public class NexusRegisterArtifactDeliveryStepPlugin implements StepPlugin {

    public static enum Reason implements FailureReason {
        CopyFileFailed, GetFileFailed, FileNotFound, UnexepectedFailure

    }



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
    @PluginProperty(title = "Model Source Directory", description = "Directory where model source data is stored.", required = true, scope = PropertyScope.Project)
    private String modelDirPath;
    @PluginProperty(title = "Nexus", description = "Nexus server URL. eg, http://repository.example.com:8081", required = true, scope=PropertyScope.Project)
    private String nexus;


    static final String REDIRECT_URL = "/nexus/service/local/artifact/maven/redirect"



    @Override
    public void executeStep(final PluginStepContext context, final Map<String, Object> configuration)
    throws StepException {


        def query = [g: group, a: artifact, v: version, r: repo, p: packaging]
        if (classifier) {
            query.c = classifier
        }

        String customDestinationPath = destinationPath;


        def NodeSetImpl nodes = new NodeSetImpl()

        /**
         *   <attribute name="artifact:${group}:${artifact}.version" value="${version}"/>
         *   <attribute name="artifact:${group}:${artifact}.destinationPath" value="${destinationPath}"/>
         *   <attribute name="artifact:${group}:${artifact}.repoUrl" value="${repoUrl}"/>
         *
         * e.g.,
         *
         *    artifact:com.sardine:sardine.version: 5.0
         *    artifact:com.sardine:sardine.destinationPath: /tmp/fish
         *    artifact:com.sardine:sardine.repoUrl: http://192.168.50.20:8081/nexus/...
         */
        context.getNodes().each { node ->
            def nodedata = DataContextUtils.nodeData(node)
            def merged = DataContextUtils.addContext("node",nodedata,context.dataContext)
            def expandedPath = DataContextUtils.replaceDataReferences(customDestinationPath,merged)
            println("Registering delivery for artifact matching query: ${query} to destination: ${node.nodename}:${expandedPath}")

            def NodeEntryImpl newNode = new NodeEntryImpl(node.getNodename())

            def prefix = "artifact:${group}:${artifact}"
            newNode.setAttribute("${prefix}:job.execid", context.dataContext.job.execid)
            newNode.setAttribute("${prefix}:job.delivery-date", new Date().toString())
            newNode.setAttribute("${prefix}:version", version)
            newNode.setAttribute("${prefix}:groupId", group)
            newNode.setAttribute("${prefix}:artifactId", artifact)
            newNode.setAttribute("${prefix}:destination_path", expandedPath)
            newNode.setAttribute("${prefix}:repoUrl", new URIBuilder(nexus)
                    .setPath(REDIRECT_URL)
                    .setQuery(query)
                    .toString())

            nodes.putNode(newNode)

        }

        /**
         * Serialize the data
         */

        def modelDir = new File(modelDirPath)
        println("Writing model data to directory: ${modelDir.absolutePath}")

        if (!modelDir.exists()) {
            if (!modelDir.mkdirs()) throw new RuntimeException("Failed creating model directory: ${modelDir.absolutePath}")
        }
        def file = new File(modelDir, "artifacts.xml")
        def fostream = new FileOutputStream(file)
        def formatter = new ResourceXMLFormatGenerator()
        formatter.generateDocument(nodes, fostream)
        fostream.close()

        println("Registered artifact on ${nodes.nodeNames.size()} nodes: ${nodes.nodeNames}")
        println("Model file: ${file.absolutePath}")

    }


}
