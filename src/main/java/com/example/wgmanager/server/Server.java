package com.example.wgmanager.server;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "servers")
public class Server {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 255) private String host;
    @Column(name = "ssh_port", nullable = false) private int sshPort;
    @Column(name = "ssh_user", nullable = false, length = 64) private String sshUser;
    @Column(name = "encrypted_credentials", nullable = false) private byte[] encryptedCredentials;
    @Column(name = "host_key_fingerprint", length = 255) private String hostKeyFingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ServerStatus status = ServerStatus.PENDING;
    @Column(name = "operating_system", length = 32) private String operatingSystem;
    @Column(name = "connection_error", length = 255) private String connectionError;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected Server() {}
    public Server(UUID userId, String name, String host, int sshPort, String sshUser, byte[] credentials) {
        this.userId=userId; this.name=name; this.host=host; this.sshPort=sshPort; this.sshUser=sshUser; this.encryptedCredentials=credentials;
    }
    @PrePersist void created() { if (createdAt == null) createdAt=Instant.now(); }
    public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getName(){return name;}
    public String getHost(){return host;} public int getSshPort(){return sshPort;} public String getSshUser(){return sshUser;}
    public byte[] getEncryptedCredentials(){return encryptedCredentials;} public String getHostKeyFingerprint(){return hostKeyFingerprint;}
    public ServerStatus getStatus(){return status;} public String getOperatingSystem(){return operatingSystem;} public String getConnectionError(){return connectionError;} public Instant getCreatedAt(){return createdAt;}
    public void update(String name,String host,int port,String sshUser,byte[] credentials){this.name=name;this.host=host;this.sshPort=port;this.sshUser=sshUser;if(credentials!=null)this.encryptedCredentials=credentials;}
    public void connectionResult(String fingerprint,String os){this.hostKeyFingerprint=fingerprint;this.operatingSystem=os;this.status=ServerStatus.ONLINE;this.connectionError=null;}
    public void markOnline(){this.status=ServerStatus.ONLINE;this.connectionError=null;}
    public void markError(){markError("Server is unreachable");}
    public void markError(String error){this.status=ServerStatus.ERROR;this.connectionError=error;}
}
