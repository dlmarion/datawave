package nsa.datawave.webservice.datadictionary;

import nsa.datawave.security.authorization.DatawavePrincipal;
import nsa.datawave.security.util.AuthorizationsUtil;
import nsa.datawave.webservice.common.connection.AccumuloConnectionFactory;
import nsa.datawave.webservice.query.result.event.DefaultResponseObjectFactory;
import nsa.datawave.webservice.query.result.event.ResponseObjectFactory;
import nsa.datawave.webservice.results.datadictionary.DataDictionaryBase;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.security.Authorizations;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.*;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import javax.ejb.EJBContext;
import javax.xml.bind.*;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertNotNull;

@RunWith(EasyMockRunner.class)
public class DataDictionaryBeanTest extends EasyMockSupport {
    
    @Mock
    private DatawaveDataDictionary dictionary;
    @Mock
    private AccumuloConnectionFactory connectionFactory;
    @Mock
    private EJBContext ctx;
    @Mock
    private DatawavePrincipal principal;
    @Mock
    private Connector connector;
    @Mock
    private DataDictionaryConfiguration config;
    
    private ResponseObjectFactory responseObjectFactory = new DefaultResponseObjectFactory();
    
    // placeholder parameter values
    private String model = "model";
    private String modelTable = "modelTable";
    private String metaTable = "metaTable";
    private String auths = "AUTH_1";
    private List<List<String>> userAuths = Collections.singletonList(Arrays.asList(auths));
    private Set<Authorizations> setOfAuthObjs = AuthorizationsUtil.mergeAuthorizations(auths, userAuths);
    
    /**
     * Test basic marshalling and creation of object.
     *
     * @throws Exception
     */
    @Test
    public void testDictionary() throws JAXBException {
        JAXBContext ctx = JAXBContext.newInstance(nsa.datawave.webservice.results.datadictionary.DefaultDataDictionary.class);
        Marshaller u = ctx.createMarshaller();
        nsa.datawave.webservice.results.datadictionary.DefaultDataDictionary dd = new nsa.datawave.webservice.results.datadictionary.DefaultDataDictionary();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        u.marshal(dd, baos);
    }
    
    @Test
    public void testGetWithDataFilters() throws Exception {
        String dataTypesInput = "dataType1,dataType2,";
        Collection<String> expectedFilters = Arrays.asList("dataType1", "dataType2");
        callGetWithDataTypeFilters(dataTypesInput, expectedFilters);
    }
    
    @Test
    public void testGetWithNullDataFilters() throws Exception {
        String dataTypesInput = null;
        Collection<String> expectedFilters = Collections.emptyList();
        callGetWithDataTypeFilters(dataTypesInput, expectedFilters);
    }
    
    @Test
    public void testGetWithBlankDataFilters() throws Exception {
        
        String dataTypesInput = "";
        Collection<String> expectedFilters = Collections.emptyList();
        callGetWithDataTypeFilters(dataTypesInput, expectedFilters);
    }
    
    private void callGetWithDataTypeFilters(String dataTypesInput, Collection<String> expectedDataTypeFilters) throws Exception {
        DataDictionaryBean dictionaryBean = createPartiallyMockedDictionaryBean();
        expect(ctx.getCallerPrincipal()).andReturn(principal);
        Map<String,Collection<String>> expectedAuths = new HashMap<>();
        expectedAuths.put("user", Collections.singletonList(auths));
        expect(principal.getAuthorizationsMap()).andReturn(expectedAuths);
        expect(principal.getUserDN()).andReturn("user");
        expect(this.dictionary.getFields(model, modelTable, metaTable, expectedDataTypeFilters, this.connector, setOfAuthObjs, 1)).andReturn(
                        Collections.EMPTY_SET);
        replayAll();
        
        DataDictionaryBase providedDictionary = dictionaryBean.get(model, modelTable, metaTable, auths, dataTypesInput);
        verifyAll();
        
        assertNotNull("Expected a non-null response", providedDictionary);
    }
    
    private DataDictionaryBean createPartiallyMockedDictionaryBean() throws Exception {
        DataDictionaryBean dictionaryBean = createMockBuilder(DataDictionaryBean.class).addMockedMethod("getConnector").createMock();
        mockBoilerPlateCalls(dictionaryBean);
        overrideDependenciesWithMocks(dictionaryBean);
        return dictionaryBean;
    }
    
    private void mockBoilerPlateCalls(DataDictionaryBean dictionaryBean) throws Exception {
        expect(this.config.getNumThreads()).andReturn(1);
        
        expectGetConnectionAndReturnConnection(dictionaryBean);
    }
    
    private void expectGetConnectionAndReturnConnection(DataDictionaryBean dictionaryBean) throws Exception {
        expect(dictionaryBean.getConnector()).andReturn(connector);
        this.connectionFactory.returnConnection(connector);
        EasyMock.expectLastCall().once();
    }
    
    private void overrideDependenciesWithMocks(DataDictionaryBean dictionaryBean) throws IllegalAccessException {
        Whitebox.getField(DataDictionaryBean.class, "connectionFactory").set(dictionaryBean, this.connectionFactory);
        Whitebox.getField(DataDictionaryBean.class, "dataDictionaryConfiguration").set(dictionaryBean, this.config);
        Whitebox.getField(DataDictionaryBean.class, "ctx").set(dictionaryBean, this.ctx);
        Whitebox.getField(DataDictionaryBean.class, "datawaveDataDictionary").set(dictionaryBean, this.dictionary);
        Whitebox.getField(DataDictionaryBean.class, "responseObjectFactory").set(dictionaryBean, this.responseObjectFactory);
    }
}
