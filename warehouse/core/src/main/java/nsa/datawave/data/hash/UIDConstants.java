package nsa.datawave.data.hash;

/**
 * Constants used for [internal] UIDs
 */
public interface UIDConstants {
    /**
     * Character used for separating various parts of a UID, such as hashes and "extra" strings
     */
    public char DEFAULT_SEPARATOR = '.';
    
    /**
     * The index of the poller's host based on the STAGING_HOSTS property
     */
    public String HOST_INDEX_OPT = "hostIndex";
    
    /**
     * The number of milliseconds in a day
     */
    public int MILLISECONDS_PER_DAY = (24 * 60 * 60 * 1000);
    
    /**
     * The index of the poller based on the POLLER_DATA_TYPES property
     */
    public String POLLER_INDEX_OPT = "pollerIndex";
    
    /**
     * The one-up index of the poller thread
     */
    public String THREAD_INDEX_OPT = "threadIndex";
    
    /**
     * A delimiter used to optionally identify a timestamp-based component of a UID (particularly hash-based UIDs)
     */
    public char TIME_SEPARATOR = '+';
    
    /**
     * The type of UID to generate (default is the traditional Murmur-hash based UID)
     */
    public String UID_TYPE_OPT = "uidType";
    
    /**
     * The base name for UID properties, as applicable, in the Hadoop configuration
     */
    public String CONFIG_BASE_KEY = UID.class.getName().toLowerCase();
    
    /**
     * The configuration key for the machine ID value <i>required</i> for generating {@link SnowflakeUID}s. See the {@link SnowflakeUID} documentation for
     * details about this value.
     */
    public String CONFIG_MACHINE_ID_KEY = CONFIG_BASE_KEY + ".machineId";
    
    /**
     * The configuration key for the UID type. If this property is not specified, the {@code UID.builder()} methods will return a hash-based {@link UIDBuilder}
     * by default.
     */
    public String CONFIG_UID_TYPE_KEY = CONFIG_BASE_KEY + '.' + UID_TYPE_OPT;
}
