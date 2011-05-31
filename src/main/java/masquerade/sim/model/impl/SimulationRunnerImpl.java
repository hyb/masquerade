package masquerade.sim.model.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import masquerade.sim.history.HistoryEntry;
import masquerade.sim.history.RequestHistory;
import masquerade.sim.history.RequestHistoryFactory;
import masquerade.sim.model.Converter;
import masquerade.sim.model.FileLoader;
import masquerade.sim.model.NamespaceResolver;
import masquerade.sim.model.RequestContext;
import masquerade.sim.model.RequestIdProvider;
import masquerade.sim.model.RequestMapping;
import masquerade.sim.model.Script;
import masquerade.sim.model.SimulationContext;
import masquerade.sim.model.SimulationRunner;
import masquerade.sim.model.VariableHolder;
import masquerade.sim.status.StatusLog;
import masquerade.sim.status.StatusLogger;

import org.apache.commons.io.IOUtils;
import org.springframework.util.StopWatch;

/**
 * Default implementation of {@link SimulationRunner}. Applies a {@link RequestMapping} to an incoming request,
 * logs the request, extracts a request ID and returns the response.
 */
public class SimulationRunnerImpl implements SimulationRunner {

	private static final StatusLog log = StatusLogger.get(SimulationRunnerImpl.class);
	
	private RequestHistoryFactory requestHistoryFactory;
	private Converter converter;
	private FileLoader fileLoader;
	private NamespaceResolver namespaceResolver;
	private VariableHolder configurationVariableHolder;

	/**
	 * @param requestHistoryFactory
	 * @param converter
	 * @param fileLoader
	 * @param namespaceResolver
	 */
	public SimulationRunnerImpl(RequestHistoryFactory requestHistoryFactory, Converter converter, FileLoader fileLoader, NamespaceResolver namespaceResolver,
			VariableHolder configurationVariableHolder) {
		this.requestHistoryFactory = requestHistoryFactory;
		this.converter = converter;
		this.fileLoader = fileLoader;
		this.namespaceResolver = namespaceResolver;
		this.configurationVariableHolder = configurationVariableHolder;
	}

	@Override
	public void runSimulation(OutputStream responseOutput, String channelName, String clientInfo, Collection<RequestMapping<?>> requestMappings, Object request, Date requestTimestamp) throws Exception {
		Date receiveTimestamp = new Date();
		RequestContext requestContext = new RequestContextImpl(namespaceResolver, converter);
		RequestHistory requestHistory = requestHistoryFactory.startRequestHistorySession();
		
		StopWatch watch = new StopWatch("SimulationRunner");
		watch.start("Match request");
		try {
			for (RequestMapping<?> mapping : requestMappings) {
				if (matches(request, requestContext, mapping)) {
					Script script = mapping.getScript();
					watch.stop();
					
					watch.start("Create context");
					Map<String, Object> initialContextVariables = configurationVariableHolder.getVariables();
					SimulationContext context = new SimulationContextImpl(request, initialContextVariables , converter, fileLoader, namespaceResolver);
					watch.stop();
					
					watch.start("Get request ID");
					String requestId = getRequestId(script.getRequestIdProvider(), request, requestContext);
					watch.stop();
					
					watch.start("Log request");
					HistoryEntry entry = logRequest(requestTimestamp, receiveTimestamp, channelName, clientInfo, request, requestHistory, script, requestId);
					watch.stop();
					
					watch.start("Run script");
					Object response = script.run(context);
					watch.stop();
					
					watch.start("Marshal response");
					marshalResponse(response, responseOutput);
					watch.stop();
					
					watch.start("Log response");
					logResponse(requestHistory, entry, response, receiveTimestamp);
					watch.stop();
					
					log.trace(watch.prettyPrint());
					
					return;
				}
			}
			
			// No match found
			requestHistory.logRequest(requestTimestamp, receiveTimestamp, channelName, "<no match>", clientInfo, null, converter.convert(request, String.class));
		} finally {
			requestHistory.endSession();
		}
	}

	private HistoryEntry logRequest(Date requestTimestamp, Date receiveTimestamp, String channelName, String clientInfo, Object request, RequestHistory requestHistory, Script script, String requestId) {
		HistoryEntry entry = 
			requestHistory.logRequest(requestTimestamp, receiveTimestamp, channelName, script.getName(), clientInfo, requestId, converter.convert(request, String.class));
		return entry;
	}

	private void logResponse(RequestHistory requestHistory, HistoryEntry entry, Object response, Date receiveTimestamp) {
		long processingPeriod = System.currentTimeMillis() - receiveTimestamp.getTime();
		String responseData = converter.convert(response, String.class);
		if (responseData != null) {
			requestHistory.addResponse(responseData, processingPeriod, entry);
		}
	}

	private boolean matches(Object request, RequestContext requestContext, RequestMapping<?> mapping) {
		try {
			return accepts(mapping, request.getClass()) && matches(mapping, request, requestContext);
		} catch (Exception e) {
			// Input might not be of the expected type for this mapping
			return false;
		}
	}

	private boolean accepts(RequestMapping<?> mapping, Class<? extends Object> requestType) {
		return converter.canConvert(requestType, mapping.acceptedRequestType());
	}

	private void marshalResponse(Object response, OutputStream responseOutput) throws IOException {
		if (response == null) {
			return;
		}
		
		InputStream input = converter.convert(response, InputStream.class);
		if (input == null) {
			byte[] ba = converter.convert(response, byte[].class);
			if (ba == null) {
				String str = converter.convert(response, String.class);
				if (str != null) {
					IOUtils.write(str, responseOutput);
				} else {
					log.error("Unable to convert response to InputStream, byte[] or String: " + response.getClass().getName());
				}
			} else {
				IOUtils.write(ba, responseOutput);
			}
		} else {
			IOUtils.copy(input, responseOutput);
		}
	}

	// Casts are safe because acceptedRequestType() returns the request type
	private boolean matches(RequestMapping<?> mapping, Object request, RequestContext requestContext) {
		@SuppressWarnings("rawtypes")
		RequestMapping cast = (RequestMapping) mapping;
		
		Object converted = converter.convert(request, mapping.acceptedRequestType());
		
		@SuppressWarnings("unchecked")
		boolean matches = cast.matches(converted, requestContext);
		return matches;
	}
 
	// Casts are safe because request is converted to RequestIdProvider#getAcceptedRequestType()
	private String getRequestId(RequestIdProvider<?> requestIdProvider, Object request, RequestContext context) {
		if (requestIdProvider == null) {
			return null;
		}
		
		@SuppressWarnings("rawtypes")
		RequestIdProvider cast = requestIdProvider;
		
		Object converted = converter.convert(request, requestIdProvider.getAcceptedRequestType());
		
		@SuppressWarnings("unchecked")
		String uniqueId = cast.getUniqueId(converted, context);
		return uniqueId;
	}
}
