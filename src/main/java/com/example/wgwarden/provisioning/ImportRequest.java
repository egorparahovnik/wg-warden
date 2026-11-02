package com.example.wgwarden.provisioning;
import java.util.List;
import java.util.Map;
public record ImportRequest(String interfaceName, List<String> peerPublicKeys, Map<String,String> peerPrivateKeys) {}
