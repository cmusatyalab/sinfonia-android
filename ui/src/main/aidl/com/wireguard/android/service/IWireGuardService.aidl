// IWireGuardService.aidl
package com.wireguard.android.service;

// Declare any non-default types here with import statements
import edu.cmu.cs.sinfonia.wireguard.ParcelableConfig;

interface IWireGuardService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
            double aDouble, String aString);

    void refreshTunnels();

    void createTunnel(String tunnelName, in ParcelableConfig parcelableConfig);

    void destroyTunnel(String tunnelName);

    boolean setTunnelUp(String tunnelName);

    boolean setTunnelDown(String tunnelName);

    ParcelableConfig getTunnelConfig(String tunnelName);
}