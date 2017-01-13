package nsa.datawave.interceptor;

import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import nsa.datawave.Constants;
import nsa.datawave.webservice.common.exception.NoResultsException;
import nsa.datawave.webservice.result.BaseQueryResponse;
import nsa.datawave.webservice.result.BaseResponse;

import java.io.IOException;

/**
 *
 * Interceptor to be used on methods that return a BaseResponse object. This interceptor will add the timing information and exception information to the
 * response. Methods that use this interceptor should not throw any exceptions. Exceptions should be captured in the response object.
 *
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class ResponseInterceptor implements ContainerResponseFilter {
    
    private static String ORIGIN = null;
    
    @AroundInvoke
    public Object invoke(InvocationContext ctx) throws Exception {
        boolean isResponseObject = Response.class.isAssignableFrom(ctx.getMethod().getReturnType());
        boolean isBaseResponseObject = BaseResponse.class.isAssignableFrom(ctx.getMethod().getReturnType());
        
        // If response type is not BaseResponse or subclass, then move on
        if (!isResponseObject && !isBaseResponseObject)
            return ctx.proceed();
        
        long start = System.currentTimeMillis();
        // Invoke the method
        if (isResponseObject) {
            Response result = (Response) ctx.proceed();
            long end = System.currentTimeMillis();
            result.getMetadata().add(Constants.OPERATION_TIME, (end - start));
            if (result.getEntity() instanceof BaseResponse)
                ((BaseResponse) result.getEntity()).setOperationTimeMS((end - start));
            return result;
        } else {
            BaseResponse result;
            try {
                result = (BaseResponse) ctx.proceed();
            } catch (NoResultsException e) {
                e.setStartTime(start);
                e.setEndTime(System.currentTimeMillis());
                throw e;
            }
            long end = System.currentTimeMillis();
            result.setOperationTimeMS((end - start));
            return result;
        }
    }
    
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        if (null == ORIGIN) {
            ORIGIN = System.getProperty("cluster.name") + "/" + System.getProperty("jboss.host.name");
        }
        
        if (response.getEntity() instanceof BaseResponse) {
            BaseResponse br = (BaseResponse) response.getEntity();
            response.getHeaders().add(Constants.OPERATION_TIME, br.getOperationTimeMS());
        }
        if (response.getEntity() instanceof BaseQueryResponse) {
            BaseQueryResponse bqr = (BaseQueryResponse) response.getEntity();
            response.getHeaders().add(Constants.PAGE_NUMBER, bqr.getPageNumber());
            response.getHeaders().add(Constants.IS_LAST_PAGE, !bqr.getHasResults());
            response.getHeaders().add(Constants.PARTIAL_RESULTS, bqr.isPartialResults());
        }
        try {
            response.getHeaders().add(Constants.RESPONSE_ORIGIN, ORIGIN);
        } catch (UnsupportedOperationException e) {
            // this only happens when there's an error earlier in the chain
        }
    }
}
