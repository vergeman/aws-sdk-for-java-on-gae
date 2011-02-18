/*
 * Copyright 2010-2011 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.http;

import java.io.*;
import java.net.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import com.amazonaws.http.gae.AmazonHttpRequestToGoogleHttpRequestAdaptor;
import com.google.appengine.api.urlfetch.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.handlers.RequestHandler;
import com.amazonaws.util.CountingInputStream;
import com.amazonaws.util.HttpUtils;
import com.amazonaws.util.ResponseMetadataCache;
import com.amazonaws.util.TimingInfo;

public class HttpClient {

    /**
     * Logger providing detailed information on requests/responses. Users can
     * enable this logger to get access to AWS request IDs for responses,
     * individual requests and parameters sent to AWS, etc.
     */
    private static final Log requestLog = LogFactory.getLog("com.amazonaws.request");

    /**
     * Logger for more detailed debugging information, that might not be as
     * useful for end users (ex: HTTP client configuration, etc).
     */
    private static final Log log = LogFactory.getLog(HttpClient.class);

    private static final Log unmarshallerPerformanceLog = LogFactory.getLog("com.amazonaws.unmarshaller.performance");

    /** Internal client for sending HTTP requests */
    private URLFetchService urlFetchService;
    private AmazonHttpRequestToGoogleHttpRequestAdaptor requestAdaptor = new AmazonHttpRequestToGoogleHttpRequestAdaptor();

    private static final String DEFAULT_ENCODING = "UTF-8";

    /** Maximum exponential back-off time before retrying a request */
    private static final int MAX_BACKOFF_IN_MILLISECONDS = 20 * 1000;

    /** Client configuration options, such as proxy settings, max retries, etc. */
    private final ClientConfiguration config;

    /** Cache of metadata for recently executed requests for diagnostic purposes */
    private ResponseMetadataCache responseMetadataCache = new ResponseMetadataCache(50);

    private Random random = new Random();


    static {
        // Customers have reported XML parsing issues with the following
        // JVM versions, which don't occur with more recent versions, so
        // if we detect any of these, give customers a heads up.
        List<String> problematicJvmVersions = Arrays.asList(new String[] {
                "1.6.0_06", "1.6.0_13", "1.6.0_17", });
        String jvmVersion = System.getProperty("java.version");
        if (problematicJvmVersions.contains(jvmVersion)) {
            log.warn("Detected a possible problem with the current JVM version (" + jvmVersion + ").  " +
                     "If you experience XML parsing problems using the SDK, try upgrading to a more recent JVM update.");
        }
    }

    /**
     * Constructs a new AWS client using the specified client configuration
     * options (ex: max retry attempts, proxy settings, etc).
     *
     * @param clientConfiguration
     *            Configuration options specifying how this client will
     *            communicate with AWS (ex: proxy settings, retry count, etc.).
     */
    public HttpClient(ClientConfiguration clientConfiguration) {
        this.config = clientConfiguration;
        urlFetchService = URLFetchServiceFactory.getURLFetchService();
    }

    /**
     * Returns additional response metadata for an executed request. Response
     * metadata isn't considered part of the standard results returned by an
     * operation, so it's accessed instead through this diagnostic interface.
     * Response metadata is typically used for troubleshooting issues with AWS
     * support staff when services aren't acting as expected.
     *
     * @param request
     *            A previously executed AmazonWebServiceRequest object, whose
     *            response metadata is desired.
     *
     * @return The response metadata for the specified request, otherwise null
     *         if there is no response metadata available for the request.
     */
    public ResponseMetadata getResponseMetadataForRequest(AmazonWebServiceRequest request) {
        return responseMetadataCache.get(request);
    }



    public <T> T execute(Request<?> request,
            HttpResponseHandler<AmazonWebServiceResponse<T>> responseHandler,
            HttpResponseHandler<AmazonServiceException> errorResponseHandler,
    		ExecutionContext executionContext) throws AmazonClientException, AmazonServiceException {
    	long startTime = System.currentTimeMillis();

    	/*
    	 * TODO: Ideally, we'd run the "beforeRequest" on any request handlers here, but
    	 *       we have to run that code *before* signing the request, since it could change
    	 *       request parameters.
    	 */

    	if (executionContext == null) throw new AmazonClientException("Internal SDK Error: No execution context parameter specified.");
    	List<RequestHandler> requestHandlers = executionContext.requestHandlers;
    	if (requestHandlers == null) requestHandlers = new ArrayList<RequestHandler>();

    	try {
    		T t = execute(request, responseHandler, errorResponseHandler);
    		TimingInfo timingInfo = new TimingInfo(startTime, System.currentTimeMillis());
			for (RequestHandler handler : requestHandlers) {
				try {
					handler.afterResponse(request, t, timingInfo);
				} catch (ClassCastException cce) {}
        	}
    		return t;
    	} catch (AmazonClientException e) {
			for (RequestHandler handler : requestHandlers) {
        		handler.afterError(request, e);
        	}
        	throw e;
    	}
    }

    @Deprecated
    public <T extends Object> T execute(HttpRequest httpRequest,
            HttpResponseHandler<AmazonWebServiceResponse<T>> responseHandler,
            HttpResponseHandler<AmazonServiceException> errorResponseHandler)
            throws AmazonClientException, AmazonServiceException {
    	return execute(convertToRequest(httpRequest), responseHandler, errorResponseHandler);
    }

    @Deprecated
    public static Request<?> convertToRequest(HttpRequest httpRequest) {
        Request<?> request = new DefaultRequest(httpRequest.getServiceName());
        request.setContent(httpRequest.getContent());
        request.setEndpoint(httpRequest.getEndpoint());
        request.setHttpMethod(httpRequest.getMethodName());
        request.setResourcePath(httpRequest.getResourcePath());

        for (Entry<String, String> parameter : httpRequest.getParameters().entrySet()) {
            request.addParameter(parameter.getKey(), parameter.getValue());
        }

        for (Entry<String, String> parameter : httpRequest.getHeaders().entrySet()) {
            request.addHeader(parameter.getKey(), parameter.getValue());
        }

        return request;
    }

    public <T extends Object> T execute(Request<?> request,
            HttpResponseHandler<AmazonWebServiceResponse<T>> responseHandler,
            HttpResponseHandler<AmazonServiceException> errorResponseHandler)
            throws AmazonClientException, AmazonServiceException {

        HTTPRequest method;
        try {
            method = requestAdaptor.convert(request);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        /* Set content type and encoding */
        List<HTTPHeader> headers = method.getHeaders();
        if (findHeader(headers, "Content-Type") == null) {
            log.debug("Setting content-type to application/x-www-form-urlencoded; " +
            		"charset=" + DEFAULT_ENCODING.toLowerCase());
            method.addHeader(new HTTPHeader("Content-Type",
                    "application/x-www-form-urlencoded; " +
                    "charset=" + DEFAULT_ENCODING.toLowerCase()));
        } else {
            log.debug("Not overwriting Content-Type; already set to: " + findHeader(headers, "Content-Type").getValue());
        }

        /*
         * Apache HttpClient omits the port number in the Host header (even if
         * we explicitly specify it) if it's the default port for the protocol
         * in use. To ensure that we use the same Host header in the request and
         * in the calculated string to sign (even if Apache HttpClient changed
         * and started honoring our explicit host with endpoint), we follow this
         * same behavior here and in the QueryString signer.
         */
        String hostHeader = request.getEndpoint().getHost();
        if (HttpUtils.isUsingNonDefaultPort(request.getEndpoint())) {
            hostHeader += ":" + request.getEndpoint().getPort();
        }
        method.addHeader(new HTTPHeader("Host", hostHeader));

        requestLog.info("Sending Request: " + request.toString());

        int retries = 0;
        AmazonServiceException exception = null;
        while (true) {
            try {
                if (retries > 0) pauseExponentially(retries, exception);
                exception = null;
                retries++;
                
                HTTPResponse response = urlFetchService.fetch(method);

                if (isRequestSuccessful(response.getResponseCode())) {
                    /*
                     * If we get back any 2xx response code, then we know we should
                     * treat the service call as successful.
                     */
                    return handleResponse(request, responseHandler, response);
                    /*
                     * URLFetchService follows redirects by default, so explicit code for handling
                     * redirection response code has been removed.
                     */
                } else {
                    exception = handleErrorResponse(request, errorResponseHandler, response);

                    if (!shouldRetry(exception, retries)) {
                        throw exception;
                    }
                }
            } catch (IOException ioe) {
                log.warn("Unable to execute HTTP request: " + ioe.getMessage());

                if (!shouldRetry(ioe, retries)) {
                    throw new AmazonClientException("Unable to execute HTTP request: " + ioe.getMessage(), ioe);
                }
            }
        }
    }

    private HTTPHeader findHeader(List<HTTPHeader> headers, String name) {
        for (HTTPHeader header : headers) {
            if (header.getName().equals(name)) return header;
        }
        return null;
    }

    /**
     * Returns true if a failed request should be retried.
     *
     * @param exception
     *            The exception from the failed request.
     * @param retries
     *            The number of times the current request has been attempted.
     *
     * @return True if the failed request should be retried.
     */
    private boolean shouldRetry(Exception exception, int retries) {
        if (retries > config.getMaxErrorRetry()) {
            return false;
        }

        if (exception instanceof SocketException
            || exception instanceof SocketTimeoutException) {
            log.debug("Retrying on " + exception.getClass().getName()
                    + ": " + exception.getMessage());
            return true;
        }


        if (exception instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException)exception;

            /*
             * For 500 internal server errors and 503 service
             * unavailable errors, we want to retry, but we need to use
             * an exponential back-off strategy so that we don't overload
             * a server with a flood of retries. If we've surpassed our
             * retry limit we handle the error response as a non-retryable
             * error and go ahead and throw it back to the user as an exception.
             */
            if (ase.getStatusCode() == 500
                || ase.getStatusCode() == 503) {
                return true;
            }

            /*
             * Throttling is reported as a 400 error from newer services. To try
             * and smooth out an occasional throttling error, we'll pause and
             * retry, hoping that the pause is long enough for the request to
             * get through the next time.
             */
            if (isThrottlingException(ase)) return true;
        }

        return false;
    }

    private boolean isRequestSuccessful(int status) {
        return status / 100 == 2;
    }

    /**
     * Handles a successful response from a service call by unmarshalling the
     * results using the specified response handler.
     *
     * @param request
     *            The original request that generated the response being
     *            handled.
     * @param responseHandler
     *            The response unmarshaller used to interpret the contents of
     *            the response.
     * @param response
     *            The HTTP response that was invoked, and contains the contents of
     *            the response.
     *
     * @return The contents of the response, unmarshalled using the specified
     *         response handler.
     *
     * @throws IOException
     *             If any problems were encountered reading the response
     *             contents from the HTTP response object.
     */
    private <T> T handleResponse(Request<?> request,
            HttpResponseHandler<AmazonWebServiceResponse<T>> responseHandler, HTTPResponse response)
            throws IOException {

        HttpResponse httpResponse = createAmazonResponseFromGoogleResponse(request, response);

        try {
            CountingInputStream countingInputStream = null;
            if (unmarshallerPerformanceLog.isTraceEnabled()) {
                countingInputStream = new CountingInputStream(httpResponse.getContent());
                httpResponse.setContent(countingInputStream);
            }

            long startTime = System.currentTimeMillis();
            AmazonWebServiceResponse<? extends T> awsResponse = responseHandler.handle(httpResponse);
            long endTime = System.currentTimeMillis();

            if (unmarshallerPerformanceLog.isTraceEnabled()) {
                unmarshallerPerformanceLog.trace(
                        countingInputStream.getByteCount() + ", " + (endTime - startTime));
            }

            if (awsResponse == null)
                throw new RuntimeException("Unable to unmarshall response metadata");

            responseMetadataCache.add(request.getOriginalRequest(), awsResponse.getResponseMetadata());

            requestLog.info("Received successful response: " + response.getResponseCode()
                    + ", AWS Request ID: " + awsResponse.getRequestId());

            return awsResponse.getResult();
        } catch (Exception e) {
            String errorMessage = "Unable to unmarshall response (" + e.getMessage() + "): "
                                + new String(response.getContent());
            log.error(errorMessage, e);
            throw new AmazonClientException(errorMessage, e);
        }
    }

    /**
     * Responsible for handling an error response, including unmarshalling the
     * error response into the most specific exception type possible, and
     * throwing the exception.
     *
     * @param request
     *            The request that generated the error response being handled.
     * @param errorResponseHandler
     *            The response handler responsible for unmarshalling the error
     *            response.
     * @param method
     *            The HTTP method containing the actual response content.
     *
     * @throws IOException
     *             If any problems are encountering reading the error response.
     * @return AmazonServiceException
     */
    private AmazonServiceException handleErrorResponse(Request request,
            HttpResponseHandler<AmazonServiceException> errorResponseHandler,
            HTTPResponse method) throws IOException {

        int status = method.getResponseCode();
        HttpResponse response = createAmazonResponseFromGoogleResponse(request, method);

        AmazonServiceException exception;

        try {
            exception = errorResponseHandler.handle(response);
            requestLog.info("Received error response: " + exception.toString());
        } catch (Exception e) {
            String errorMessage = "Unable to unmarshall error response (" + e.getMessage() + "): "
                                + new String(method.getContent());
            log.error(errorMessage, e);
            throw new AmazonClientException(errorMessage, e);
        }

        exception.setStatusCode(status);
        exception.setServiceName(request.getServiceName());
        exception.fillInStackTrace();
        return exception;
    }

    /**
     * Creates and initializes an HttpResponse object suitable to be passed to
     * an HTTP response handler object.
     *
     * @param request
     *            The HTTP request associated with the response.
     *
     * @param response
     *            The HTTP response that was invoked to get the response.
     * @return The new, initialized HttpResponse object ready to be passed to an
     *         HTTP response handler object.
     *
     * @throws IOException
     *             If there were any problems getting any response information
     *             from the HttpClient response object.
     */
    private HttpResponse createAmazonResponseFromGoogleResponse(Request request, HTTPResponse response) throws IOException {
        HttpResponse httpResponse = new HttpResponse(request);

        httpResponse.setContent(new ByteArrayInputStream(response.getContent()));
        httpResponse.setStatusCode(response.getResponseCode());
        httpResponse.setStatusText("Status text not available from URLFetchService");
        for (HTTPHeader header : response.getHeaders()) {
            httpResponse.addHeader(header.getName(), header.getValue());
        }

        return httpResponse;
    }

    /**
     * Exponential sleep on failed request to avoid flooding a service with
     * retries.
     *
     * @param retries
     *            Current retry count.
     * @param previousException
     *            Exception information for the previous attempt, if any.
     */
    private void pauseExponentially(int retries, AmazonServiceException previousException) {
        long scaleFactor = 300;
        if ( isThrottlingException(previousException) ) {
            scaleFactor = 500 + random.nextInt(100);
        }
        long delay = (long) (Math.pow(2, retries) * scaleFactor);

        delay = Math.min(delay, MAX_BACKOFF_IN_MILLISECONDS);
        log.debug("Retriable error detected, will retry in " + delay + "ms, attempt number: " + retries);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
        	throw new AmazonClientException(e.getMessage(), e);
        }
    }

    /**
     * Returns true if the specified exception is a throttling error.
     *
     * @param ase
     *            The exception to test.
     *
     * @return True if the exception resulted from a throttling error message
     *         from a service, otherwise false.
     */
    private boolean isThrottlingException(AmazonServiceException ase) {
        if (ase == null) return false;
        return "Throttling".equals(ase.getErrorCode());
    }
}
