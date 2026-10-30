package gov.assets;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "blockchain_blocks")
public class Block {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long blockIndex;

    @Column(nullable = false, length = 64)
    public String previousHash;

    @Column(nullable = false, length = 64)
    public String hash;

    @Column(nullable = false)
    public Instant timestamp;

    public Long nonce;

    @Column(nullable = false, length = 50)
    public String action;

    public Long assetId;
    public String assetName;
    public String fromLocation;
    public String toLocation;
    public String operator;

    @Column(length = 500)
    public String details;

    public boolean authorized;

    public Long getId() { return id; }
    public Long getBlockIndex() { return blockIndex; }
    public String getPreviousHash() { return previousHash; }
    public String getHash() { return hash; }
    public Instant getTimestamp() { return timestamp; }
    public Long getNonce() { return nonce; }
    public String getAction() { return action; }
    public Long getAssetId() { return assetId; }
    public String getAssetName() { return assetName; }
    public String getFromLocation() { return fromLocation; }
    public String getToLocation() { return toLocation; }
    public String getOperator() { return operator; }
    public String getDetails() { return details; }
    public boolean isAuthorized() { return authorized; }
}
