package nsa.datawave.webservice.common.audit;

import java.io.Serializable;
import java.util.List;

import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.annotation.security.RunAs;
import javax.ejb.Asynchronous;
import javax.ejb.EJBContext;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Topic;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MultivaluedMap;

import nsa.datawave.configuration.DatawaveEmbeddedProjectStageHolder;
import nsa.datawave.webservice.common.exception.DatawaveWebApplicationException;
import nsa.datawave.webservice.query.exception.DatawaveErrorCode;
import nsa.datawave.webservice.query.exception.QueryException;
import nsa.datawave.webservice.result.VoidResponse;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.apache.deltaspike.core.api.exclude.Exclude;
import org.apache.log4j.Logger;
import org.jboss.resteasy.annotations.GZIP;

@Path("/Common/Auditor")
@LocalBean
@Stateless
@RunAs("InternalUser")
@RolesAllowed({"AuthorizedUser", "AuthorizedServer", "InternalUser", "Administrator"})
@DeclareRoles({"AuthorizedUser", "AuthorizedServer", "InternalUser", "Administrator"})
@Exclude(ifProjectStage = DatawaveEmbeddedProjectStageHolder.DatawaveEmbedded.class)
public class AuditBean implements Auditor {
    private static final Logger log = Logger.getLogger(AuditBean.class);
    
    @Resource
    private EJBContext ctx;
    
    @Inject
    private AuditParameters auditParameters;
    
    @Inject
    private JMSContext jmsContext;
    
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Inject
    @ConfigProperty(name = "dw.audit.topics")
    private List<String> auditTopics;
    
    protected void sendMessage(AuditParameters parameters) throws Exception {
        try {
            JMSProducer producer = jmsContext.createProducer();
            for (String topicName : auditTopics) {
                Topic topic = jmsContext.createTopic(topicName);
                producer.send(topic, (Serializable) parameters.toMap());
            }
        } catch (Exception e) {
            log.error("Error sending audit message: " + parameters.toString(), e);
            throw e;
        }
    }
    
    @Override
    @Asynchronous
    public void audit(AuditParameters parameters) throws Exception {
        sendMessage(parameters);
    }
    
    @POST
    @Path("/audit")
    @Consumes("*/*")
    @Produces({"application/xml", "text/xml", "application/json", "text/yaml", "text/x-yaml", "application/x-yaml", "application/x-protobuf",
            "application/x-protostuff"})
    @GZIP
    public VoidResponse audit(MultivaluedMap<String,String> parameters) {
        VoidResponse response = new VoidResponse();
        try {
            auditParameters.clear();
            auditParameters.validate(parameters);
            audit(auditParameters);
            return response;
        } catch (Exception e) {
            QueryException qe = new QueryException(DatawaveErrorCode.AUDITING_ERROR, e);
            log.error(qe);
            response.addException(qe.getBottomQueryException());
            int statusCode = qe.getBottomQueryException().getStatusCode();
            throw new DatawaveWebApplicationException(qe, response, statusCode);
        }
    }
}
