package gov.assets;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="alerts")
public class Alert {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long assetId;
    public String assetName;
    public String message;
    public Instant createdAt;
    public boolean resolved;
    public Instant resolvedAt;
    public String getAssetName(){return assetName;} public String getMessage(){return message;}
    public Instant getCreatedAt(){return createdAt;} public boolean isResolved(){return resolved;}
}
