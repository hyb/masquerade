package masquerade.sim.model;


/**
 * A request ID reader is able to extract a uniqe ID from a request. 
 * Dynamic request expectations will be matched against the request using
 * the unique ID.  
 */
public interface RequestIdProvider<R> {
	Class<R> getAcceptedRequestType();
	String getUniqueId(R request, RequestContext context);
}
