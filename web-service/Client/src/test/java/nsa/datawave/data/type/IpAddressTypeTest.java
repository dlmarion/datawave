/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nsa.datawave.data.type;

import nsa.datawave.data.type.util.IpV4Address;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 * 
 */
public class IpAddressTypeTest {
    private static Logger log = Logger.getLogger(IpAddressTypeTest.class);
    
    @Test
    public void testIpNormalizer01() {
        String ip = "1.2.3.4";
        String expected = "001.002.003.004";
        IpAddressType norm = new IpAddressType();
        String result = norm.normalize(ip);
        Assert.assertEquals(expected, result);
        log.debug("result: " + result);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testIpNormalizer02() {
        String ip = "1.2.3";
        IpAddressType norm = new IpAddressType();
        norm.normalize(ip);
    }
    
    @Test
    public void testIpNormalizer03() {
        IpAddressType norm = new IpAddressType();
        if (log.isDebugEnabled()) {
            log.debug("testIpNormalizer03");
            log.debug(norm.normalize("1.2.3.*"));
            log.debug(norm.normalize("1.2.3..*"));
            log.debug(norm.normalize("1.2.*"));
            log.debug(norm.normalize("1.2..*"));
            log.debug(norm.normalize("1.*"));
            log.debug(norm.normalize("1..*"));
            
        }
        Assert.assertEquals("001.002.003.*", norm.normalize("1.2.3.*"));
        Assert.assertEquals("001.002.003.*", norm.normalize("1.2.3..*"));
        Assert.assertEquals("001.002.*", norm.normalize("1.2.*"));
        Assert.assertEquals("001.002.*", norm.normalize("1.2..*"));
        Assert.assertEquals("001.*", norm.normalize("1.*"));
        Assert.assertEquals("001.*", norm.normalize("1..*"));
    }
    
    @Test
    public void testIpNormalizer04() {
        log.debug("testIpNormalizer04");
        IpAddressType norm = new IpAddressType();
        log.debug(norm.normalize("*.2.13.4"));
        log.debug(norm.normalize("*.13.4"));
        Assert.assertEquals("*.002.013.004", norm.normalize("*.2.13.4"));
        Assert.assertEquals("*.013.004", norm.normalize("*.13.4"));
    }
    
    // @Test TEST IS TURNED OFF
    public void testIpNormalizer05() {
        log.debug("testIpNormalizer05");
        IpV4Address ip = IpV4Address.parse("*.2.13.4");
        if (log.isDebugEnabled()) {
            log.debug(ip.toString());
            log.debug(ip.toZeroPaddedString());
            log.debug(ip.toReverseString());
            log.debug(ip.toReverseZeroPaddedString());
        }
    }
    
    /*
     * NOTE: call toReverseString() on a wildcarded ip doesn't work right although this is not much of an issue.
     */
    // @Test TEST IS TURNED OFF
    public void testIpNormalizer06() {
        log.debug("testIpNormalizer06");
        IpV4Address ip = IpV4Address.parse("1.2.*");
        if (log.isDebugEnabled()) {
            log.debug(ip.toString());
            log.debug(ip.toZeroPaddedString());
            log.debug(ip.toReverseString());
            log.debug(ip.toReverseZeroPaddedString());
        }
    }
}
