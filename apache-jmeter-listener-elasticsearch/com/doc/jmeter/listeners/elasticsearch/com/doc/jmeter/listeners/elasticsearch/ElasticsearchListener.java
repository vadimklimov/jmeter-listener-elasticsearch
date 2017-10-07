package com.doc.jmeter.listeners.elasticsearch.com.doc.jmeter.listeners.elasticsearch;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minidev.json.JSONObject;

public class ElasticsearchListener extends AbstractBackendListenerClient {

	private static final String PARAMETER_NAME_ELASTICSEARCH_PROTOCOL = "elasticsearch.protocol";
	private static final String PARAMETER_NAME_ELASTICSEARCH_HOST = "elasticsearch.host";
	private static final String PARAMETER_NAME_ELASTICSEARCH_PORT = "elasticsearch.port";
	private static final String PARAMETER_NAME_ELASTICSEARCH_USER = "elasticsearch.user";
	private static final String PARAMETER_NAME_ELASTICSEARCH_PASSWORD = "elasticsearch.password";
	private static final String PARAMETER_NAME_ELASTICSEARCH_INDEX = "elasticsearch.index";
	private static final String PARAMETER_NAME_ELASTICSEARCH_TYPE = "elasticsearch.type";
	private static final String PARAMETER_NAME_TIMEZONE_ID = "timezone.id";
	private static final String PARAMETER_NAME_RESULT_EXCLUDED_ATTRIBUTES = "result.attributes.excluded";
	private static final String PARAMETER_NAME_ELASTICSEARCH_CONN_PROXY_URL = "elasticsearch.connection.proxy.url";
	private static final String PARAMETER_NAME_ELASTICSEARCH_CONN_PROXY_USER = "elasticsearch.connection.proxy.user";
	private static final String PARAMETER_NAME_ELASTICSEARCH_CONN_PROXY_PASSWORD = "elasticsearch.connection.proxy.password";
	private static final String PARAMETER_NAME_ELASTICSEARCH_CONN_SSL_TRUST_ALL_CERTS = "elasticsearch.connection.trustAllSslCertificates";

	private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchListener.class);

	private static final Object LOCK = new Object();

	private static final List<String> SAMPLE_RESULT_ALL_ATTRIBUTES = Arrays.asList("Timestamp", "StartTime", "EndTime",
			"Time", "Latency", "ConnectTime", "IdleTime", "SampleLabel", "GroupName", "ThreadName", "ResponseCode",
			"IsResponseCodeOk", "IsSuccessful", "SampleCount", "ErrorCount", "ContentType", "MediaType", "DataType",
			"RequestHeaders", "ResponseHeaders", "HeadersSize", "SamplerData", "ResponseMessage", "ResponseData",
			"BodySize", "Bytes");

	private String esProtocol;
	private String esHost;
	private int esPort;
	private String esUser;
	private String esPassword;
	private String esIndex;
	private String esType;
	private String tzId;
	private String srExcludedAttributes;
	private String esConnProxyUrl;
	String esConnProxyProtocol;
	String esConnProxyHost;
	int esConnProxyPort;
	private String esConnProxyUser;
	private String esConnProxyPassword;
	private boolean esConnSslTrustAllCerts;

	private RestClient esClient = null;;
	private boolean isEsPingOk = false;
	private List<String> resultExcludedAttributes = new ArrayList<String>();

	/*
	 * @Override public SampleResult createSampleResult(BackendListenerContext
	 * context, SampleResult sampleResult) {
	 * 
	 * // Not used in current implementation return null;
	 * 
	 * }
	 */

	@Override
	public Arguments getDefaultParameters() {
		LOGGER.debug("Retrieving Elasticsearch listener parameterization");

		Arguments parameters = new Arguments();
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_PROTOCOL, "http");
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_HOST, null);
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_PORT, "9200");
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_USER, null);
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_PASSWORD, null);
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_INDEX, null);
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_TYPE, null);
		parameters.addArgument(PARAMETER_NAME_TIMEZONE_ID, "GMT");
		parameters.addArgument(PARAMETER_NAME_RESULT_EXCLUDED_ATTRIBUTES, null);
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_CONN_PROXY_URL, null);
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_CONN_PROXY_USER, null);
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_CONN_PROXY_PASSWORD, null);
		parameters.addArgument(PARAMETER_NAME_ELASTICSEARCH_CONN_SSL_TRUST_ALL_CERTS, "false");

		return parameters;

	}

	@Override
	public void handleSampleResults(List<SampleResult> sampleResults, BackendListenerContext context) {
		LOGGER.debug("Processing sample results for request to Elasticsearch server");

		if (!isEsPingOk) {
			LOGGER.debug("Sample result will not be sent to Elasticsearch server because server ping failed");
			return;
		}

		synchronized (LOCK) {
			for (SampleResult sampleResult : sampleResults) {
				LOGGER.debug("Preparing sample result to send to Elasticsearch server");
				String sampleResult4External = getSampleResult4External(sampleResult);

				if (sampleResult4External == null || sampleResult4External.isEmpty()) {
					LOGGER.debug(
							"Sample result will not be sent to Elasticsearch server because message content is missing");
					return;
				}
				HttpEntity sampleResultEntity = new StringEntity(sampleResult4External, ContentType.APPLICATION_JSON);

				String sampleResultEsDocumentLocation = "/" + esIndex + "/" + esType + "/" + UUID	.randomUUID()
																									.toString();

				LOGGER.debug("Elasticsearch request - document location: " + sampleResultEsDocumentLocation);
				LOGGER.debug("Elasticsearch request - document data:\n" + sampleResult4External);

				try {
					Response sampleResultEsDocumentResponse = esClient.performRequest("POST",
							sampleResultEsDocumentLocation, Collections.<String, String>emptyMap(), sampleResultEntity);

					LOGGER.debug("Elasticsearch response - HTTP status code: "
							+ sampleResultEsDocumentResponse.getStatusLine()
															.getStatusCode());
					LOGGER.debug("Elasticsearch response - HTTP body:\n"
							+ EntityUtils.toString(sampleResultEsDocumentResponse.getEntity()));
				} catch (IOException e) {
					LOGGER.error("Error when sending sample result to Elasticsearch");
					LOGGER.error(ExceptionUtils.getStackTrace(e));
				}

			}
		}

	}

	@Override
	public void setupTest(BackendListenerContext context) throws Exception {
		LOGGER.debug("Setting up Elasticsearch listener");

		esProtocol = context.getParameter(PARAMETER_NAME_ELASTICSEARCH_PROTOCOL)
							.trim();
		esHost = context.getParameter(PARAMETER_NAME_ELASTICSEARCH_HOST)
						.trim();
		esPort = Integer.valueOf(context.getParameter(PARAMETER_NAME_ELASTICSEARCH_PORT)
										.trim());
		esUser = context.getParameter(PARAMETER_NAME_ELASTICSEARCH_USER)
						.trim();
		esPassword = context.getParameter(PARAMETER_NAME_ELASTICSEARCH_PASSWORD)
							.trim();
		esIndex = context	.getParameter(PARAMETER_NAME_ELASTICSEARCH_INDEX)
							.trim()
							.toLowerCase();
		esType = context.getParameter(PARAMETER_NAME_ELASTICSEARCH_TYPE)
						.trim()
						.toLowerCase();
		tzId = context	.getParameter(PARAMETER_NAME_TIMEZONE_ID)
						.trim();
		srExcludedAttributes = context	.getParameter(PARAMETER_NAME_RESULT_EXCLUDED_ATTRIBUTES)
										.trim();
		esConnProxyUrl = context.getParameter(PARAMETER_NAME_ELASTICSEARCH_CONN_PROXY_URL)
								.trim();
		esConnProxyUser = context	.getParameter(PARAMETER_NAME_ELASTICSEARCH_CONN_PROXY_USER)
									.trim();
		esConnProxyPassword = context	.getParameter(PARAMETER_NAME_ELASTICSEARCH_CONN_PROXY_PASSWORD)
										.trim();
		esConnSslTrustAllCerts = Boolean.valueOf(
				context	.getParameter(PARAMETER_NAME_ELASTICSEARCH_CONN_SSL_TRUST_ALL_CERTS)
						.trim());

		if (srExcludedAttributes != null && srExcludedAttributes.length() > 0) {
			resultExcludedAttributes.addAll(Arrays.asList(srExcludedAttributes.split(",")));
			resultExcludedAttributes.retainAll(SAMPLE_RESULT_ALL_ATTRIBUTES);
			LOGGER.debug("Possible sample result attributes: " + SAMPLE_RESULT_ALL_ATTRIBUTES);
			LOGGER.debug("Excluded sample result attributes: " + resultExcludedAttributes);
		}

		RestClientBuilder esClientBuilder = RestClient.builder(new HttpHost(esHost, esPort, esProtocol));

		if (esConnSslTrustAllCerts) {
			connTrustAllSslCerts();
		}

		HttpClientConfigCallback esHttpClientConfig = new RestClientBuilder.HttpClientConfigCallback() {
			@Override
			public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {

				CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

				if (esUser != null && esUser.length() > 0 && esPassword != null && esPassword.length() > 0) {
					LOGGER.debug("Using basic authentication when sending requests to Elasticsearch server");
					credentialsProvider.setCredentials(new AuthScope(esHost, esPort),
							new UsernamePasswordCredentials(esUser, esPassword));
				}

				if (esConnProxyUrl != null && esConnProxyUrl.length() > 0) {
					LOGGER.debug("Using proxy when sending requests to Elasticsearch server");

					try {
						URL esConnProxy = new URL(esConnProxyUrl);
						esConnProxyProtocol = esConnProxy.getProtocol();
						esConnProxyHost = esConnProxy.getHost();
						esConnProxyPort = esConnProxy.getPort();

						httpClientBuilder.setProxy(new HttpHost(esConnProxyHost, esConnProxyPort, esConnProxyProtocol));

						if (esConnProxyUser != null && esConnProxyUser.length() > 0 && esConnProxyPassword != null
								&& esConnProxyPassword.length() > 0) {
							credentialsProvider.setCredentials(new AuthScope(esConnProxyHost, esConnProxyPort),
									new UsernamePasswordCredentials(esConnProxyUser, esConnProxyPassword));
						}

						httpClientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());

					} catch (MalformedURLException e) {
						LOGGER.error("Error when parsing proxy URL - disabling proxy usage");
						LOGGER.error(ExceptionUtils.getStackTrace(e));
					}
				}

				httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);

				return httpClientBuilder;
			}
		};

		esClientBuilder.setHttpClientConfigCallback(esHttpClientConfig);

		esClient = esClientBuilder.build();

		try {
			esClient.performRequest("HEAD", "/", Collections.<String, String>emptyMap());
			isEsPingOk = true;
			LOGGER.info("Elasticsearch ping test: Successful");
		} catch (IOException e) {
			isEsPingOk = false;
			LOGGER.error("Elasticsearch ping test: Failed");
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}

	}

	@Override
	public void teardownTest(BackendListenerContext context) throws Exception {
		LOGGER.debug("Shutting down Elasticsearch listener");

		try {
			esClient.close();
		} catch (IOException e) {
			LOGGER.error("Error when closing connection to Elasticsearch");
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}

	}

	private String getSampleResult4External(SampleResult sampleResult) {
		LOGGER.debug("Preparing sample result message");

		JSONObject sampleResult4ExternalJson = new JSONObject();

		for (String attributeName : SAMPLE_RESULT_ALL_ATTRIBUTES) {
			if (!resultExcludedAttributes.contains(attributeName)) {
				switch (attributeName) {
				case "Timestamp":
					sampleResult4ExternalJson.put(attributeName,
							LocalDateTime	.ofInstant(Instant.ofEpochMilli(sampleResult.getTimeStamp()), ZoneId.of(tzId))
											.toString());
					break;
				case "StartTime":
					sampleResult4ExternalJson.put(attributeName,
							LocalDateTime	.ofInstant(Instant.ofEpochMilli(sampleResult.getStartTime()), ZoneId.of(tzId))
											.toString());
					break;
				case "EndTime":
					sampleResult4ExternalJson.put(attributeName,
							LocalDateTime	.ofInstant(Instant.ofEpochMilli(sampleResult.getEndTime()), ZoneId.of(tzId))
											.toString());
					break;
				case "Time":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getTime());
					break;
				case "Latency":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getLatency());
					break;
				case "ConnectTime":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getConnectTime());
					break;
				case "IdleTime":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getIdleTime());
					break;
				case "SampleLabel":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getSampleLabel());
					break;
				case "GroupName":
					sampleResult4ExternalJson.put(attributeName, sampleResult	.getSampleLabel(true)
																				.substring(0,
																						sampleResult.getSampleLabel(
																								true)
																									.indexOf(
																											sampleResult.getSampleLabel())
																								- 1));
					break;
				case "ThreadName":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getThreadName());
					break;
				case "ResponseCode":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getResponseCode());
					break;
				case "IsResponseCodeOk":
					sampleResult4ExternalJson.put(attributeName, sampleResult.isResponseCodeOK());
					break;
				case "IsSuccessful":
					sampleResult4ExternalJson.put(attributeName, sampleResult.isSuccessful());
					break;
				case "SampleCount":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getSampleCount());
					break;
				case "ErrorCount":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getErrorCount());
					break;
				case "ContentType":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getContentType());
					break;
				case "MediaType":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getMediaType());
					break;
				case "DataType":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getDataType());
					break;
				case "RequestHeaders":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getRequestHeaders());
					break;
				case "ResponseHeaders":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getResponseHeaders());
					break;
				case "HeadersSize":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getHeadersSize());
					break;
				case "SamplerData":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getSamplerData());
					break;
				case "ResponseMessage":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getResponseMessage());
					break;
				case "ResponseData":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getResponseDataAsString());
					break;
				case "BodySize":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getBodySizeAsLong());
					break;
				case "Bytes":
					sampleResult4ExternalJson.put(attributeName, sampleResult.getBytesAsLong());
					break;
				}
			}
		}

		if (sampleResult4ExternalJson != null && !sampleResult4ExternalJson.isEmpty()) {
			return sampleResult4ExternalJson.toString();
		} else {
			return null;
		}

	}

	private void connTrustAllSslCerts() {

		LOGGER.debug("Enabling trust all SSL certificates when establishing connection to Elasticsearch server");
		TrustManager[] trustCertificates = new TrustManager[] { new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			@Override
			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		} };

		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustCertificates, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			LOGGER.error("Elasticsearch connection - trust all SSL certificates: Failed");
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}

	}

}