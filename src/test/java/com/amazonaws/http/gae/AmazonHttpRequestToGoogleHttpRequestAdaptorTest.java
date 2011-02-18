package com.amazonaws.http.gae;

import com.amazonaws.Request;
import com.amazonaws.http.HttpClient;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.http.HttpRequest;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

public class AmazonHttpRequestToGoogleHttpRequestAdaptorTest {

    @Test
    public void shouldConcatenateEndpointWithPathAndQueryString() throws Exception {
        Request<?> amazonRequest = HttpClient.convertToRequest(new HttpRequest(HttpMethodName.GET));
        amazonRequest.setEndpoint(new URI("https://endpoint"));
        amazonRequest.setResourcePath("/resource/path/");
        amazonRequest.addParameter("key1", "value1");
        amazonRequest.addParameter("key2", "value2");

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://endpoint/resource/path/?key2=value2&key1=value1", googleRequest.getURL().toString());
    }

    @Test
    public void shouldInsertSlashBetweenEndpointAndPathWhenNeitherHasOne() throws Exception {
        Request<?> amazonRequest = HttpClient.convertToRequest(new HttpRequest(HttpMethodName.GET));
        amazonRequest.setEndpoint(new URI("https://hostname.without.a.trailing.slash"));
        amazonRequest.setResourcePath("resource/without/a/leading/slash/");

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://hostname.without.a.trailing.slash/resource/without/a/leading/slash/", googleRequest.getURL().toString());
    }

    @Test
    public void shouldAppendTrailingSlashWhenEndpointLacksOneAndNeitherPathNorQueryStringSupplied() throws Exception {
        Request<?> amazonRequest = HttpClient.convertToRequest(new HttpRequest(HttpMethodName.GET));
        amazonRequest.setEndpoint(new URI("https://hostname.without.a.trailing.slash"));

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://hostname.without.a.trailing.slash/", googleRequest.getURL().toString());
    }

    @Test
    public void shouldAvoidDoubleSlashWhenBothEndpointAndPathSupplyOne() throws Exception {
        Request<?> amazonRequest = HttpClient.convertToRequest(new HttpRequest(HttpMethodName.GET));
        amazonRequest.setEndpoint(new URI("https://endpoint/"));
        amazonRequest.setResourcePath("/resource/path/");

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://endpoint/resource/path/", googleRequest.getURL().toString());
    }

    @Test
    public void shouldNotAddAdditionalSlashWhenEndpointContainsOneButPathIsEmpty() throws Exception {
        Request<?> amazonRequest = HttpClient.convertToRequest(new HttpRequest(HttpMethodName.GET));
        amazonRequest.setEndpoint(new URI("https://endpoint.with.hostname/and_a_path"));
        amazonRequest.setResourcePath("");

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://endpoint.with.hostname/and_a_path", googleRequest.getURL().toString());
    }

    @Test
    public void shouldAddSlashBetweenHostAndQueryStringWhenEndpointHasNoSlashAndPathIsEmpty() throws Exception {
        Request<?> amazonRequest = HttpClient.convertToRequest(new HttpRequest(HttpMethodName.GET));
        amazonRequest.setEndpoint(new URI("https://hostname.without.a.trailing.slash"));
        amazonRequest.addParameter("key", "value");

        HTTPRequest googleRequest = new AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(amazonRequest);
        assertEquals("https://hostname.without.a.trailing.slash/?key=value", googleRequest.getURL().toString());
    }

    @Test
    public void shouldMapAllSupportedHttpMethods() throws Exception {
        AmazonHttpRequestToGoogleHttpRequestAdaptor adaptor = new AmazonHttpRequestToGoogleHttpRequestAdaptor();
        assertEquals(HTTPMethod.GET, adaptor.convert(request(HttpMethodName.GET)).getMethod());
        assertEquals(HTTPMethod.POST, adaptor.convert(request(HttpMethodName.POST)).getMethod());
        assertEquals(HTTPMethod.PUT, adaptor.convert(request(HttpMethodName.PUT)).getMethod());
        assertEquals(HTTPMethod.DELETE, adaptor.convert(request(HttpMethodName.DELETE)).getMethod());
        assertEquals(HTTPMethod.HEAD, adaptor.convert(request(HttpMethodName.HEAD)).getMethod());
    }

    private static Request<?> request(HttpMethodName method) throws URISyntaxException {
        HttpRequest request = new HttpRequest(method);
        request.setEndpoint(new URI("http://endpoint"));
        return HttpClient.convertToRequest(request);
    }

    @Test
    public void shouldPutContentIntoPayload() throws Exception {
        HttpRequest amazonRequest = new HttpRequest(HttpMethodName.POST);
        amazonRequest.setEndpoint(new URI("https://endpoint/"));
        amazonRequest.setContent(new ByteArrayInputStream("PAYLOAD".getBytes()));

        HTTPRequest googleRequest = new
          AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(HttpClient.convertToRequest(amazonRequest));
        assertEquals("https://endpoint/", googleRequest.getURL().toString());
        assertEquals("PAYLOAD", new String(googleRequest.getPayload()));
    }

    @Test
    public void shouldPutParamatersInQueryStringWhenContentAlsoSupplied() throws Exception {
        HttpRequest amazonRequest = new HttpRequest(HttpMethodName.POST);
        amazonRequest.setEndpoint(new URI("https://endpoint/"));
        amazonRequest.addParameter("key", "value");
        amazonRequest.setContent(new ByteArrayInputStream("PAYLOAD".getBytes()));

        HTTPRequest googleRequest = new
          AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(HttpClient.convertToRequest(amazonRequest));
        assertEquals("https://endpoint/?key=value", googleRequest.getURL().toString());
        assertEquals("PAYLOAD", new String(googleRequest.getPayload()));
    }
    
    @Test
    public void shouldIncludeParametersInRequestBodyForPostRequestIfNoContentSupplied() throws Exception {
        HttpRequest amazonRequest = new HttpRequest(HttpMethodName.POST);
        amazonRequest.setEndpoint(new URI("https://endpoint/"));
        amazonRequest.addParameter("key", "value");

        HTTPRequest googleRequest = new
          AmazonHttpRequestToGoogleHttpRequestAdaptor().convert(HttpClient.convertToRequest(amazonRequest));
        assertEquals("https://endpoint/", googleRequest.getURL().toString());
        assertEquals("key=value", new String(googleRequest.getPayload()));
    }

}
