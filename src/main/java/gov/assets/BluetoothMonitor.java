package gov.assets;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

@Component
public class BluetoothMonitor {
    private static final Logger log = LoggerFactory.getLogger(BluetoothMonitor.class);
    private final AssetRepository assets;
    private final TrackingService tracking;
    private final ObjectMapper mapper = new ObjectMapper();
    @Value("${tracking.mode}") private String mode;

    public static class DeviceScanDto {
        @JsonProperty("Name")
        public String name;
        @JsonProperty("Mac")
        public String mac;
        @JsonProperty("Connected")
        public boolean connected;
        @JsonProperty("Remembered")
        public boolean remembered;
        @JsonProperty("DeviceClass")
        public Long deviceClass;
    }

    public BluetoothMonitor(AssetRepository assets, TrackingService tracking) {
        this.assets = assets;
        this.tracking = tracking;
    }

    @Scheduled(fixedDelayString = "${tracking.interval-ms}")
    public void poll() {
        if (mode.equals("linux")) {
            pollLinux();
        } else if (mode.equals("windows")) {
            pollWindows();
        }
        // demo mode: no polling — simulation UI drives state changes
    }

    // ── Scan all Bluetooth devices on Windows host (Win32 API) ─────────────
    public List<DeviceScanDto> scanAllDevices() {
        File script = new File("scripts/scan-bluetooth.ps1");
        if (!script.exists()) {
            return Collections.emptyList();
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", script.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.warn("Bluetooth device scan timed out");
                return Collections.emptyList();
            }
            String json = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                return mapper.readValue(json, new TypeReference<List<DeviceScanDto>>() {});
            }
        } catch (Exception e) {
            log.warn("Error scanning Bluetooth devices: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    // ── Sync/Import all paired or detected devices into Asset registry ──────
    public List<Asset> syncAllPairedDevices() {
        List<DeviceScanDto> scanned = scanAllDevices();
        List<Asset> added = new ArrayList<>();
        for (DeviceScanDto dev : scanned) {
            if (dev.mac == null || dev.mac.isBlank()) continue;
            String cleanMac = dev.mac.replace(":", "").replace("-", "").toUpperCase();
            boolean exists = assets.findAll().stream().anyMatch(a ->
                    a.bluetoothMac != null && a.bluetoothMac.replace(":", "").replace("-", "").equalsIgnoreCase(cleanMac));
            if (!exists) {
                Asset newAsset = new Asset();
                newAsset.name = (dev.name != null && !dev.name.isBlank()) ? dev.name : "Bluetooth Device (" + dev.mac + ")";
                newAsset.type = deriveDeviceType(dev.deviceClass, dev.name);
                newAsset.serialNo = "BT-" + cleanMac;
                newAsset.bluetoothMac = dev.mac;
                newAsset.department = "Connected Peripherals";
                newAsset.assignedTo = "Laptop Bluetooth Host";
                newAsset.currentLocation = "Laptop Workstation";
                newAsset.allowedLocations = "Laptop Workstation, Office, Desk";
                newAsset.status = dev.connected ? Asset.Status.CONNECTED : Asset.Status.DISCONNECTED;
                tracking.save(newAsset);
                added.add(newAsset);
                log.info("Synced Bluetooth device to asset registry: {} ({})", newAsset.name, newAsset.bluetoothMac);
            }
        }
        return added;
    }

    // ── Linux / BlueZ path ──────────────────────────────────────────────────
    private void pollLinux() {
        for (Asset asset : assets.findAll()) {
            try {
                Process p = new ProcessBuilder("bluetoothctl", "info", asset.bluetoothMac)
                        .redirectErrorStream(true).start();
                if (!p.waitFor(3, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException("Bluetooth probe timed out"); }
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (p.exitValue() != 0 || !out.contains("Connected:")) throw new IllegalStateException("No connection state returned");
                tracking.transition(asset.id, out.contains("Connected: yes") ? Asset.Status.CONNECTED : Asset.Status.DISCONNECTED, "Linux Bluetooth profile");
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) { Thread.currentThread().interrupt(); return; }
                log.warn("Linux probe failed for asset {}: {}. Last observation retained.", asset.id, ex.getMessage());
            }
        }
    }

    // ── Windows / Native Bluetooth Radio probe for ALL connected devices ────
    private void pollWindows() {
        List<DeviceScanDto> scanned = scanAllDevices();
        if (scanned.isEmpty()) {
            return;
        }

        Map<String, DeviceScanDto> scanMap = new HashMap<>();
        for (DeviceScanDto dev : scanned) {
            if (dev.mac != null) {
                scanMap.put(dev.mac.replace(":", "").replace("-", "").toUpperCase(), dev);
            }
        }

        // 1. Auto-discover: If ANY device is actively connected in Windows Bluetooth and not yet registered, auto-register it!
        for (DeviceScanDto dev : scanned) {
            if (dev.connected && dev.mac != null && !dev.mac.isBlank()) {
                String cleanMac = dev.mac.replace(":", "").replace("-", "").toUpperCase();
                boolean alreadyTracked = false;
                for (Asset a : assets.findAll()) {
                    if (a.bluetoothMac != null && a.bluetoothMac.replace(":", "").replace("-", "").equalsIgnoreCase(cleanMac)) {
                        alreadyTracked = true;
                        break;
                    }
                }
                if (!alreadyTracked) {
                    Asset newAsset = new Asset();
                    newAsset.name = (dev.name != null && !dev.name.isBlank()) ? dev.name : "Bluetooth Device (" + dev.mac + ")";
                    newAsset.type = deriveDeviceType(dev.deviceClass, dev.name);
                    newAsset.serialNo = "BT-" + cleanMac;
                    newAsset.bluetoothMac = dev.mac;
                    newAsset.department = "Connected Peripherals";
                    newAsset.assignedTo = "Laptop Bluetooth Host";
                    newAsset.currentLocation = "Laptop Workstation";
                    newAsset.allowedLocations = "Laptop Workstation, Office, Desk";
                    newAsset.status = Asset.Status.CONNECTED;
                    tracking.save(newAsset);
                    log.info("Auto-registered new connected Bluetooth device: {} ({})", newAsset.name, newAsset.bluetoothMac);
                }
            }
        }

        // 2. Poll all registered assets against live Bluetooth radio status
        for (Asset asset : assets.findAll()) {
            if (asset.bluetoothMac == null || asset.bluetoothMac.isBlank()) continue;
            String cleanMac = asset.bluetoothMac.replace(":", "").replace("-", "").toUpperCase();
            DeviceScanDto dev = scanMap.get(cleanMac);

            Asset.Status newStatus = (dev != null && dev.connected) ? Asset.Status.CONNECTED : Asset.Status.DISCONNECTED;
            tracking.transition(asset.id, newStatus, "Windows Bluetooth Live Radio");
        }
    }

    private String deriveDeviceType(Long deviceClass, String name) {
        String n = (name != null ? name.toLowerCase() : "");
        if (n.contains("phone") || n.contains("mobile") || n.contains("oneplus") || n.contains("galaxy") || n.contains("iphone")) {
            return "Smartphone";
        }
        if (n.contains("bud") || n.contains("airpod") || n.contains("headset") || n.contains("headphone") || n.contains("speaker") || n.contains("dopes") || n.contains("astra")) {
            return "Audio / Headset";
        }
        if (n.contains("mouse") || n.contains("keyboard") || n.contains("pen")) {
            return "Peripheral";
        }
        if (n.contains("watch") || n.contains("band")) {
            return "Wearable";
        }
        if (deviceClass != null) {
            long major = (deviceClass >> 8) & 0x1F;
            if (major == 2) return "Phone";
            if (major == 4) return "Audio / Video";
            if (major == 5) return "Peripheral";
            if (major == 7) return "Wearable";
        }
        return "Bluetooth Device";
    }
}

