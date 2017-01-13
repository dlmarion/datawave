package nsa.datawave.webservice.common.exception;

import nsa.datawave.Constants;
import nsa.datawave.resteasy.interceptor.DatawaveCorsFilter;
import nsa.datawave.webservice.query.exception.DatawaveErrorCode;
import nsa.datawave.webservice.query.exception.QueryException;
import nsa.datawave.webservice.result.VoidResponse;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.resteasy.spi.CorsHeaders;
import org.jboss.resteasy.spi.LoggableFailure;

import javax.ejb.EJBAccessException;
import javax.ejb.EJBException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;

@Provider
public class RESTExceptionMapper implements ExceptionMapper<Exception> {
    
    private Logger log = Logger.getLogger(this.getClass());
    
    private static String RESPONSE_ORIGIN = null;
    private static final Object lock = new Object();
    
    @Context
    private HttpHeaders headers;
    
    @Override
    public Response toResponse(Exception e) {
        
        Level level = Level.TRACE;
        if (e instanceof LoggableFailure) {
            level = Level.WARN;
        }
        log.log(level, "Turning this exception into a response", e);
        
        synchronized (lock) {
            if (null == RESPONSE_ORIGIN) {
                RESPONSE_ORIGIN = System.getProperty("cluster.name") + "/" + System.getProperty("jboss.host.name");
            }
        }
        
        String requestOrigin = "*";
        if ((headers != null) && (headers.getRequestHeader(CorsHeaders.ORIGIN) != null) && !(headers.getRequestHeader(CorsHeaders.ORIGIN).isEmpty())) {
            requestOrigin = headers.getRequestHeader(CorsHeaders.ORIGIN).get(0);
        }
        
        // EJB to EJB calls where the target EJB is transactional will cause any thrown
        // exceptions to be wrapped in an EJB exception. Unwrap those here if the cause
        // is one that we explicitly handle (WebApplicationException or QueryException).
        if ((e instanceof EJBException) && ((e.getCause() instanceof WebApplicationException) || (e.getCause() instanceof QueryException))) {
            e = (Exception) e.getCause();
        }
        
        if (e instanceof WebApplicationException) {
            WebApplicationException web = (WebApplicationException) e;
            Response r = web.getResponse();
            r.getMetadata().put(Constants.RESPONSE_ORIGIN, Collections.singletonList((Object) RESPONSE_ORIGIN));
            // Add CORS headers in case this is a CORS request that has thrown an exception. We have to add
            // this so that they can see the response body
            r.getMetadata().add(CorsHeaders.ACCESS_CONTROL_MAX_AGE, DatawaveCorsFilter.MAX_AGE);
            r.getMetadata().add(CorsHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, requestOrigin);
            r.getMetadata().add(CorsHeaders.ACCESS_CONTROL_ALLOW_HEADERS, DatawaveCorsFilter.ALLOWED_HEADERS);
            r.getMetadata().add(CorsHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, DatawaveCorsFilter.ALLOW_CREDENTIALS);
            if (e instanceof NoResultsException) {
                NoResultsException noResultsException = (NoResultsException) e;
                r.getMetadata().add(Constants.OPERATION_TIME, noResultsException.getEndTime() - noResultsException.getStartTime());
            }
            return r;
        } else {
            VoidResponse response = new VoidResponse();
            QueryException qe;
            if (e instanceof QueryException) {
                qe = (QueryException) e;
            } else {
                String message;
                Throwable cause = e.getCause();
                
                if ((null != cause) && (e.getClass() == java.lang.reflect.UndeclaredThrowableException.class || (e instanceof LoggableFailure))) {
                    // somewhere a method is throwing an exception that it didn't declare, or we have an internal RestEasy exception and we
                    // want the cause as our message.
                    message = cause.getMessage();
                } else {
                    message = e.getMessage();
                }
                
                if (null == message) {
                    message = "Exception has no message";
                }
                
                qe = new QueryException(message, e);
                if (null != cause) {
                    qe.setStackTrace(cause.getStackTrace());
                } else {
                    qe.setStackTrace(e.getStackTrace());
                }
                
                if (message.startsWith("java.lang.IllegalArgumentException: ")) {
                    qe.setErrorCode(Integer.toString(Response.Status.BAD_REQUEST.getStatusCode()) + "-1");
                } else if ((e.getClass() == javax.ejb.EJBAccessException.class) && (message.equals("Caller unauthorized"))) {
                    qe.setErrorCode(Integer.toString(Response.Status.FORBIDDEN.getStatusCode()) + "-1");
                } else if (e instanceof EJBAccessException && message.startsWith("WFLYEJB0364:")) {
                    qe.setErrorCode(Integer.toString(Response.Status.FORBIDDEN.getStatusCode()) + "-1");
                } else {
                    qe.setErrorCode(DatawaveErrorCode.UNKNOWN_SERVER_ERROR.getErrorCode());
                }
            }
            
            response.addException(qe.getBottomQueryException());
            ResponseBuilder r = Response.status(qe.getBottomQueryException().getStatusCode()).entity(response)
                            .header(Constants.RESPONSE_ORIGIN, RESPONSE_ORIGIN);
            // Add CORS headers in case this is a CORS request that has thrown an exception. We have to add
            // this so that they can see the response body
            r.header(CorsHeaders.ACCESS_CONTROL_MAX_AGE, DatawaveCorsFilter.MAX_AGE);
            r.header(CorsHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, requestOrigin);
            r.header(CorsHeaders.ACCESS_CONTROL_ALLOW_HEADERS, DatawaveCorsFilter.ALLOWED_HEADERS);
            r.header(CorsHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, DatawaveCorsFilter.ALLOW_CREDENTIALS);
            
            return r.build();
        }
        
    }
}
