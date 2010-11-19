package com.amazonaws.http;

import com.google.appengine.api.urlfetch.HTTPRequest;
import org.junit.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

public class AmazonHttpRequestToGoogleHttpRequestAdaptorTest {
    
    @Test
    public void shouldConcatenateEndpointWithPathAndQueryString() throws Exception {
        HttpRequest amazonRequest = new HttpRequest(HttpMethodName.GET);
        amazonRequest.setEndpoint(new URI("https://endpoint"));
        amazonRequest.setResourcePath("/resource/path/");
        Map<String, String> paramaters = new TreeMap<String, String>();
        paramaters.put("key1", "value1");
        paramaters.put("key2", "value2");
        amazonRequest.setParameters(paramaters);

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://endpoint/resource/path/?key1=value1&key2=value2", googleRequest.getURL().toString());
    }

    @Test
    public void shouldInsertSlashBetweenEndpointAndPathWhenNeitherHasOne() throws Exception {
        HttpRequest amazonRequest = new HttpRequest(HttpMethodName.GET);
        amazonRequest.setEndpoint(new URI("https://hostname.without.a.trailing.slash"));
        amazonRequest.setResourcePath("resource/without/a/leading/slash/");

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://hostname.without.a.trailing.slash/resource/without/a/leading/slash/", googleRequest.getURL().toString());
    }

    @Test
    public void shouldAppendTrailingSlashWhenEndpointLacksOneAndNeitherPathNorQueryStringSupplied() throws Exception {
        HttpRequest amazonRequest = new HttpRequest(HttpMethodName.GET);
        amazonRequest.setEndpoint(new URI("https://hostname.without.a.trailing.slash"));

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://hostname.without.a.trailing.slash/", googleRequest.getURL().toString());
    }

    @Test
    public void shouldAvoidDoubleSlashWhenBothEndpointAndPathSupplyOne() throws Exception {
        HttpRequest amazonRequest = new HttpRequest(HttpMethodName.GET);
        amazonRequest.setEndpoint(new URI("https://endpoint/"));
        amazonRequest.setResourcePath("/resource/path/");

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://endpoint/resource/path/", googleRequest.getURL().toString());
    }

    @Test
    public void shouldAddSlashBetweenHostAndQueryStringWhenEndpointHasNoSlashAndPathIsEmpty() throws Exception {
        HttpRequest amazonRequest = new HttpRequest(HttpMethodName.GET);
        amazonRequest.setEndpoint(new URI("https://hostname.without.a.trailing.slash"));
        HashMap<String, String> paramaters = new HashMap<String, String>();
        paramaters.put("key", "value");
        amazonRequest.setParameters(paramaters);

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://hostname.without.a.trailing.slash/?key=value", googleRequest.getURL().toString());
    }
}
