/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nsa.datawave.webservice.common.cache;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.listen.Listenable;
import org.apache.curator.framework.recipes.shared.SharedValue;
import org.apache.curator.framework.recipes.shared.SharedValueListener;
import org.apache.curator.framework.recipes.shared.SharedValueReader;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.log4j.Logger;

/**
 *
 *
 */
public class SharedBoolean implements Closeable, SharedBooleanReader, Listenable<SharedBooleanListener> {
    
    private static Logger log = Logger.getLogger(SharedBoolean.class);
    
    private final Map<SharedBooleanListener,SharedValueListener> listeners = Maps.newConcurrentMap();
    private final SharedValue sharedValue;
    
    public SharedBoolean(CuratorFramework client, String path, boolean seedValue) {
        this.sharedValue = new SharedValue(client, path, toBytes(seedValue));
        log.debug("sharedValue has " + Arrays.toString(sharedValue.getValue()) + " and getBoolean gives:" + getBoolean());
    }
    
    public boolean getBoolean() {
        log.debug("in getBoolean, sharedValue has " + Arrays.toString(sharedValue.getValue()));
        return fromBytes(this.sharedValue.getValue());
    }
    
    public void setBoolean(boolean newBoolean) throws Exception {
        this.sharedValue.setValue(toBytes(newBoolean));
        log.debug("someone setBoolean to " + newBoolean);
    }
    
    public boolean trySetBoolean(boolean newBoolean) throws Exception {
        log.debug("tryToSetBoolean(" + newBoolean + ")");
        return this.sharedValue.trySetValue(toBytes(newBoolean));
    }
    
    public void addListener(SharedBooleanListener listener) {
        this.addListener((SharedBooleanListener) listener, MoreExecutors.sameThreadExecutor());
    }
    
    public void addListener(final SharedBooleanListener listener, Executor executor) {
        SharedValueListener valueListener = new SharedValueListener() {
            public void valueHasChanged(SharedValueReader sharedValue, byte[] newValue) throws Exception {
                log.debug("valueHasChanged in " + Arrays.toString(sharedValue.getValue()) + " to " + Arrays.toString(newValue));
                listener.booleanHasChanged(SharedBoolean.this, SharedBoolean.fromBytes(newValue));
            }
            
            public void stateChanged(CuratorFramework client, ConnectionState newState) {
                listener.stateChanged(client, newState);
            }
        };
        this.sharedValue.getListenable().addListener(valueListener, executor);
        this.listeners.put(listener, valueListener);
    }
    
    public void removeListener(SharedBooleanListener listener) {
        this.listeners.remove(listener);
    }
    
    public void start() throws Exception {
        this.sharedValue.start();
    }
    
    public void close() throws IOException {
        this.sharedValue.close();
    }
    
    private static byte[] toBytes(boolean value) {
        return new byte[] {value ? (byte) 1 : (byte) 0};
    }
    
    private static boolean fromBytes(byte[] bytes) {
        log.debug("fromBytes lookin at " + Arrays.toString(bytes));
        log.debug("bytes.length:" + bytes.length);
        log.debug("does bytes[0] == (byte)1 ???" + (bytes[0] == (byte) 1));
        return bytes.length > 0 && bytes[0] == (byte) 1;
    }
    
    @Override
    public String toString() {
        return "SharedBoolean{" + "listeners=" + listeners + ", sharedValue=" + Arrays.toString(sharedValue.getValue()) + " which is "
                        + fromBytes(sharedValue.getValue()) + '}';
    }
}
