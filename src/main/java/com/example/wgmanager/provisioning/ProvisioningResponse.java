package com.example.wgmanager.provisioning;
import java.util.UUID;
public record ProvisioningResponse(UUID id,String name,int listenPort,String subnet,String publicKey,String status){
 static ProvisioningResponse ready(WgInterface i){return new ProvisioningResponse(i.getId(),i.getName(),i.getListenPort(),i.getSubnet(),i.getPublicKey(),"READY");}
}
