package com.doc.jmeter.listeners.elasticsearch;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
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

import com.doc.jmeter.listeners.elasticsearch.util.ElasticsearchListenerParameters;
import com.doc.jmeter.listeners.elasticsearch.util.ParameterValueChecker;

import net.minidev.json.JSONObject;

public class ElasticsearchListener extends AbstractBackendListenerClient {

	private enum SampleResultDefaultAttributes {
		Timestamp, StartTime, EndTime, Time, Latency, ConnectTime, IdleTime, SampleLabel, GroupName, ThreadName, ResponseCode, IsResponseCodeOk, IsSuccessful, SampleCount, ErrorCount, ContentType, MediaType, DataType, RequestHeaders, ResponseHeaders, HeadersSize, SamplerData, ResponseMessage, ResponseData, BodySize, Bytes, RunGUID
	};

	private enum EsSupportedAuthMethods {
		BASIC
	};

	private enum ProxySupportedAuthMethods {
		BASIC, NTLM
	};

	private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchListener.class);
	private static final Object LOCK = new Object();

	private String esUrl;
	private String esProtocol;
	private String esHost;
	private int esPort;
	private String esIndex;
	private String esType;
	private boolean isEsAuth = false;
	private String esAuthMethod;
	private String esUser;
	private String esPassword;

	private String tzId;
	private String srExcludedAttributes;
	private List<String> sampleResultExcludedAttributes = new ArrayList<String>();

	private boolean isProxyUse = false;
	private String proxyUrl;
	private String proxyProtocol;
	private String proxyHost;
	private int proxyPort;
	private boolean isProxyAuth = false;
	private String proxyAuthMethod;
	private String proxyUser;
	private String proxyPassword;
	private String proxyWorkstation;
	private String proxyDomain;

	private String xConnTrustAllSslCerts;

	private boolean isConnTrustAllSslCerts = false;

	private RestClient esClient = null;

	private boolean isError = false;

	private String jmRunGUID = (System.getenv("RUN_ID") != null)
			? System.getenv("RUN_ID"): UUID.randomUUID().toString();

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

		Arguments parameters = new Arguments();

		parameters.addArgument(ElasticsearchListenerParameters.ELASTICSEARCH_URL, "http://localhost:9200");
		parameters.addArgument(ElasticsearchListenerParameters.ELASTICSEARCH_INDEX, null);
		parameters.addArgument(ElasticsearchListenerParameters.ELASTICSEARCH_TYPE, null);
		parameters.addArgument(ElasticsearchListenerParameters.ELASTICSEARCH_AUTH_METHOD, null);
		parameters.addArgument(ElasticsearchListenerParameters.ELASTICSEARCH_USER, null);
		parameters.addArgument(ElasticsearchListenerParameters.ELASTICSEARCH_PASSWORD, null);
		parameters.addArgument(ElasticsearchListenerParameters.TIMEZONE_ID, "GMT");
		parameters.addArgument(ElasticsearchListenerParameters.RESULT_EXCLUDED_ATTRIBUTES, null);
		parameters.addArgument(ElasticsearchListenerParameters.PROXY_URL, null);
		parameters.addArgument(ElasticsearchListenerParameters.PROXY_AUTH_METHOD, null);
		parameters.addArgument(ElasticsearchListenerParameters.PROXY_USER, null);
		parameters.addArgument(ElasticsearchListenerParameters.PROXY_PASSWORD, null);
		parameters.addArgument(ElasticsearchListenerParameters.PROXY_WORKSTATION, null);
		parameters.addArgument(ElasticsearchListenerParameters.PROXY_DOMAIN, null);
		parameters.addArgument(ElasticsearchListenerParameters.X_CONN_TRUST_ALL_CERTS, "false");

		return parameters;

	}

	@Override
	public void handleSampleResults(List<SampleResult> sampleResults, BackendListenerContext context) {

		if (isError) {
			LOGGER.warn("Sample result will not be sent to Elasticsearch server due to server ping failure");
			return;
		}

		LOGGER.debug("Processing sample results for request to Elasticsearch server");

		synchronized (LOCK) {

			sampleResults.forEach(sampleResult -> {

				LOGGER.debug("Preparing sample result to send to Elasticsearch server");
				String sampleResult4External = getSampleResult4External(sampleResult);

				if (sampleResult4External == null || sampleResult4External.isEmpty()) {
					LOGGER.warn(
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

					LOGGER.debug("Elasticsearch server response - HTTP status code: "
							+ sampleResultEsDocumentResponse.getStatusLine()
															.getStatusCode());
					LOGGER.debug("Elasticsearch server response - HTTP body:\n"
							+ EntityUtils.toString(sampleResultEsDocumentResponse.getEntity()));
				} catch (IOException e) {
					LOGGER.error("Sending sample result to Elasticsearch server failed");
					LOGGER.error(ExceptionUtils.getStackTrace(e));
				}

			});
		}

	}

	@Override
	public void setupTest(BackendListenerContext context) throws Exception {

		LOGGER.debug("Initializing Elasticsearch listener");

		getListenerParameters(context);
		checkListenerParameters();

		if (isError) {
			LOGGER.error(
					"One or several checks of Elasticsearch listener parameters failed. Terminating Elasticsearch listener");
			return;
		}

		esClient = getElasticsearchRestClient();

		if (isError) {
			LOGGER.error("Elasticsearch REST client initialization failed. Terminating Elasticsearch listener");
			return;
		}

		try {
			esClient.performRequest("HEAD", "/", Collections.<String, String>emptyMap());
			isError = false;
			LOGGER.info("Elasticsearch server ping test: Successful");
		} catch (IOException e) {
			isError = true;
			LOGGER.error("Elasticsearch server ping test: Failed");
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}

	}

	@Override
	public void teardownTest(BackendListenerContext context) throws Exception {

		LOGGER.debug("Shutting down Elasticsearch listener");

		if (esClient != null) {
			try {
				esClient.close();
			} catch (IOException e) {
				LOGGER.error("Connection closure to Elasticsearch server failed");
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}
		}

	}

	private void getListenerParameters(BackendListenerContext context) {

		LOGGER.debug("Retrieving Elasticsearch listener parameters");

		esUrl = context	.getParameter(ElasticsearchListenerParameters.ELASTICSEARCH_URL)
						.trim();
		esIndex = context	.getParameter(ElasticsearchListenerParameters.ELASTICSEARCH_INDEX)
							.trim()
							.toLowerCase();
		esType = context.getParameter(ElasticsearchListenerParameters.ELASTICSEARCH_TYPE)
						.trim();
		esAuthMethod = context	.getParameter(ElasticsearchListenerParameters.ELASTICSEARCH_AUTH_METHOD)
								.trim()
								.toUpperCase();
		esUser = context.getParameter(ElasticsearchListenerParameters.ELASTICSEARCH_USER)
						.trim();
		esPassword = context.getParameter(ElasticsearchListenerParameters.ELASTICSEARCH_PASSWORD)
							.trim();

		tzId = context	.getParameter(ElasticsearchListenerParameters.TIMEZONE_ID)
						.trim()
						.toUpperCase();
		srExcludedAttributes = context	.getParameter(ElasticsearchListenerParameters.RESULT_EXCLUDED_ATTRIBUTES)
										.trim();
		proxyUrl = context	.getParameter(ElasticsearchListenerParameters.PROXY_URL)
							.trim();
		proxyAuthMethod = context	.getParameter(ElasticsearchListenerParameters.PROXY_AUTH_METHOD)
									.trim()
									.toUpperCase();
		proxyUser = context	.getParameter(ElasticsearchListenerParameters.PROXY_USER)
							.trim();
		proxyPassword = context	.getParameter(ElasticsearchListenerParameters.PROXY_PASSWORD)
								.trim();
		proxyWorkstation = context	.getParameter(ElasticsearchListenerParameters.PROXY_WORKSTATION)
									.trim();
		proxyDomain = context	.getParameter(ElasticsearchListenerParameters.PROXY_DOMAIN)
								.trim();
		xConnTrustAllSslCerts = context	.getParameter(ElasticsearchListenerParameters.X_CONN_TRUST_ALL_CERTS)
										.trim()
										.toLowerCase();

	}

	private void checkListenerParameters() {

		LOGGER.debug("Checking Elasticsearch listener parameters");

		// Get list of excluded attributes of sample result
		if (!ParameterValueChecker.isNullOrEmpty(srExcludedAttributes)) {
			List<String> sampleResultDefaultAttributes = Stream	.of(SampleResultDefaultAttributes.values())
																.map(SampleResultDefaultAttributes::name)
																.collect(Collectors.toList());
			sampleResultExcludedAttributes.addAll(Arrays.asList(srExcludedAttributes.split(",")));
			sampleResultExcludedAttributes.retainAll(sampleResultDefaultAttributes);
			LOGGER.debug("Possible sample result attributes: " + sampleResultDefaultAttributes);
			LOGGER.debug("Excluded sample result attributes: " + sampleResultExcludedAttributes);
		}

		// Check Elasticsearch server URL
		if (!ParameterValueChecker.isNullOrEmpty(esUrl)) {
			try {
				URL esURL = new URL(esUrl);
				esProtocol = esURL.getProtocol();
				esHost = esURL.getHost();
				esPort = esURL.getPort();
			} catch (MalformedURLException e) {
				isError = true;
				LOGGER.error("Parsing Elasticsearch server URL failed");
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}
		} else {
			isError = true;
			LOGGER.error("Mandatory parameter missing. Check parameter: "
					+ ElasticsearchListenerParameters.ELASTICSEARCH_URL);
		}

		// Check Elasticsearch index and type
		if (ParameterValueChecker.isNullOrEmpty(esIndex)) {
			isError = true;
			LOGGER.error("Mandatory parameter missing. Check parameter: "
					+ ElasticsearchListenerParameters.ELASTICSEARCH_INDEX);
		}

		if (ParameterValueChecker.isNullOrEmpty(esType)) {
			isError = true;
			LOGGER.error("Mandatory parameter missing. Check parameter: "
					+ ElasticsearchListenerParameters.ELASTICSEARCH_TYPE);
		}

		// Check Elasticsearch authentication method
		if (!ParameterValueChecker.isNullOrEmpty(esAuthMethod)) {
			isEsAuth = true;

			try {
				switch (EsSupportedAuthMethods.valueOf(esAuthMethod)) {

				case BASIC:
					if (ParameterValueChecker.isNullOrEmpty(esUser)
							|| ParameterValueChecker.isNullOrEmpty(esPassword)) {
						isError = true;
						LOGGER.error(
								"Elasticsearch basic authentication method selected. One or several mandatory parameters missing. Check parameters: "
										+ ElasticsearchListenerParameters.ELASTICSEARCH_USER + ", "
										+ ElasticsearchListenerParameters.ELASTICSEARCH_PASSWORD);
					}

					break;

				}
			} catch (IllegalArgumentException e) {
				isError = true;
				LOGGER.error("Unsupported Elasticsearch authentication method. Check parameter: "
						+ ElasticsearchListenerParameters.ELASTICSEARCH_AUTH_METHOD);
			}

		}

		// Check proxy URL
		if (!ParameterValueChecker.isNullOrEmpty(proxyUrl)) {
			isProxyUse = true;

			try {
				URL proxyURL = new URL(proxyUrl);
				proxyProtocol = proxyURL.getProtocol();
				proxyHost = proxyURL.getHost();
				proxyPort = proxyURL.getPort();
			} catch (MalformedURLException e) {
				isError = true;
				LOGGER.error("Parsing proxy URL failed");
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}
		}

		// Check proxy authentication method
		if (isProxyUse && !ParameterValueChecker.isNullOrEmpty(proxyAuthMethod)) {
			isProxyAuth = true;

			try {
				switch (ProxySupportedAuthMethods.valueOf(proxyAuthMethod)) {

				case BASIC:
					if (ParameterValueChecker.isNullOrEmpty(proxyUser)
							|| ParameterValueChecker.isNullOrEmpty(proxyPassword)) {
						isError = true;
						LOGGER.error(
								"Proxy basic authentication method selected. One or several mandatory parameters missing. Check parameters: "
										+ ElasticsearchListenerParameters.PROXY_USER + ", "
										+ ElasticsearchListenerParameters.PROXY_PASSWORD);
					}

					break;

				case NTLM:
					if (ParameterValueChecker.isNullOrEmpty(proxyUser)
							|| ParameterValueChecker.isNullOrEmpty(proxyPassword)
							|| ParameterValueChecker.isNullOrEmpty(proxyDomain)) {
						isError = true;
						LOGGER.error(
								"Proxy NTLM authentication method selected. One or several mandatory parameters missing. Check parameters: "
										+ ElasticsearchListenerParameters.PROXY_USER + ", "
										+ ElasticsearchListenerParameters.PROXY_PASSWORD + ", "
										+ ElasticsearchListenerParameters.PROXY_DOMAIN);
					} else {
						if (ParameterValueChecker.isNullOrEmpty(proxyWorkstation)) {
							LOGGER.debug(
									"Proxy NTLM authentication method selected. Workstation not specified. Attempting to determine local host name");

							try {
								proxyWorkstation = InetAddress	.getLocalHost()
																.getHostName();
								LOGGER.info("Local host name: " + proxyWorkstation);
							} catch (UnknownHostException e) {
								isError = true;
								LOGGER.error("Determination of local host name failed");
								LOGGER.error(ExceptionUtils.getStackTrace(e));
							}
						}
					}

					break;

				}
			} catch (IllegalArgumentException e) {
				isError = true;
				LOGGER.error("Unsupported proxy authentication method. Check parameter: "
						+ ElasticsearchListenerParameters.PROXY_AUTH_METHOD);
			}

		}

		// Check timezone ID
		if (!ZoneId	.getAvailableZoneIds()
					.contains(tzId)) {
			LOGGER.warn("Incorrect timezone ID. Falling back to timezone ID 'GMT'. Check parameter: "
					+ ElasticsearchListenerParameters.TIMEZONE_ID);
			tzId = "GMT";
		}

		// Check trust all SSL certificates
		isConnTrustAllSslCerts = ParameterValueChecker.convertBoolean(
				ParameterValueChecker.isTrueOrFalse(xConnTrustAllSslCerts), false);

	}

	private RestClient getElasticsearchRestClient() {

		if (isError) {
			LOGGER.warn(
					"Elasticsearch REST client will not be initialized due to failure during checks of Elasticsearch listener parameters. Terminating Elasticsearch listener");
			return null;
		}

		LOGGER.debug("Initializing Elasticsearch REST client");

		RestClientBuilder clientBuilder = RestClient.builder(new HttpHost(esHost, esPort, esProtocol));

		HttpClientConfigCallback httpClientConfig = new RestClientBuilder.HttpClientConfigCallback() {

			@Override
			public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {

				if (isEsAuth || isProxyAuth) {
					Lookup<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder	.<AuthSchemeProvider>create()
																					.register(AuthSchemes.BASIC,
																							new BasicSchemeFactory())
																					.register(AuthSchemes.DIGEST,
																							new DigestSchemeFactory())
																					.register(AuthSchemes.NTLM,
																							new NTLMSchemeFactory())
																					.register(AuthSchemes.SPNEGO,
																							new SPNegoSchemeFactory())
																					.register(AuthSchemes.KERBEROS,
																							new KerberosSchemeFactory())
																					.build();

					httpClientBuilder.setDefaultAuthSchemeRegistry(authSchemeRegistry);

					CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

					// Elasticsearch authentication
					if (isEsAuth) {
						switch (EsSupportedAuthMethods.valueOf(esAuthMethod)) {

						case BASIC:
							credentialsProvider.setCredentials(new AuthScope(esHost, esPort),
									new UsernamePasswordCredentials(esUser, esPassword));
							break;

						}
					}

					// Proxy authentication
					if (isProxyUse && isProxyAuth) {
						switch (ProxySupportedAuthMethods.valueOf(proxyAuthMethod)) {

						case BASIC:
							credentialsProvider.setCredentials(new AuthScope(proxyHost, proxyPort),
									new UsernamePasswordCredentials(proxyUser, proxyPassword));
							break;

						case NTLM:
							credentialsProvider.setCredentials(new AuthScope(proxyHost, proxyPort),
									new NTCredentials(proxyUser, proxyPassword, proxyWorkstation, proxyDomain));
							break;

						}
					}

					httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);

				}

				// Use proxy
				if (isProxyUse) {
					httpClientBuilder.setProxy(new HttpHost(proxyHost, proxyPort, proxyProtocol));
				}

				// Trust all SSL certificates (experimental feature)
				if (isConnTrustAllSslCerts) {
					try {
						SSLContext sslContext = SSLContextBuilder	.create()
																	.loadTrustMaterial(new TrustSelfSignedStrategy())
																	.build();
						httpClientBuilder	.setSSLContext(sslContext)
											.setSSLHostnameVerifier(new NoopHostnameVerifier());
					} catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
						LOGGER.warn("Enablement of trust all SSL certificates failed");
						LOGGER.warn(ExceptionUtils.getStackTrace(e));
					}
				}

				return httpClientBuilder;
			}

		};

		clientBuilder.setHttpClientConfigCallback(httpClientConfig);

		esClient = clientBuilder.build();

		return esClient;

	}

	private String getSampleResult4External(SampleResult sampleResult) {

		LOGGER.debug("Preparing sample result message");

		JSONObject sampleResult4ExternalJson = new JSONObject();

		Stream	.of(SampleResultDefaultAttributes.values())
				.forEach(attribute -> {

					if (!sampleResultExcludedAttributes.contains(attribute.toString())) {
						switch (attribute) {
						case Timestamp:
							sampleResult4ExternalJson.put(
									attribute.toString(), LocalDateTime
																		.ofInstant(
																				Instant.ofEpochMilli(
																						sampleResult.getTimeStamp()),
																				ZoneId.of(tzId))
																		.toString());
							break;
						case StartTime:
							sampleResult4ExternalJson.put(
									attribute.toString(), LocalDateTime
																		.ofInstant(
																				Instant.ofEpochMilli(
																						sampleResult.getStartTime()),
																				ZoneId.of(tzId))
																		.toString());
							break;
						case EndTime:
							sampleResult4ExternalJson.put(
									attribute.toString(), LocalDateTime
																		.ofInstant(
																				Instant.ofEpochMilli(
																						sampleResult.getEndTime()),
																				ZoneId.of(tzId))
																		.toString());
							break;
						case Time:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getTime());
							break;
						case Latency:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getLatency());
							break;
						case ConnectTime:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getConnectTime());
							break;
						case IdleTime:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getIdleTime());
							break;
						case SampleLabel:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getSampleLabel());
							break;
						case GroupName:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getSampleLabel(true)
																							.substring(0,
																									sampleResult.getSampleLabel(
																											true)
																												.indexOf(
																														sampleResult.getSampleLabel())
																											- 1));
							break;
						case ThreadName:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getThreadName());
							break;
						case ResponseCode:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getResponseCode());
							break;
						case IsResponseCodeOk:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.isResponseCodeOK());
							break;
						case IsSuccessful:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.isSuccessful());
							break;
						case SampleCount:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getSampleCount());
							break;
						case ErrorCount:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getErrorCount());
							break;
						case ContentType:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getContentType());
							break;
						case MediaType:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getMediaType());
							break;
						case DataType:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getDataType());
							break;
						case RequestHeaders:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getRequestHeaders());
							break;
						case ResponseHeaders:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getResponseHeaders());
							break;
						case HeadersSize:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getHeadersSize());
							break;
						case SamplerData:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getSamplerData());
							break;
						case ResponseMessage:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getResponseMessage());
							break;
						case ResponseData:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getResponseDataAsString());
							break;
						case BodySize:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getBodySizeAsLong());
							break;
						case Bytes:
							sampleResult4ExternalJson.put(attribute.toString(), sampleResult.getBytesAsLong());
							break;
						case RunGUID:
							sampleResult4ExternalJson.put(attribute.toString(), this.jmRunGUID.toString());
							break;
						}
					}

				});

		if (sampleResult4ExternalJson != null && !sampleResult4ExternalJson.isEmpty()) {
			return sampleResult4ExternalJson.toString();
		} else {
			return null;
		}

	}

}