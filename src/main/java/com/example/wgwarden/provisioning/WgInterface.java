package com.example.wgwarden.provisioning;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="wg_interfaces",uniqueConstraints=@UniqueConstraint(name="uq_wg_interface_server_name",columnNames={"server_id","name"}))
public class WgInterface {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="server_id",nullable=false) private UUID serverId;
    @Column(nullable=false,length=15) private String name;
    @Column(name="listen_port",nullable=false) private int listenPort;
    @Column(nullable=false,length=64) private String subnet;
    @Column(name="public_key",nullable=false,length=64) private String publicKey;
    @Column(name="encrypted_private_key",nullable=false) private byte[] encryptedPrivateKey;
    protected WgInterface(){}
    public WgInterface(UUID serverId,String name,int listenPort,String subnet,String publicKey,byte[] encryptedPrivateKey){this.serverId=serverId;this.name=name;this.listenPort=listenPort;this.subnet=subnet;this.publicKey=publicKey;this.encryptedPrivateKey=encryptedPrivateKey;}
    public UUID getId(){return id;} public UUID getServerId(){return serverId;} public String getName(){return name;} public int getListenPort(){return listenPort;}
    public String getSubnet(){return subnet;} public String getPublicKey(){return publicKey;} public byte[] getEncryptedPrivateKey(){return encryptedPrivateKey;}
    public void updateKeys(String publicKey, byte[] encryptedPrivateKey){this.publicKey=publicKey;this.encryptedPrivateKey=encryptedPrivateKey;}
}
