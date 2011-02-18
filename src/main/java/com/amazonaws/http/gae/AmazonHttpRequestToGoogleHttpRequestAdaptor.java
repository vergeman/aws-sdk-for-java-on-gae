package com.amazonaws.http.gae;

import com.amazonaws.AmazonClientException;
import com.amazonaws.Request;
import com.amazonaws.http.HttpMethodName;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.http.HttpMethodName.*;

public class AmazonHttpRequestToGoogleHttpRequestAdaptor {

    static final Map<HttpMethodName, HTTPMethod> methodMap = new HashMap<HttpMethodName, HTTPMethod>();

    static {
        methodMap.put(GET, HTTPMethod.GET);
        methodMap.put(POST, HTTPMethod.POST);
        methodMap.put(PUT, HTTPMethod.PUT);
        methodMap.put(DELETE, HTTPMethod.DELETE);
        methodMap.put(HEAD, HTTPMethod.HEAD);
    }

    /**
     * Creates an HttpClient method object based on the specified request and
     * populates any parameters, headers, etc. from the original request.
     *
     * @param amazonRequest The request to convert to an HttpClient method object.
     * @return The converted HttpClient method object with any parameters,
     *         headers, etc. from the original request set.
     * @throws java.net.MalformedURLException If supplied request includes a malformed URL.
     */
    public HTTPRequest convert(Request<?> amazonRequest) throws MalformedURLException {
        String uri = concatenateEnsuringCorrectNumberOfSlashes(String.valueOf(amazonRequest.getEndpoint()),
                amazonRequest.getResourcePath());

        HTTPRequest googleRequest;
        HttpMethodName method = amazonRequest.getHttpMethod();
        if (method == POST || method == PUT) {
            /*
             * If there isn't any payload content to include in this request,
             * then try to include the POST parameters in the query body,
             * otherwise, just use the query string. For all AWS Query services,
             * the best behavior is putting the params in the request body for
             * POST requests, but we can't do that for S3.
             */
            if (amazonRequest.getContent() == null) {
                googleRequest = new HTTPRequest(new URL(uri), methodMap.get(method));
                if (!toQueryString(amazonRequest.getParameters()).isEmpty()) {
                    googleRequest.setPayload(toQueryString(amazonRequest.getParameters()).substring(1).getBytes());
                }
            } else {
                googleRequest = new HTTPRequest(new URL(uri + toQueryString(amazonRequest.getParameters())), methodMap.get(method));
                googleRequest.setPayload(toByteArray(amazonRequest.getContent()));
            }
        } else if (methodMap.containsKey(method)) {
            googleRequest = new HTTPRequest(new URL(uri + toQueryString(amazonRequest.getParameters())), methodMap.get(method));
        } else {
            throw new AmazonClientException("Unknown HTTP method name: " + method);
        }

        // No matter what type of HTTP googleRequest we're creating, we need to copy
        // all the headers from the request.
        for (Map.Entry<String, String> entry : amazonRequest.getHeaders().entrySet()) {
            googleRequest.addHeader(new HTTPHeader(entry.getKey(), entry.getValue()));
        }

        return googleRequest;
    }

    private static String concatenateEnsuringCorrectNumberOfSlashes(String endpoint, String path) {
        if (path == null || path.isEmpty()) {
            if (endpoint.replaceAll("://", "").contains("/")) {
                return endpoint;
            } else {
                return endpoint + "/";
            }
        } else {
            if (path.startsWith("/")) {
                if (endpoint.endsWith("/")) {
                    return endpoint + path.substring(1);
                } else {
                    return endpoint + path;
                }
            } else {
                if (endpoint.endsWith("/")) {
                    return endpoint + path;
                } else {
                    return endpoint + "/" + path;
                }
            }
        }
    }

    public static String toQueryString(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) return "";

        StringBuilder queryString = new StringBuilder("?");
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            try {
                queryString.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                queryString.append("=");
                queryString.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                queryString.append("&");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        return queryString.substring(0, queryString.length() - 1);
    }

    public static byte[] toByteArray(InputStream inputStream) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int n;
        try {
            while (-1 != (n = inputStream.read(buffer))) {
                output.write(buffer, 0, n);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return output.toByteArray();
    }

}
