package gov.assets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class TrackingService {
    private final AssetRepository assets;
    private final LogRepository logs;
    private final AlertRepository alerts;
    private final BlockchainService blockchain;

    public TrackingService(AssetRepository assets, LogRepository logs, AlertRepository alerts, BlockchainService blockchain) {
        this.assets = assets;
        this.logs = logs;
        this.alerts = alerts;
        this.blockchain = blockchain;
    }

    // Row locks serialize transitions through transaction commit.
    @Transactional
    public synchronized void transition(Long id, Asset.Status status, String source) {
        Asset asset = assets.findForUpdate(id).orElseThrow();
        if (asset.status == status) return;
        asset.status = status;
        assets.save(asset);

        TrackingLog log = new TrackingLog();
        log.assetId = id;
        log.assetName = asset.name;
        log.status = status;
        log.timestamp = Instant.now();
        log.source = source;
        logs.save(log);

        if (status == Asset.Status.DISCONNECTED && alerts.findByAssetIdAndResolvedFalse(id).isEmpty()) {
            Alert alert = new Alert();
            alert.assetId = id;
            alert.assetName = asset.name;
            alert.message = "Connection lost — out of range or unavailable. Verify the asset.";
            alert.createdAt = Instant.now();
            alerts.save(alert);
            blockchain.addBlock("STATUS_ALERT", id, asset.name, asset.currentLocation, asset.currentLocation, "monitor", "DISCONNECTED: Proximity lost", false);
            triggerWindowsNotification(asset.name, "Bluetooth connection lost — device out of range or turned off!");
        } else if (status == Asset.Status.CONNECTED) {
            for (Alert alert : alerts.findByAssetIdAndResolvedFalse(id)) {
                alert.resolved = true;
                alert.resolvedAt = Instant.now();
                alerts.save(alert);
            }
            blockchain.addBlock("STATUS_CHANGE", id, asset.name, asset.currentLocation, asset.currentLocation, "monitor", "CONNECTED: Proximity re-established", true);
        }
    }

    @Transactional
    public synchronized void delete(Long id) {
        Asset asset = assets.findForUpdate(id).orElseThrow();
        for (Alert alert : alerts.findByAssetIdAndResolvedFalse(id)) {
            alert.resolved = true;
            alert.resolvedAt = Instant.now();
            alerts.save(alert);
        }
        blockchain.addBlock("ASSET_DELETED", id, asset.name, asset.currentLocation, "DECOMMISSIONED", "admin", "Asset deregistered from tracking system", true);
        assets.deleteById(id);
    }

    @Transactional
    public synchronized Asset save(Asset input) {
        boolean isNew = input.id == null;
        Asset asset = isNew ? new Asset() : assets.findForUpdate(input.id).orElseThrow();
        if (asset.id != null && !asset.bluetoothMac.equalsIgnoreCase(input.bluetoothMac)) {
            throw new IllegalArgumentException("Register a new asset to change the tracked Bluetooth address.");
        }
        asset.name = input.name;
        asset.type = input.type;
        asset.serialNo = input.serialNo;
        asset.bluetoothMac = input.bluetoothMac.toUpperCase(java.util.Locale.ROOT);
        asset.department = input.department;
        asset.assignedTo = input.assignedTo;
        if (input.currentLocation != null && !input.currentLocation.isBlank()) {
            asset.currentLocation = input.currentLocation.trim();
        }
        if (input.allowedLocations != null && !input.allowedLocations.isBlank()) {
            asset.allowedLocations = input.allowedLocations.trim();
        }
        Asset saved = assets.save(asset);

        if (isNew) {
            blockchain.addBlock("ASSET_REGISTERED", saved.id, saved.name, "ORIGIN", saved.currentLocation, "admin",
                    "New asset registered. Permitted zones: [" + saved.allowedLocations + "]", true);
        } else {
            blockchain.addBlock("ASSET_UPDATED", saved.id, saved.name, saved.currentLocation, saved.currentLocation, "admin",
                    "Asset configuration updated. Permitted zones: [" + saved.allowedLocations + "]", true);
        }
        return saved;
    }

    public boolean isLocationAuthorized(Asset asset, String targetLocation) {
        if (asset.allowedLocations == null || asset.allowedLocations.isBlank()) return true;
        if (targetLocation == null || targetLocation.isBlank()) return false;
        String[] allowed = asset.allowedLocations.split("[,;]");
        for (String loc : allowed) {
            if (loc.trim().equalsIgnoreCase(targetLocation.trim())) {
                return true;
            }
        }
        return false;
    }

    public record ShiftResult(Asset asset, boolean authorized, boolean alertCreated, String message, Block block) {}

    @Transactional
    public synchronized ShiftResult shiftLocation(Long id, String toLocation, String operator) {
        Asset asset = assets.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        String oldLocation = asset.currentLocation != null ? asset.currentLocation : "Unknown";
        String target = toLocation != null && !toLocation.isBlank() ? toLocation.trim() : "Unknown Zone";
        boolean authorized = isLocationAuthorized(asset, target);

        asset.currentLocation = target;
        assets.save(asset);

        TrackingLog log = new TrackingLog();
        log.assetId = id;
        log.assetName = asset.name;
        log.status = asset.status;
        log.timestamp = Instant.now();
        log.source = (authorized ? "Relocation: " : "BREACH Relocation: ") + oldLocation + " -> " + target;
        logs.save(log);

        boolean alertCreated = false;
        String message;
        Block block;

        if (!authorized) {
            alertCreated = true;
            message = "SECURITY BREACH: Unauthorized asset movement! Shifted to unauthorized location '" + target +
                    "'! Authorized locations: [" + asset.allowedLocations + "].";

            Alert alert = new Alert();
            alert.assetId = id;
            alert.assetName = asset.name;
            alert.message = message;
            alert.createdAt = Instant.now();
            alerts.save(alert);

            block = blockchain.addBlock("SECURITY_BREACH_ALERT", id, asset.name, oldLocation, target, operator,
                    message, false);
        } else {
            message = "Authorized transfer: Asset shifted from '" + oldLocation + "' to '" + target + "'.";

            // If there were any active breach alerts for this asset and it is moved back to an authorized location, resolve them
            for (Alert alert : alerts.findByAssetIdAndResolvedFalse(id)) {
                if (alert.message != null && alert.message.contains("unauthorized location")) {
                    alert.resolved = true;
                    alert.resolvedAt = Instant.now();
                    alerts.save(alert);
                }
            }

            block = blockchain.addBlock("LOCATION_SHIFT", id, asset.name, oldLocation, target, operator,
                    message, true);
        }

        return new ShiftResult(asset, authorized, alertCreated, message, block);
    }

    private void triggerWindowsNotification(String assetName, String message) {
        try {
            java.io.File script = new java.io.File("scripts/notify-windows.ps1");
            if (script.exists()) {
                new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-File", script.getAbsolutePath(),
                        "-Title", "CRITICAL ALERT: " + assetName,
                        "-Message", message)
                        .start();
            }
        } catch (Exception ignored) {
        }
    }
}
