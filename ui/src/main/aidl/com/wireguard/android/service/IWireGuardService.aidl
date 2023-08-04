// IWireGuardService.aidl
package com.wireguard.android.service;

// Declare any non-default types here with import statements
import edu.cmu.cs.sinfonia.wireguard.ParcelableConfig;
import edu.cmu.cs.sinfonia.util.TunnelException;

interface IWireGuardService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
            double aDouble, String aString);

    String[] fetchMyTunnels(in String[] applications);

    void refreshTunnels();

    TunnelException createTunnel(String tunnelName, in ParcelableConfig parcelableConfig);

    TunnelException destroyTunnel(String tunnelName);

    TunnelException setTunnelUp(String tunnelName);

    TunnelException setTunnelDown(String tunnelName);

    TunnelException setTunnelToggle(String tunnelName);

    ParcelableConfig getTunnelConfig(String tunnelName);

    ParcelableConfig setTunnelConfig(String tunnelName, in ParcelableConfig parcelableConfig);
}