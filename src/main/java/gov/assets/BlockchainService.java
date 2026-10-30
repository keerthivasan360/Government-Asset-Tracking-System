package gov.assets;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class BlockchainService {
    private final BlockRepository blockRepository;

    public BlockchainService(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    @PostConstruct
    @Transactional
    public synchronized void init() {
        if (blockRepository.count() == 0) {
            createGenesisBlock();
        }
    }

    public synchronized Block createGenesisBlock() {
        Block genesis = new Block();
        genesis.blockIndex = 0L;
        genesis.previousHash = "0".repeat(64);
        genesis.timestamp = Instant.now();
        genesis.action = "GENESIS";
        genesis.assetId = null;
        genesis.assetName = "System Genesis Ledger";
        genesis.fromLocation = "SYSTEM";
        genesis.toLocation = "SYSTEM";
        genesis.operator = "system";
        genesis.details = "AssetGuard Immutable Audit Blockchain Initialized";
        genesis.authorized = true;
        mineBlock(genesis, 1);
        return blockRepository.save(genesis);
    }

    @Transactional
    public synchronized Block addBlock(String action, Long assetId, String assetName,
                                      String fromLocation, String toLocation,
                                      String operator, String details, boolean authorized) {
        Block lastBlock = blockRepository.findTopByOrderByBlockIndexDesc()
                .orElseGet(this::createGenesisBlock);

        Block block = new Block();
        block.blockIndex = lastBlock.blockIndex + 1;
        block.previousHash = lastBlock.hash;
        block.timestamp = Instant.now();
        block.action = action;
        block.assetId = assetId;
        block.assetName = assetName != null ? assetName : "N/A";
        block.fromLocation = fromLocation != null ? fromLocation : "Unknown";
        block.toLocation = toLocation != null ? toLocation : "Unknown";
        block.operator = operator != null ? operator : "admin";
        block.details = details != null ? details : "";
        block.authorized = authorized;

        mineBlock(block, 1); // 1 zero prefix difficulty for quick local mining with real PoW
        return blockRepository.save(block);
    }

    public static String calculateHash(Long index, String previousHash, Instant timestamp,
                                       String action, Long assetId, String fromLocation,
                                       String toLocation, String operator, String details,
                                       boolean authorized, Long nonce) {
        String data = index + ":" + previousHash + ":" + timestamp.toEpochMilli() + ":" +
                action + ":" + assetId + ":" + fromLocation + ":" + toLocation + ":" +
                operator + ":" + details + ":" + authorized + ":" + nonce;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private void mineBlock(Block block, int difficulty) {
        String prefix = "0".repeat(difficulty);
        long nonce = 0L;
        String hash;
        do {
            nonce++;
            hash = calculateHash(block.blockIndex, block.previousHash, block.timestamp,
                    block.action, block.assetId, block.fromLocation, block.toLocation,
                    block.operator, block.details, block.authorized, nonce);
        } while (!hash.startsWith(prefix) && nonce < 1_000_000L);
        block.nonce = nonce;
        block.hash = hash;
    }

    public record ChainVerificationResult(boolean valid, int totalBlocks, String latestHash, String error) {}

    public ChainVerificationResult verifyChain() {
        List<Block> chain = blockRepository.findAllByOrderByBlockIndexAsc();
        if (chain.isEmpty()) {
            return new ChainVerificationResult(true, 0, "", null);
        }

        for (int i = 0; i < chain.size(); i++) {
            Block current = chain.get(i);

            // Re-hash check
            String recalculated = calculateHash(current.blockIndex, current.previousHash,
                    current.timestamp, current.action, current.assetId, current.fromLocation,
                    current.toLocation, current.operator, current.details, current.authorized,
                    current.nonce);
            if (!recalculated.equalsIgnoreCase(current.hash)) {
                return new ChainVerificationResult(false, chain.size(), current.hash,
                        "Block #" + current.blockIndex + " cryptographic hash mismatch (tampered block).");
            }

            // Chain linkage check
            if (i > 0) {
                Block previous = chain.get(i - 1);
                if (!current.previousHash.equalsIgnoreCase(previous.hash)) {
                    return new ChainVerificationResult(false, chain.size(), current.hash,
                            "Block #" + current.blockIndex + " previousHash does not match Block #" + previous.blockIndex + " hash.");
                }
            } else {
                if (!current.previousHash.equals("0".repeat(64))) {
                    return new ChainVerificationResult(false, chain.size(), current.hash,
                            "Genesis block previousHash invalid.");
                }
            }
        }

        return new ChainVerificationResult(true, chain.size(), chain.get(chain.size() - 1).hash, null);
    }
}
