package nsa.datawave.ingest.data.config;

import nsa.datawave.TestBaseIngestHelper;
import nsa.datawave.data.type.DateType;
import nsa.datawave.data.type.HexStringType;
import nsa.datawave.data.type.LcNoDiacriticsType;
import nsa.datawave.data.type.NoOpType;
import nsa.datawave.data.type.Type;
import nsa.datawave.ingest.data.TypeRegistry;
import nsa.datawave.ingest.data.config.ingest.BaseIngestHelper;
import nsa.datawave.ingest.mapreduce.SimpleDataTypeHandler;
import nsa.datawave.policy.IngestPolicyEnforcer;
import org.apache.hadoop.conf.Configuration;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FieldConfigHelperTest {
    
    final BaseIngestHelper ingestHelper = new TestBaseIngestHelper();
    
    @Before
    public void setUp() {
        Configuration conf = new Configuration();
        conf.set(DataTypeHelper.Properties.DATA_NAME, "test");
        conf.set("test" + DataTypeHelper.Properties.INGEST_POLICY_ENFORCER_CLASS, IngestPolicyEnforcer.NoOpIngestPolicyEnforcer.class.getName());
        conf.set("test" + BaseIngestHelper.DEFAULT_TYPE, NoOpType.class.getName());
        
        nsa.datawave.ingest.data.Type type = new nsa.datawave.ingest.data.Type("test", null, null, new String[] {SimpleDataTypeHandler.class.getName()}, 10,
                        null);
        TypeRegistry.reset();
        TypeRegistry.getInstance(conf).put("test", type);
        
        ingestHelper.setup(conf);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testBadTag() throws Exception {
        String input = "<?xml version=\"1.0\"?>\n"
                        + "<fieldConfig>\n"
                        + "    <default indexed=\"false\" reverseIndexed=\"false\" tokenized=\"true\" reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.LcNoDiacriticsType\"/>\n"
                        + "    <nomatch indexed=\"true\" reverseIndexed=\"true\" tokenized=\"true\"  reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.HexStringType\"/>\n"
                        + "    <fieldPattern pattern=\"*J\" indexed=\"true\" indexType=\"nsa.datawave.data.type.MacAddressType\"/>\n"
                        + "    <field name=\"H\" indexType=\"nsa.datawave.data.type.DateType\"/>\n"
                        + "    <orange name=\"H\" indexType=\"nsa.datawave.data.type.DateType\"/>\n" + "</fieldConfig>";
        
        FieldConfigHelper helper = new FieldConfigHelper(new ByteArrayInputStream(input.getBytes()), ingestHelper);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testDuplicateField() throws Exception {
        String input = "<?xml version=\"1.0\"?>\n"
                        + "<fieldConfig>\n"
                        + "    <default indexed=\"false\" reverseIndexed=\"false\" tokenized=\"true\" reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.LcNoDiacriticsType\"/>\n"
                        + "    <nomatch indexed=\"true\" reverseIndexed=\"true\" tokenized=\"true\"  reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.HexStringType\"/>\n"
                        + "    <fieldPattern pattern=\"*J\" indexed=\"true\" indexType=\"nsa.datawave.data.type.MacAddressType\"/>\n"
                        + "    <field name=\"H\" indexType=\"nsa.datawave.data.type.DateType\"/>\n"
                        + "    <field name=\"H\" indexType=\"nsa.datawave.data.type.HexStringType\"/>\n" + "</fieldConfig>";
        
        FieldConfigHelper helper = new FieldConfigHelper(new ByteArrayInputStream(input.getBytes()), ingestHelper);
    }
    
    @Test(expected = IllegalStateException.class)
    public void testMissingDefault() throws Exception {
        String input = "<?xml version=\"1.0\"?>\n"
                        + "<fieldConfig>\n"
                        + "    <nomatch indexed=\"true\" reverseIndexed=\"true\" tokenized=\"true\"  reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.HexStringType\"/>\n"
                        + "    <field name=\"A\" indexed=\"true\"/>\n" + "</fieldConfig>";
        
        FieldConfigHelper helper = new FieldConfigHelper(new ByteArrayInputStream(input.getBytes()), ingestHelper);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testIncompleteDefault() throws Exception {
        String input = "<?xml version=\"1.0\"?>\n"
                        + "<fieldConfig>\n"
                        + "    <default reverseIndexed=\"false\" tokenized=\"true\" reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.LcNoDiacriticsType\"/>\n"
                        + "    <nomatch indexed=\"true\" reverseIndexed=\"true\" tokenized=\"true\"  reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.HexStringType\"/>\n"
                        + "    <fieldPattern pattern=\"*J\" indexed=\"true\" indexType=\"nsa.datawave.data.type.MacAddressType\"/>\n"
                        + "    <field name=\"A\" indexed=\"true\"/>\n" +
                        
                        "</fieldConfig>";
        
        FieldConfigHelper helper = new FieldConfigHelper(new ByteArrayInputStream(input.getBytes()), ingestHelper);
    }
    
    public void testMissingNomatch() throws Exception {
        String input = "<?xml version=\"1.0\"?>\n"
                        + "<fieldConfig>\n"
                        + "    <default indexed=\"false\" reverseIndexed=\"false\" tokenized=\"true\" reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.LcNoDiacriticsType\"/>\n"
                        + "    <fieldPattern pattern=\"*J\" indexed=\"true\" indexType=\"nsa.datawave.data.type.MacAddressType\"/>\n"
                        + "    <field name=\"H\" indexType=\"nsa.datawave.data.type.DateType\"/>\n" + "</fieldConfig>";
        
        FieldConfigHelper helper = new FieldConfigHelper(new ByteArrayInputStream(input.getBytes()), ingestHelper);
        // ok.
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testIncompleteNomatch() throws Exception {
        String input = "<?xml version=\"1.0\"?>\n"
                        + "<fieldConfig>\n"
                        + "    <default indexed=\"false\" reverseIndexed=\"false\" tokenized=\"true\" reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.LcNoDiacriticsType\"/>\n"
                        + "    <nomatch reverseIndexed=\"true\" tokenized=\"true\"  reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.HexStringType\"/>\n"
                        + "    <fieldPattern pattern=\"*J\" indexed=\"true\" indexType=\"nsa.datawave.data.type.MacAddressType\"/>\n"
                        + "    <field name=\"H\" indexType=\"nsa.datawave.data.type.DateType\"/>\n" + "</fieldConfig>";
        
        FieldConfigHelper helper = new FieldConfigHelper(new ByteArrayInputStream(input.getBytes()), ingestHelper);
    }
    
    @Test
    public void testMultiType() throws Exception {
        String input = "<?xml version=\"1.0\"?>\n"
                        + "<fieldConfig>\n"
                        + "    <default indexed=\"false\" reverseIndexed=\"false\" tokenized=\"true\" reverseTokenized=\"true\" indexType=\"nsa.datawave.data.type.LcNoDiacriticsType\"/>\n"
                        + "    <fieldPattern pattern=\"*J\" indexed=\"true\" indexType=\"nsa.datawave.data.type.MacAddressType\"/>\n"
                        + "    <field name=\"H\" indexType=\"nsa.datawave.data.type.DateType,nsa.datawave.data.type.HexStringType\"/>\n" + "</fieldConfig>";
        
        FieldConfigHelper helper = new FieldConfigHelper(new ByteArrayInputStream(input.getBytes()), ingestHelper);
        
        List<Type<?>> types = ingestHelper.getDataTypes("H");
        assertEquals(2, types.size());
    }
    
    @Test
    public void testFieldConfigHelperWhitelist() throws Exception {
        InputStream in = ClassLoader.getSystemResourceAsStream("datawave/ingest/test-field-whitelist.xml");
        FieldConfigHelper helper = new FieldConfigHelper(in, ingestHelper);
        
        // this is whitelist behavior
        assertFalse(helper.isNoMatchIndexed());
        assertFalse(helper.isNoMatchReverseIndexed());
        assertFalse(helper.isNoMatchTokenized());
        assertFalse(helper.isNoMatchReverseTokenized());
        
        assertFalse(helper.isIndexedField("A"));
        assertTrue(helper.isIndexedField("B"));
        assertTrue(helper.isIndexedField("C"));
        assertTrue(helper.isIndexedField("D"));
        assertFalse(helper.isIndexedField("E"));
        assertTrue(helper.isIndexedField("F"));
        assertFalse(helper.isIndexedField("G"));
        assertTrue(helper.isIndexedField("H"));
        
        assertTrue(helper.isReverseIndexedField("A"));
        assertFalse(helper.isReverseIndexedField("B"));
        assertTrue(helper.isReverseIndexedField("C"));
        assertTrue(helper.isReverseIndexedField("D"));
        assertFalse(helper.isReverseIndexedField("E"));
        assertTrue(helper.isReverseIndexedField("F"));
        assertFalse(helper.isReverseIndexedField("G"));
        assertTrue(helper.isReverseIndexedField("H"));
        
        assertFalse(helper.isTokenizedField("A"));
        assertFalse(helper.isTokenizedField("B"));
        assertTrue(helper.isTokenizedField("C"));
        assertFalse(helper.isTokenizedField("D"));
        assertFalse(helper.isTokenizedField("E"));
        assertTrue(helper.isTokenizedField("F"));
        assertFalse(helper.isTokenizedField("G"));
        assertFalse(helper.isTokenizedField("H"));
        
        assertFalse(helper.isReverseTokenizedField("A"));
        assertFalse(helper.isReverseTokenizedField("B"));
        assertFalse(helper.isReverseTokenizedField("C"));
        assertTrue(helper.isReverseTokenizedField("D"));
        assertFalse(helper.isReverseTokenizedField("E"));
        assertTrue(helper.isReverseTokenizedField("F"));
        assertFalse(helper.isReverseTokenizedField("G"));
        assertFalse(helper.isReverseTokenizedField("H"));
        
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("A"));
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("B"));
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("C"));
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("D"));
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("E"));
        assertType(DateType.class, ingestHelper.getDataTypes("F"));
        assertType(HexStringType.class, ingestHelper.getDataTypes("G"));
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("H"));
    }
    
    public static void assertType(Class<?> expected, List<Type<?>> observedList) {
        int count = 0;
        for (Type<?> observed : observedList) {
            
            if (expected.isAssignableFrom(observed.getClass())) {
                count++;
            }
        }
        assertTrue("Expected a single type to match " + expected.getName() + ", but " + count + " types matched; List was: " + observedList, count == 1);
    }
    
    @Test
    public void testFieldConfigHelperBlacklist() throws Exception {
        InputStream in = ClassLoader.getSystemResourceAsStream("datawave/ingest/test-field-blacklist.xml");
        FieldConfigHelper helper = new FieldConfigHelper(in, ingestHelper);
        
        // this is blacklist behavior
        assertTrue(helper.isNoMatchIndexed());
        assertTrue(helper.isNoMatchReverseIndexed());
        assertTrue(helper.isNoMatchTokenized());
        assertTrue(helper.isNoMatchReverseTokenized());
        
        assertTrue(helper.isIndexedField("A"));
        assertFalse(helper.isIndexedField("B"));
        assertFalse(helper.isIndexedField("C"));
        assertFalse(helper.isIndexedField("D"));
        assertTrue(helper.isIndexedField("E"));
        assertFalse(helper.isIndexedField("F"));
        assertTrue(helper.isIndexedField("G"));
        assertFalse(helper.isIndexedField("H"));
        
        assertFalse(helper.isReverseIndexedField("A"));
        assertTrue(helper.isReverseIndexedField("B"));
        assertFalse(helper.isReverseIndexedField("C"));
        assertFalse(helper.isReverseIndexedField("D"));
        assertFalse(helper.isReverseIndexedField("E"));
        assertFalse(helper.isReverseIndexedField("F"));
        assertTrue(helper.isReverseIndexedField("G"));
        assertFalse(helper.isReverseIndexedField("H"));
        
        assertTrue(helper.isTokenizedField("A"));
        assertTrue(helper.isTokenizedField("B"));
        assertFalse(helper.isTokenizedField("C"));
        assertTrue(helper.isTokenizedField("D"));
        assertTrue(helper.isTokenizedField("E"));
        assertFalse(helper.isTokenizedField("F"));
        assertTrue(helper.isTokenizedField("G"));
        assertTrue(helper.isTokenizedField("H"));
        
        assertTrue(helper.isReverseTokenizedField("A"));
        assertTrue(helper.isReverseTokenizedField("B"));
        assertTrue(helper.isReverseTokenizedField("C"));
        assertFalse(helper.isReverseTokenizedField("D"));
        assertTrue(helper.isReverseTokenizedField("E"));
        assertFalse(helper.isReverseTokenizedField("F"));
        assertTrue(helper.isReverseTokenizedField("G"));
        assertTrue(helper.isReverseTokenizedField("H"));
        
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("A"));
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("B"));
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("C"));
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("D"));
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("E"));
        assertType(LcNoDiacriticsType.class, ingestHelper.getDataTypes("F"));
        assertType(HexStringType.class, ingestHelper.getDataTypes("G"));
        assertType(DateType.class, ingestHelper.getDataTypes("H"));
    }
}
