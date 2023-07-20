package com.wireguard.android.service;

@VintfStability
interface IWireguardService {
    void refreshTunnels();

    void createTunnel(String tunnelName);

    void destroyTunnel(String tunnelName);

    void setTunnelUp(String tunnelName);

    void setTunnelDown(String tunnelName);
}