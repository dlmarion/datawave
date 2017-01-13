package nsa.datawave.webservice.query.util;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import nsa.datawave.webservice.query.Query;
import nsa.datawave.webservice.query.QueryImpl;
import nsa.datawave.webservice.query.QueryImpl.Parameter;

import org.apache.hadoop.io.Text;
import org.junit.Assert;
import org.junit.Test;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.ColumnVisibility;

import com.google.protobuf.InvalidProtocolBufferException;

public class QueryUtilTest {
    
    @Test
    public void testSerializationDeserialization() throws InvalidProtocolBufferException, ClassNotFoundException {
        QueryImpl q = new QueryImpl();
        q.setQueryLogicName("EventQuery");
        q.setExpirationDate(new Date());
        q.setId(UUID.randomUUID());
        q.setPagesize(10);
        q.setQuery("FOO == BAR");
        q.setQueryName("test query");
        q.setQueryAuthorizations("ALL");
        q.setUserDN("some user");
        q.setOwner("some owner");
        q.setColumnVisibility("A&B");
        
        Set<Parameter> parameters = new HashSet<>();
        parameters.add(new Parameter("some param", "some value"));
        q.setParameters(parameters);
        
        Mutation m = QueryUtil.toMutation(q, new ColumnVisibility(q.getColumnVisibility()));
        
        Assert.assertEquals(1, m.getUpdates().size());
        
        byte[] value = m.getUpdates().get(0).getValue();
        Query q2 = QueryUtil.deserialize(QueryImpl.class.getName(), new Text("A&B"), new Value(value));
        
        Assert.assertEquals(q, q2);
        
    }
    
}
