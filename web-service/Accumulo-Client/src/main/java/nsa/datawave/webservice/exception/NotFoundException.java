package nsa.datawave.webservice.exception;

import javax.ejb.ApplicationException;
import javax.ws.rs.core.Response;

import nsa.datawave.webservice.response.BaseResponse;

@ApplicationException(rollback = true)
public class NotFoundException extends AccumuloWebApplicationException {
    
    private static final long serialVersionUID = 1L;
    
    public NotFoundException(Throwable t, BaseResponse response) {
        super(t, response, Response.Status.NOT_FOUND.getStatusCode());
    }
    
}
