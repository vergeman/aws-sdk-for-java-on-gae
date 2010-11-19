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
        String uri = endpoint.toString();
        String path = amazonRequest.getResourcePath();
        if (path != null && path.length() > 0) {
            if (!path.startsWith("/")) {
                uri += "/";
            }
            uri += path;
        } else if (!uri.endsWith("/")) {
            uri += "/";
        };

        NameValuePair[] nameValuePairs = null;
        if (amazonRequest.getParameters().size() > 0) {
            nameValuePairs = new NameValuePair[amazonRequest.getParameters().size()];
            int i = 0;
            for (Map.Entry<String, String> entry : amazonRequest.getParameters().entrySet()) {
                nameValuePairs[i++] = new NameValuePair(entry.getKey(), entry.getValue());
            }
        }

        HTTPRequest method;
        if (amazonRequest.getMethodName() == HttpMethodName.POST) {
            /*
             * If there isn't any payload content to include in this request,
             * then try to include the POST parameters in the query body,
             * otherwise, just use the query string. For all AWS Query services,
             * the best behavior is putting the params in the request body for
             * POST requests, but we can't do that for S3.
             */
            if (nameValuePairs != null) uri += toQueryString(nameValuePairs);

            method = new HTTPRequest(new URL(uri), HTTPMethod.POST);
            if (amazonRequest.getContent() != null) {
                method.setPayload(toByteArray(amazonRequest.getContent()));
            }
        } else if (amazonRequest.getMethodName() == HttpMethodName.GET) {
            if (nameValuePairs != null) uri += toQueryString(nameValuePairs);
            method = new HTTPRequest(new URL(uri), HTTPMethod.GET);
        } else if (amazonRequest.getMethodName() == HttpMethodName.PUT) {
            if (nameValuePairs != null) uri += toQueryString(nameValuePairs);
            method = new HTTPRequest(new URL(uri), HTTPMethod.PUT);

            /*
             * URLFetchService doesn't explicitly support 100-continue behaviour, so remove code catering for it
             */

            if (amazonRequest.getContent() != null) {
                method.setPayload(toByteArray(amazonRequest.getContent()));
            }
        } else if (amazonRequest.getMethodName() == HttpMethodName.DELETE) {
            if (nameValuePairs != null) uri += toQueryString(nameValuePairs);
            method = new HTTPRequest(new URL(uri), HTTPMethod.DELETE);
        } else if (amazonRequest.getMethodName() == HttpMethodName.HEAD) {
            if (nameValuePairs != null) uri += toQueryString(nameValuePairs);
            method = new HTTPRequest(new URL(uri), HTTPMethod.HEAD);
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

    static class NameValuePair {
        private String name;
        private String value;

        public NameValuePair(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    public static String toQueryString(NameValuePair[] nameValuePairs) {
        StringBuilder queryString = new StringBuilder("?");
        for (NameValuePair nameValuePair : nameValuePairs) {
            try {
                queryString.append(URLEncoder.encode(nameValuePair.getName(), "UTF-8"));
                queryString.append("=");
                queryString.append(URLEncoder.encode(nameValuePair.getValue(), "UTF-8"));
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
