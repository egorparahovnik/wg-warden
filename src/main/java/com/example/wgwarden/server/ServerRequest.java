package com.example.wgwarden.server;
import jakarta.validation.constraints.*;
public record ServerRequest(@NotBlank @Size(max=100) String name, @NotBlank @Size(max=255) String host,
 @Min(1) @Max(65535) int sshPort, @NotBlank @Size(max=64) @Pattern(regexp="[A-Za-z_][A-Za-z0-9_.-]*") String sshUser,
 Credentials credentials) {
 public record Credentials(String password, String privateKey, String passphrase) {
  public boolean valid(){return (password!=null&&!password.isBlank()) ^ (privateKey!=null&&!privateKey.isBlank());}
 }
}
