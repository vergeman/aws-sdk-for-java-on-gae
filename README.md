# AWS SDK for Java on GAE

The official [AWS SDK for Java](http://aws.amazon.com/sdkforjava) doesn't work on [Google App Engine](http://code.google.com/appengine/), because the SDK uses the [Apache HttpClient](http://hc.apache.org/httpclient-3.x/) whose socket creation antics are not permitted inside the GAE sandbox.  There are a number of ways of working around this problem, but this particular fork takes the brute force approach of ripping out Apache HttpClient, and hardwiring the GAE-specific [UrlFetchService](http://code.google.com/appengine/docs/java/javadoc/com/google/appengine/api/urlfetch/URLFetchService.html) in its place.

## Notable changes from the official version

The SDK was already pretty well isolated from Apache HttpClient, so the code changes are pretty much contained to a single class [com.amazonaws.http.HttpClient](https://github.com/apcj/aws-sdk-for-java-on-gae/blob/master/src/main/java/com/amazonaws/http/HttpClient.java).

Also, dependencies have changes, since the library now depends on the [Google App Engine API](http://code.google.com/appengine/docs/java/overview.html), and no longer depends on [Apache HttpClient](http://hc.apache.org/httpclient-3.x/).

## Building

Nothing has changed in the build department from the official version, so you should still build with [Maven](http://maven.apache.org/).  It seems to work fine with  default Maven settings, fetching all dependencies from public repositories.

	$ mvn clean package

## Using

If you have an existing GAE app, just drop the jar output from this project into your.  GAE apps don't use Maven by default, so you'll have to fish these dependencies from your local maven repository, or find them somewhere else:

	<dependency>
	    <groupId>commons-logging</groupId>
	    <artifactId>commons-logging</artifactId>
	    <version>[1.1, 2.0)</version>
	</dependency>
	<dependency>
	    <groupId>commons-codec</groupId>
	    <artifactId>commons-codec</artifactId>
	    <version>1.3</version>
	</dependency>
	<dependency>
	    <groupId>org.codehaus.jackson</groupId>
	    <artifactId>jackson-core-asl</artifactId>
	    <version>[1.4,)</version>
	    <type>jar</type>
	    <scope>compile</scope>
	</dependency>

## Why would I want to use the SDK on GAE anyway?

There is a certain class of app that can benefit from combining the flexibility of AWS with the simplicity of GAE.  This is particularly relevant for very low traffic apps which can completely shut down their AWS foot print during periods of very low traffic.

