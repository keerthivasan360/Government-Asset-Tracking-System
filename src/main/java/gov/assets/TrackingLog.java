package gov.assets;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="tracking_logs")
public class TrackingLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long assetId;
    public String assetName;
    @Enumerated(EnumType.STRING) public Asset.Status status;
    public Instant timestamp;
    public String source;
    public Long getAssetId(){return assetId;} public String getAssetName(){return assetName;}
    public Asset.Status getStatus(){return status;} public Instant getTimestamp(){return timestamp;} public String getSource(){return source;}
}
