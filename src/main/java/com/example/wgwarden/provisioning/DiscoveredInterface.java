package com.example.wgwarden.provisioning;
import java.util.List;
public record DiscoveredInterface(String name, int listenPort, String subnet, String publicKey, List<DiscoveredPeer> peers) {}
