/**
 * Copyright 2005-2009 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.engine.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Encoding;
import org.restlet.data.Language;
import org.restlet.data.Method;
import org.restlet.data.Parameter;
import org.restlet.data.Status;
import org.restlet.data.Tag;
import org.restlet.engine.ConnectorHelper;
import org.restlet.engine.http.adapter.ClientAdapter;
import org.restlet.engine.http.header.ContentType;
import org.restlet.engine.http.header.DispositionReader;
import org.restlet.engine.http.header.HeaderConstants;
import org.restlet.engine.http.header.HeaderReader;
import org.restlet.engine.http.header.HeaderUtils;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.util.Series;

/**
 * Low-level HTTP client call.
 * 
 * @author Jerome Louvel
 */
public abstract class ClientCall extends Call {
    /**
     * Copies entity headers into a response and ensures that a non null
     * representation is returned when at least one entity header is present.
     * 
     * @param responseHeaders
     *            The headers to copy.
     * @param representation
     *            The Representation to update.
     * @return a representation with the entity headers of the response or null
     *         if no representation has been provided and the response has not
     *         sent any entity header.
     * @throws NumberFormatException
     * @see {@link ClientAdapter#copyResponseTransportHeaders(Series, Response)}
     */
    public static Representation copyResponseEntityHeaders(
            Iterable<Parameter> responseHeaders, Representation representation)
            throws NumberFormatException {
        Representation result = (representation == null) ? new EmptyRepresentation()
                : representation;
        boolean entityHeaderFound = false;

        for (Parameter header : responseHeaders) {
            if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_CONTENT_TYPE)) {
                ContentType contentType = new ContentType(header.getValue());
                result.setMediaType(contentType.getMediaType());

                if ((result.getCharacterSet() == null)
                        || (contentType.getCharacterSet() != null)) {
                    result.setCharacterSet(contentType.getCharacterSet());
                }

                entityHeaderFound = true;
            } else if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_CONTENT_LENGTH)) {
                entityHeaderFound = true;
            } else if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_EXPIRES)) {
                result.setExpirationDate(HeaderUtils.parseDate(header
                        .getValue(), false));
                entityHeaderFound = true;
            } else if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_CONTENT_ENCODING)) {
                HeaderReader hr = new HeaderReader(header.getValue());
                String value = hr.readValue();
                while (value != null) {
                    Encoding encoding = new Encoding(value);
                    if (!encoding.equals(Encoding.IDENTITY)) {
                        result.getEncodings().add(encoding);
                    }
                    value = hr.readValue();
                }
                entityHeaderFound = true;
            } else if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_CONTENT_LANGUAGE)) {
                HeaderReader hr = new HeaderReader(header.getValue());
                String value = hr.readValue();
                while (value != null) {
                    result.getLanguages().add(new Language(value));
                    value = hr.readValue();
                }
                entityHeaderFound = true;
            } else if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_LAST_MODIFIED)) {
                result.setModificationDate(HeaderUtils.parseDate(header
                        .getValue(), false));
                entityHeaderFound = true;
            } else if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_ETAG)) {
                result.setTag(Tag.parse(header.getValue()));
                entityHeaderFound = true;
            } else if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_CONTENT_LOCATION)) {
                result.setIdentifier(header.getValue());
                entityHeaderFound = true;
            } else if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_CONTENT_DISPOSITION)) {
                try {
                    DispositionReader r = new DispositionReader(header
                            .getValue());
                    result.setDisposition(r.readDisposition());
                    entityHeaderFound = true;
                } catch (IOException ioe) {
                    Context.getCurrentLogger().log(
                            Level.WARNING,
                            "Error during Content-Disposition header parsing. Header: "
                                    + header.getValue(), ioe);
                }
            } else if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_CONTENT_RANGE)) {
                // [ifndef gwt]
                org.restlet.engine.http.header.RangeUtils.parseContentRange(
                        header.getValue(), result);
                entityHeaderFound = true;
                // [enddef]
            } else if (header.getName().equalsIgnoreCase(
                    HeaderConstants.HEADER_CONTENT_MD5)) {
                // [ifndef gwt]
                result.setDigest(new org.restlet.data.Digest(
                        org.restlet.data.Digest.ALGORITHM_MD5,
                        org.restlet.engine.util.Base64
                                .decode(header.getValue())));
                entityHeaderFound = true;
                // [enddef]
            }

        }

        // If no representation was initially expected and no entity header
        // is found, then do not return any representation
        if ((representation == null) && !entityHeaderFound) {
            result = null;
        }

        return result;
    }

    /**
     * Returns the local IP address or 127.0.0.1 if the resolution fails.
     * 
     * @return The local IP address or 127.0.0.1 if the resolution fails.
     */
    public static String getLocalAddress() {
        // [ifndef gae,gwt]
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (java.net.UnknownHostException e) {
            // [enddef]
            return "127.0.0.1";
            // [ifndef gae,gwt]
        }
        // [enddef]
    }

    /**
     * Parse the Content-Disposition header value
     * 
     * @param value
     *            Content-disposition header
     * @return Filename
     * @deprecated Use {@link DispositionReader} instead.
     */
    @Deprecated
    public static String parseContentDisposition(String value) {
        if (value != null) {
            String key = "FILENAME=\"";
            int index = value.toUpperCase().indexOf(key);
            if (index > 0) {
                return value
                        .substring(index + key.length(), value.length() - 1);
            }

            key = "FILENAME=";
            index = value.toUpperCase().indexOf(key);
            if (index > 0) {
                return value.substring(index + key.length(), value.length());
            }
        }

        return null;
    }

    /** The parent HTTP client helper. */
    private volatile HttpClientHelper helper;

    /**
     * Constructor setting the request address to the local host.
     * 
     * @param helper
     *            The parent HTTP client helper.
     * @param method
     *            The method name.
     * @param requestUri
     *            The request URI.
     */
    public ClientCall(HttpClientHelper helper, String method, String requestUri) {
        this.helper = helper;
        setMethod(method);
        setRequestUri(requestUri);
        setClientAddress(getLocalAddress());
    }

    /**
     * Returns the content length of the request entity if know,
     * {@link Representation#UNKNOWN_SIZE} otherwise.
     * 
     * @return The request content length.
     */
    protected long getContentLength() {
        return HeaderUtils.getContentLength(getResponseHeaders());
    }

    /**
     * Returns the HTTP client helper.
     * 
     * @return The HTTP client helper.
     */
    public HttpClientHelper getHelper() {
        return this.helper;
    }

    // [ifndef gwt] member
    /**
     * Returns the request entity channel if it exists.
     * 
     * @return The request entity channel if it exists.
     */
    public abstract java.nio.channels.WritableByteChannel getRequestEntityChannel();

    // [ifndef gwt] member
    /**
     * Returns the request entity stream if it exists.
     * 
     * @return The request entity stream if it exists.
     */
    public abstract OutputStream getRequestEntityStream();

    // [ifdef gwt] member uncomment
    // /**
    // * Returns the request entity string if it exists.
    // *
    // * @return The request entity string if it exists.
    // */
    // public abstract String getRequestEntityString();

    // [ifndef gwt] member
    /**
     * Returns the request head stream if it exists.
     * 
     * @return The request head stream if it exists.
     */
    public abstract OutputStream getRequestHeadStream();

    /**
     * Returns the response entity if available. Note that no metadata is
     * associated by default, you have to manually set them from your headers.
     * 
     * @param response
     *            the Response to get the entity from
     * @return The response entity if available.
     */
    public Representation getResponseEntity(Response response) {
        Representation result = null;
        // boolean available = false;
        long size = Representation.UNKNOWN_SIZE;

        // Compute the content length
        Series<Parameter> responseHeaders = getResponseHeaders();
        String transferEncoding = responseHeaders.getFirstValue(
                HeaderConstants.HEADER_TRANSFER_ENCODING, true);
        if ((transferEncoding != null)
                && !"identity".equalsIgnoreCase(transferEncoding)) {
            size = Representation.UNKNOWN_SIZE;
        } else {
            size = getContentLength();
        }

        if (!getMethod().equals(Method.HEAD.getName())
                && !response.getStatus().isInformational()
                && !response.getStatus()
                        .equals(Status.REDIRECTION_NOT_MODIFIED)
                && !response.getStatus().equals(Status.SUCCESS_NO_CONTENT)
                && !response.getStatus().equals(Status.SUCCESS_RESET_CONTENT)) {
            // Make sure that an InputRepresentation will not be instantiated
            // while the stream is closed.
            InputStream stream = getUnClosedResponseEntityStream(getResponseEntityStream(size));
            // [ifndef gwt] line
            java.nio.channels.ReadableByteChannel channel = getResponseEntityChannel(size);
            // [ifdef gwt] line uncomment
            // InputStream channel = null;

            if (stream != null) {
                result = getRepresentation(stream);
            } else if (channel != null) {
                result = getRepresentation(channel);
                // } else {
                // result = new EmptyRepresentation();
            }
        }

        result = copyResponseEntityHeaders(responseHeaders, result);
        if (result != null) {
            result.setSize(size);

            // Informs that the size has not been specified in the header.
            if (size == Representation.UNKNOWN_SIZE) {
                getLogger()
                        .fine(
                                "The length of the message body is unknown. The entity must be handled carefully and consumed entirely in order to surely release the connection.");
            }
        }
        // }

        return result;
    }

    // [ifndef gwt] member
    /**
     * Returns the response channel if it exists.
     * 
     * @param size
     *            The expected entity size or -1 if unknown.
     * @return The response channel if it exists.
     */
    public abstract java.nio.channels.ReadableByteChannel getResponseEntityChannel(
            long size);

    /**
     * Returns the response entity stream if it exists.
     * 
     * @param size
     *            The expected entity size or -1 if unknown.
     * @return The response entity stream if it exists.
     */
    public abstract InputStream getResponseEntityStream(long size);

    /**
     * Checks if the given input stream really contains bytes to be read. If so,
     * returns the inputStream otherwise returns null.
     * 
     * @param inputStream
     *            the inputStream to check.
     * @return null if the given inputStream does not contain any byte, an
     *         inputStream otherwise.
     */
    private InputStream getUnClosedResponseEntityStream(InputStream inputStream) {
        InputStream result = null;

        if (inputStream != null) {
            try {
                if (inputStream.available() > 0) {
                    result = inputStream;
                    // [ifndef gwt]
                } else {
                    java.io.PushbackInputStream is = new java.io.PushbackInputStream(
                            inputStream);
                    int i = is.read();

                    if (i >= 0) {
                        is.unread(i);
                        result = is;
                    }
                    // [enddef]
                }
            } catch (IOException ioe) {
                getLogger().log(Level.FINER, "End of response entity stream.",
                        ioe);
            }

        }

        return result;
    }

    @Override
    protected boolean isClientKeepAlive() {
        return true;
    }

    @Override
    protected boolean isServerKeepAlive() {
        String header = getResponseHeaders().getFirstValue(
                HeaderConstants.HEADER_CONNECTION, true);
        return (header == null) || !header.equalsIgnoreCase("close");
    }

    // [ifndef gwt] method
    /**
     * Sends the request to the client. Commits the request line, headers and
     * optional entity and send them over the network.
     * 
     * @param request
     *            The high-level request.
     * @return the status of the communication
     */
    public Status sendRequest(Request request) {
        Status result = null;
        Representation entity = request.isEntityAvailable() ? request
                .getEntity() : null;

        // Get the connector service to callback
        org.restlet.service.ConnectorService connectorService = ConnectorHelper
                .getConnectorService(request);
        if (connectorService != null) {
            connectorService.beforeSend(entity);
        }

        try {
            if (entity != null) {

                // In order to workaround bug #6472250
                // (http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6472250),
                // it is very important to reuse that exact same "requestStream"
                // reference when manipulating the request stream, otherwise
                // "insufficient data sent" exceptions will occur in
                // "fixedLengthMode"
                OutputStream requestStream = getRequestEntityStream();
                java.nio.channels.WritableByteChannel wbc = getRequestEntityChannel();

                if (wbc != null) {
                    entity.write(wbc);
                } else if (requestStream != null) {
                    entity.write(requestStream);
                    requestStream.flush();
                }

                if (requestStream != null) {
                    requestStream.close();
                } else if (wbc != null) {
                    wbc.close();
                }
            }

            // Now we can access the status code, this MUST happen after closing
            // any open request stream.
            result = new Status(getStatusCode(), null, getReasonPhrase(), null);
        } catch (IOException ioe) {
            getHelper()
                    .getLogger()
                    .log(
                            Level.FINE,
                            "An error occured during the communication with the remote HTTP server.",
                            ioe);
            result = new Status(Status.CONNECTOR_ERROR_COMMUNICATION, ioe);
        } finally {
            if (entity != null) {
                entity.release();
            }

            // Call-back after writing
            if (connectorService != null) {
                connectorService.afterSend(entity);
            }
        }

        return result;
    }

    /**
     * Sends the request to the client. Commits the request line, headers and
     * optional entity and send them over the network.
     * 
     * @param request
     *            The high-level request.
     * @param response
     *            The high-level response.
     * @param callback
     *            The callback invoked upon request completion.
     */
    public void sendRequest(Request request, Response response,
            org.restlet.Uniform callback) throws Exception {
        Context.getCurrentLogger().warning(
                "Currently callbacks are only implemented in the GWT edition.");
    }

    /**
     * Indicates if the request entity should be chunked.
     * 
     * @return True if the request should be chunked
     */
    protected boolean shouldRequestBeChunked(Request request) {
        return request.isEntityAvailable()
                && (request.getEntity() != null)
                && (request.getEntity().getSize() == Representation.UNKNOWN_SIZE);
    }
}
