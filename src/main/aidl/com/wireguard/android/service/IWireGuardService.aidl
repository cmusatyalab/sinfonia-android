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

    void createTunnel(String tunnelName, in ParcelableConfig parcelaleConfig);

    void destroyTunnel(String tunnelName);

    void setTunnelUp(String tunnelName);

    void setTunnelDown(String tunnelName);

    void getTunnelConfig(String tunnelName, out ParcelableConfig parcelableConfig);
}