
# Elasticsearch listener for Apache JMeter
The listener plugin for Apache JMeter that enables delivery of samples' result data to Elasticsearch server using Elasticsearch API.

Usage of the plugin is described in SAP Community blog https://blogs.sap.com/2016/04/06/load-testing-with-jmeter-test-results-visualization-using-kibana-dashboards/.


## Prerequisites
* Java runtime 1.8 or higher.


## Dependencies
* Elasticsearch Java REST client library

Documentation: https://www.elastic.co/guide/en/elasticsearch/client/java-rest/index.html

Dependency declaration for Maven POM:

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
|Parameter|Description|Default value|
|---|---|---|
|elasticsearch.url|Elasticsearch server URL in format &lt;scheme&gt;://&lt;host&gt;:&lt;port&gt;|http://localhost:9200|
|elasticsearch.index|Index to which documents containing sample results are to be added on Elasticsearch server||
|elasticsearch.type|Document type of documents containing sample results that are to be added on Elasticsearch server||
|elasticsearch.authenticationMethod|In case authentication is required at Elasticsearch server, authentication method to be used. If no authentication is required, the parameter value shall be left empty. Currently supported authentication methods: BASIC|Empty (no authentication)|
|elasticsearch.user|User name for authentication of requests sent to Elasticsearch server. Applicable to basic authentication method||
|elasticsearch.password|Password for authentication of requests sent to Elasticsearch server. Applicable to basic authentication method||
|timezone.id|Timezone identifier|GMT|
|result.attributes.excluded|Comma-separated names of sample result attributes that shall not be passed to Elasticsearch server. Valid attributes' names: Timestamp, StartTime, EndTime, Time, Latency, ConnectTime, IdleTime, SampleLabel, GroupName, ThreadName, ResponseCode, IsResponseCodeOk, IsSuccessful, SampleCount, ErrorCount, ContentType, MediaType, DataType, RequestHeaders, ResponseHeaders, HeadersSize, SamplerData, ResponseMessage, ResponseData, BodySize, Bytes|All attributes are passed|
|proxy.url|In case connection to Elasticsearch server is over proxy server, proxy URL in format &lt;scheme&gt;://&lt;host&gt;:&lt;port&gt;|Empty (no proxy)|
|proxy.authenticationMethod|In case authentication is required at proxy server, authentication method to be used. If no authentication is required, the parameter value shall be left empty. Currently supported authentication methods: BASIC, NTLM|Empty (no authentication)|
|proxy.user|User name for authentication of requests sent over proxy server. Applicable to basic and NTLM authentication methods||
|proxy.password|Password for authentication of requests sent over proxy server. Applicable to basic and NTLM authentication methods||
|proxy.workstation|Workstation name for authentication of requests sent over proxy server. Applicable to NTLM authentication method. If no value is provided, local host name of the machine will be determined and used||
|proxy.domain|Domain name for authentication of requests sent over proxy server. Applicable to NTLM authentication method||
|experimental.connection.trustAllSslCertificates|Enable trust all SSL certificates when establishing connection to Elasticsearch server. Possible values: true / false. Not recommended for production usage due to security considerations|false|


## Additional notes
The listener plugin has been tested with JMeter version 3.3 running on JRE versions 1.8 and 1.9 in conjunction with Elasticsearch server version 5.6.3.

In recent versions of JMeter API, several methods related to sample result processing, have been deprecated and newer replacing methods were introduced. The current version of the listener plugin makes use of the latter methods.

Recent versions of JMeter API deprecate usage of Jorphan Logging Manager, which is superseded with Simple Logging Facade for Java (SLF4J). The current version of the listener plugin makes use of SLF4J for logging.
