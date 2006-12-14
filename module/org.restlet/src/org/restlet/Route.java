/*
 * Copyright 2005-2006 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package org.restlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.restlet.data.Form;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.util.Template;

/**
 * Filter scoring the affinity of calls with the attached Restlet. The score is
 * used by an associated Router in order to determine the most appropriate
 * Restlet for a given call.
 * 
 * Scorer based of a reference template. It also supports the extraction of some
 * attributes from a call. Multiple extractions can be defined, based on the
 * query string of the resource reference, on the request form (posted from a
 * browser), on the base URI matches or on the request or response data model.
 * 
 * @author Jerome Louvel (contact@noelios.com)
 */
public class Route extends Filter {
    /** Internal class holding extraction information. */
    private static final class ExtractInfo {
        /**
         * Holds the attribute name.
         */
        protected String attribute;

        /**
         * Holds information to extract the attribute value.
         */
        protected String value;

        /**
         * Holds indicator on how to handle repeating values.
         */
        protected boolean first;

        /**
         * Holds information to extract the attribute value.
         */
        protected int index;

        /**
         * Constructor.
         * 
         * @param attribute
         * @param index
         */
        public ExtractInfo(String attribute, int index) {
            this.attribute = attribute;
            this.value = null;
            this.first = true;
            this.index = index;
        }

        /**
         * Constructor.
         * 
         * @param attribute
         * @param value
         */
        public ExtractInfo(String attribute, String value) {
            this.attribute = attribute;
            this.value = value;
            this.first = true;
            this.index = -1;
        }

        /**
         * Constructor.
         * 
         * @param attribute
         * @param value
         * @param first
         */
        public ExtractInfo(String attribute, String value, boolean first) {
            this.attribute = attribute;
            this.value = value;
            this.first = first;
            this.index = -1;
        }
    }

    /** The parent router. */
    private Router router;

    /** List of cookies to extract. */
    private List<ExtractInfo> cookieExtracts;

    /** List of query parameters to extract. */
    private List<ExtractInfo> queryExtracts;

    /** List of request entity parameters to extract. */
    private List<ExtractInfo> entityExtracts;

    /** List of request form parameters (query or entity parameters) to extract. */
    private List<ExtractInfo> formExtracts;

    /** List of request's model attributes to extract. */
    private List<ExtractInfo> modelExtracts;

    /** The reference template to match. */
    private Template template;

    /**
     * Constructor behaving as a simple extractor filter.
     * 
     * @param next
     *            The next Restlet.
     */
    public Route(Restlet next) {
        this(null, null, next);
    }

    /**
     * Constructor.
     * 
     * @param router
     *            The parent router.
     * @param uriTemplate
     *            The URI template.
     * @param next
     *            The next Restlet.
     */
    public Route(Router router, String uriTemplate, Restlet next) {
        super(router == null ? null : router.getContext(), next);
        this.router = router;
        this.cookieExtracts = null;
        this.queryExtracts = null;
        this.entityExtracts = null;
        this.formExtracts = null;
        this.modelExtracts = null;
        this.template = new Template(getLogger(), uriTemplate);
    }

    /**
     * Allows filtering before its handling by the target Restlet. Does nothing
     * by default.
     * 
     * @param request
     *            The request to filter.
     * @param response
     *            The response to filter.
     */
    protected void beforeHandle(Request request, Response response) {
        if (getTemplate() != null) {
            String remainingPart = request.getResourceRef().getRemainingPart();
            int matchedLength = getTemplate().parse(remainingPart, request);

            if (getLogger().isLoggable(Level.FINER)) {
                getLogger().finer(
                        "Attempting to match this pattern: "
                                + getTemplate().getPattern() + " >> "
                                + matchedLength);
            }

            if (matchedLength > 0) {
                // Updates the context
                String matchedPart = remainingPart.substring(0, matchedLength);
                Reference baseRef = request.getResourceRef().getBaseRef();

                if (baseRef == null) {
                    baseRef = new Reference(matchedPart);
                } else {
                    baseRef = new Reference(baseRef.toString(false, false)
                            + matchedPart);
                }

                request.getResourceRef().setBaseRef(baseRef);

                if (getLogger().isLoggable(Level.FINE)) {
                    getLogger().fine(
                            "New base URI: "
                                    + request.getResourceRef().getBaseRef());
                    getLogger().fine(
                            "New remaining part: "
                                    + request.getResourceRef()
                                            .getRemainingPart());
                }

                if (getLogger().isLoggable(Level.FINE)) {
                    getLogger().fine(
                            "Delegating the call to the target Restlet");
                }
            } else {
                response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            }
        }

        // Now extract attributes
        extractAttributes(request, response);
    }

    /**
     * Extracts the attributes value from the request.
     * 
     * @param request
     *            The request to process.
     * @param response
     *            The response to process.
     * @throws IOException
     */
    private void extractAttributes(Request request, Response response) {
        // Extract the query parameters
        if (this.queryExtracts != null) {
            Form form = request.getResourceRef().getQueryAsForm();

            if (form != null) {
                for (ExtractInfo qe : getQueryExtracts()) {
                    if (qe.first) {
                        request.getAttributes().put(qe.attribute,
                                form.subList(qe.value));
                    } else {
                        request.getAttributes().put(qe.attribute,
                                form.getFirst(qe.value));
                    }
                }
            }
        }

        // Extract the request entity parameters
        if (this.entityExtracts != null) {
            Form form = request.getEntityAsForm();

            if (form != null) {
                for (ExtractInfo ie : getEntityExtracts()) {
                    if (ie.first) {
                        request.getAttributes().put(ie.attribute,
                                form.subList(ie.value));
                    } else {
                        request.getAttributes().put(ie.attribute,
                                form.getFirst(ie.value));
                    }
                }
            }
        }

        // Extract the model patterns
        if (this.modelExtracts != null) {
            // CallModel model = new CallModel(request, response, null);
            // for (ExtractInfo me : getModelExtracts()) {
            // request.getAttributes().put(me.attribute, model.get(me.value));
            // }
        }
    }

    /**
     * Extracts an attribute from the request cookies.
     * 
     * @param attributeName
     *            The name of the request attribute to set.
     * @param cookieName
     *            The name of the cookies to extract.
     * @param first
     *            Indicates if only the first cookie should be set. Otherwise as
     *            a List instance might be set in the attribute value.
     * @return The current Filter.
     */
    public Route extractCookie(String attributeName, String cookieName,
            boolean first) {
        getCookieExtracts().add(
                new ExtractInfo(attributeName, cookieName, first));
        return this;
    }

    /**
     * Extracts an attribute from the request entity form.
     * 
     * @param attributeName
     *            The name of the request attribute to set.
     * @param parameterName
     *            The name of the entity form parameter to extract.
     * @param first
     *            Indicates if only the first cookie should be set. Otherwise as
     *            a List instance might be set in the attribute value.
     * @return The current Filter.
     */
    public Route extractEntity(String attributeName, String parameterName,
            boolean first) {
        getEntityExtracts().add(
                new ExtractInfo(attributeName, parameterName, first));
        return this;
    }

    /**
     * Extracts an attribute from the request query form or, if not found, from
     * the entity form.
     * 
     * @param attributeName
     *            The name of the request attribute to set.
     * @param parameterName
     *            The name of the entity form parameter to extract.
     * @param first
     *            Indicates if only the first cookie should be set. Otherwise as
     *            a List instance might be set in the attribute value.
     * @return The current Filter.
     */
    public Route extractForm(String attributeName, String parameterName,
            boolean first) {
        getFormExtracts().add(
                new ExtractInfo(attributeName, parameterName, first));
        return this;
    }

    /**
     * Extracts an attribute from the query string of the resource reference.
     * 
     * @param attributeName
     *            The name of the request attribute to set.
     * @param parameterName
     *            The name of the query string parameter to extract.
     * @param first
     *            Indicates if only the first cookie should be set. Otherwise as
     *            a List instance might be set in the attribute value.
     * @return The current Filter.
     */
    public Route extractQuery(String attributeName, String parameterName,
            boolean first) {
        getQueryExtracts().add(
                new ExtractInfo(attributeName, parameterName, first));
        return this;
    }

    /**
     * Extracts an attribute from the request's model.
     * 
     * @param attributeName
     *            The name of the request attribute to set.
     * @param variableName
     *            The variable name to resolve (see
     *            {@link org.restlet.util.Template} for details.
     * @return The current Filter.
     * @see org.restlet.util.Template
     */
    public Route extractRequest(String attributeName, String variableName) {
        getRequestExtracts().add(new ExtractInfo(attributeName, variableName));
        return this;
    }

    /**
     * Returns the list of query extracts.
     * 
     * @return The list of query extracts.
     */
    private List<ExtractInfo> getCookieExtracts() {
        if (this.cookieExtracts == null)
            this.cookieExtracts = new ArrayList<ExtractInfo>();
        return this.cookieExtracts;
    }

    /**
     * Returns the list of query extracts.
     * 
     * @return The list of query extracts.
     */
    private List<ExtractInfo> getEntityExtracts() {
        if (this.entityExtracts == null)
            this.entityExtracts = new ArrayList<ExtractInfo>();
        return this.entityExtracts;
    }

    /**
     * Returns the list of form extracts.
     * 
     * @return The list of form extracts.
     */
    private List<ExtractInfo> getFormExtracts() {
        if (this.formExtracts == null)
            this.formExtracts = new ArrayList<ExtractInfo>();
        return this.formExtracts;
    }

    /**
     * Returns the list of query extracts.
     * 
     * @return The list of query extracts.
     */
    private List<ExtractInfo> getQueryExtracts() {
        if (this.queryExtracts == null)
            this.queryExtracts = new ArrayList<ExtractInfo>();
        return this.queryExtracts;
    }

    /**
     * Returns the list of query extracts.
     * 
     * @return The list of query extracts.
     */
    private List<ExtractInfo> getRequestExtracts() {
        if (this.modelExtracts == null)
            this.modelExtracts = new ArrayList<ExtractInfo>();
        return this.modelExtracts;
    }

    /**
     * Returns the parent router.
     * 
     * @return The parent router.
     */
    public Router getRouter() {
        return this.router;
    }

    /**
     * Returns the reference template to match.
     * 
     * @return The reference template to match.
     */
    public Template getTemplate() {
        return this.template;
    }

    /**
     * Returns the score for a given call (between 0 and 1.0).
     * 
     * @param request
     *            The request to score.
     * @param response
     *            The response to score.
     * @return The score for a given call (between 0 and 1.0).
     */
    public float score(Request request, Response response) {
        float result = 0F;

        if ((getRouter() != null) && (request.getResourceRef() != null)
                && (getTemplate() != null)) {
            String remainingPart = request.getResourceRef().getRemainingPart();
            int matchedLength = getTemplate().match(remainingPart);

            if (matchedLength > 0) {
                float totalLength = remainingPart.length();

                if (totalLength > 0.0F) {
                    result = getRouter().getRequiredScore()
                            + (1.0F - getRouter().getRequiredScore())
                            * (((float) matchedLength) / totalLength);
                } else {
                    result = 1.0F;
                }
            }

            if (getLogger().isLoggable(Level.FINER)) {
                getLogger().finer(
                        "Call score for the \"" + getTemplate().getPattern()
                                + "\" URI pattern: " + result);
            }
        }

        return result;
    }

    /**
     * Sets the reference template to match.
     * 
     * @param template
     *            The reference template to match.
     */
    public void setTemplate(Template template) {
        this.template = template;
    }

}
