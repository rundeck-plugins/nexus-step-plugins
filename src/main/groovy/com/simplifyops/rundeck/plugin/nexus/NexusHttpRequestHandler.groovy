package com.simplifyops.rundeck.plugin.nexus

import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovyx.net.http.URIBuilder
import groovyx.net.http.ContentType


class NexusHttpRequestHandler {

    static final String REDIRECT_URL = "/service/local/artifact/maven/redirect"

    public static enum Reason implements FailureReason {
        FileNotFound, UnexepectedFailure
    }

    static handleRequest(host, user, password, query, successHandler) {

        def uri = new URIBuilder(host + REDIRECT_URL).setQuery(query);

        println "Nexus request URL: ${uri}"

        def http = new HTTPBuilder(uri)

        if (user && password) {
            http.auth.basic user, password
        }

        http.request( Method.GET, ContentType.ANY ) {

            response.success = successHandler

            response.failure = { resp ->
                throw new StepException("Status code: ${resp.status}. query: ${query}. server: ${host}", Reason.UnexepectedFailure)
            }

            response.'404' = { resp ->
                throw new StepException("Artifact not found in matching query: ${query}, server: ${host}", Reason.FileNotFound)
            }
        }
    }

    static buildQuery(group , artifact, version, repo, packaging, classifier) {

        def query = [g: group, a: artifact, v: version, r: repo, p: packaging]

        if (classifier) {
            query.c = classifier
        }

        return query
    }

}
