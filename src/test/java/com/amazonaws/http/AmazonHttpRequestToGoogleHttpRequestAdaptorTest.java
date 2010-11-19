package com.amazonaws.http;

import com.google.appengine.api.urlfetch.HTTPRequest;
import org.junit.Test;

import java.net.URI;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class AmazonHttpRequestToGoogleHttpRequestAdaptorTest {
               
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
