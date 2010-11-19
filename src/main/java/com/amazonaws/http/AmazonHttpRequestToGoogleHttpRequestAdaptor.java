package com.amazonaws.http;

import com.amazonaws.AmazonClientException;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

public class AmazonHttpRequestToGoogleHttpRequestAdaptor {

    /**
     * Creates an HttpClient method object based on the specified request and
     * populates any parameters, headers, etc. from the original request.
     *
     * @param amazonRequest
     *            The request to convert to an HttpClient method object.
     *
     * @return The converted HttpClient method object with any parameters,
     *         headers, etc. from the original request set.
     */
    public HTTPRequest convert(HttpRequest amazonRequest) throws MalformedURLException {
        URI endpoint = amazonRequest.getEndpoint();
        String uri = concatenateEnsuringCorrectNumberOfSlashes(endpoint.toString(), amazonRequest.getResourcePath());

        HTTPRequest method;
        if (amazonRequest.getMethodName() == HttpMethodName.POST) {
            /*
             * If there isn't any payload content to include in this request,
             * then try to include the POST parameters in the query body,
             * otherwise, just use the query string. For all AWS Query services,
             * the best behavior is putting the params in the request body for
             * POST requests, but we can't do that for S3.
             */
            method = new HTTPRequest(new URL(uri + toQueryString(amazonRequest.getParameters())), HTTPMethod.POST);
            if (amazonRequest.getContent() != null) {
                method.setPayload(toByteArray(amazonRequest.getContent()));
            }
        } else if (amazonRequest.getMethodName() == HttpMethodName.GET) {
            method = new HTTPRequest(new URL(uri + toQueryString(amazonRequest.getParameters())), HTTPMethod.GET);
        } else if (amazonRequest.getMethodName() == HttpMethodName.PUT) {
            method = new HTTPRequest(new URL(uri + toQueryString(amazonRequest.getParameters())), HTTPMethod.PUT);

            /*
             * URLFetchService doesn't explicitly support 100-continue behaviour, so remove code catering for it
             */

            if (amazonRequest.getContent() != null) {
                method.setPayload(toByteArray(amazonRequest.getContent()));
            }
        } else if (amazonRequest.getMethodName() == HttpMethodName.DELETE) {
            method = new HTTPRequest(new URL(uri + toQueryString(amazonRequest.getParameters())), HTTPMethod.DELETE);
        } else if (amazonRequest.getMethodName() == HttpMethodName.HEAD) {
            method = new HTTPRequest(new URL(uri + toQueryString(amazonRequest.getParameters())), HTTPMethod.HEAD);
        } else {
            throw new AmazonClientException("Unknown HTTP method name: " + amazonRequest.getMethodName());
        }

        // No matter what type of HTTP method we're creating, we need to copy
        // all the headers from the request.
        for (Map.Entry<String, String> entry : amazonRequest.getHeaders().entrySet()) {
            method.addHeader(new HTTPHeader(entry.getKey(), entry.getValue()));
        }

        return method;
    }

    private static String concatenateEnsuringCorrectNumberOfSlashes(String endpoint, String path) {
        if (path == null || path.isEmpty()) {
            if (endpoint.endsWith("/")) {
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
        for (Map.Entry<String,String> entry : parameters.entrySet()) {
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
