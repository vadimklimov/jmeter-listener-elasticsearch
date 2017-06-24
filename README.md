
# Elasticsearch Listener for Apache JMeter
The listener plugin for Apache JMeter that enables delivery of samples' result data to Elasticsearch server using Elasticsearch API.

Usage of the plugin is described in SAP Community blog https://blogs.sap.com/2016/04/06/load-testing-with-jmeter-test-results-visualization-using-kibana-dashboards/.


## Prerequisites
* Java runtime 1.8.


## Dependencies
* Elasticsearch Java REST client library

Documentation: https://www.elastic.co/guide/en/elasticsearch/client/java-rest/index.html

Dependeny declaration for Maven POM:

<pre><code>&lt;dependency&gt;
	&lt;groupId&gt;org.elasticsearch.client&lt;/groupId&gt;
	&lt;artifactId&gt;rest&lt;/artifactId&gt;
	&lt;version&gt;RELEASE&lt;/version&gt;
&lt;/dependency&gt;</code></pre>
* Several libraries (such as HTTP client that is used by Elasticsearch Java REST client, JSON processor, logging framework) that are already a part of JMeter distribution (the statement is based on content of distribution of JMeter version 3.2).


## Installation
* Copy dependency libraries to the directory **/&lt;JMeter home&gt;/lib/**.
* Copy plugin library to the directory **/&lt;JMeter home&gt;/lib/ext/**.


## Parameterization
* **elasticsearch.protocol** - URI scheme (http or https) for accessing Elasticsearch server. Default: http.
* **elasticsearch.host** - host for accessing Elasticsearch server.
* **elasticsearch.port** - port for accessing Elasticsearch server. Default: 9200.
* **elasticsearch.user** - user name for authentication of requests sent to Elasticsearch server in case basic authentication is used. Default: empty (no authentication).
* **elasticsearch.password** - password for authentication of requests sent to Elasticsearch server in case basic authentication is used. Default: empty (no authentication).
* **elasticsearch.index** - index to which documents containing sample results that are to be added on Elasticsearch server.
* **elasticsearch.type** - document type of documents containing sample results that are to be added on Elasticsearch server.
* **timezone.id** - timezone identifier. Default: GMT.
* **result.attributes.excluded** - comma-separated names of sample result attributes that shall not be passed to Elasticsearch server. By default, all attributes are passed. Valid attributes' names: Timestamp, StartTime, EndTime, Time, Latency, ConnectTime, IdleTime, SampleLabel, GroupName, ThreadName, ResponseCode, IsResponseCodeOk, IsSuccessful, SampleCount, ErrorCount, ContentType, MediaType, DataType, RequestHeaders, ResponseHeaders, HeadersSize, SamplerData, ResponseMessage, ResponseData, BodySize, Bytes.


## Additional Notes
The listener plugin has been tested with JMeter version 3.2 running on JRE version 1.8 in conjunction with Elasticsearch server version 5.4.2.

In recent versions of JMeter API, several methods related to sample result processing, have been deprecated and newer replacing methods were introduced. The current version of the listener plugin makes use of the latter methods.

Recent versions of JMeter API deprecate usage of Jorphan Logging Manager, which is superseded with Simple Logging Facade for Java (SLF4J). The current version of the listener plugin makes use of SLF4J for logging. Current implementation creates log entries of ERROR, INFO and DEBUG levels.
