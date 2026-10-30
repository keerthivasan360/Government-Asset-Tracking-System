package gov.assets;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AssetRepository assets;
    private final AlertRepository alerts;
    private final LogRepository logs;
    private final TrackingService tracking;
    private final BlockRepository blockRepository;
    private final BlockchainService blockchainService;
    private final BluetoothMonitor bluetoothMonitor;

    @Value("${tracking.mode}") private String mode;

    public ApiController(AssetRepository assets, AlertRepository alerts, LogRepository logs,
                         TrackingService tracking, BlockRepository blockRepository,
                         BlockchainService blockchainService, BluetoothMonitor bluetoothMonitor) {
        this.assets = assets;
        this.alerts = alerts;
        this.logs = logs;
        this.tracking = tracking;
        this.blockRepository = blockRepository;
        this.blockchainService = blockchainService;
        this.bluetoothMonitor = bluetoothMonitor;
    }

    @GetMapping("/session")
    public Map<String, Object> session(Authentication authentication, CsrfToken csrf) {
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        return Map.of(
            "authenticated", authenticated,
            "username", authenticated ? authentication.getName() : "",
            "admin", authenticated && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
            "csrfToken", csrf.getToken(),
            "csrfHeader", csrf.getHeaderName()
        );
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return Map.of(
            "assets", assets.findAll(),
            "alerts", alerts.findTop100ByOrderByCreatedAtDesc(),
            "openAlerts", alerts.countByResolvedFalse(),
            "mode", mode,
            "totalBlocks", blockRepository.count()
        );
    }

    @GetMapping("/logs")
    public List<TrackingLog> logs() {
        return logs.findTop100ByOrderByTimestampDesc();
    }

    @GetMapping("/assets/{id}")
    public Asset asset(@PathVariable Long id) {
        return assets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found."));
    }

    // Explicit DTO prevents clients from writing observed status or choosing creation IDs.
    public record AssetInput(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String type,
        @NotBlank @Size(max = 100) String serialNo,
        @NotBlank @Pattern(regexp = "(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}", message = "Use a MAC address such as AA:BB:CC:DD:EE:FF") String bluetoothMac,
        @NotBlank @Size(max = 100) String department,
        @NotBlank @Size(max = 100) String assignedTo,
        @Size(max = 150) String currentLocation,
        @Size(max = 500) String allowedLocations
    ) {
        Asset toAsset(Long id) {
            Asset asset = new Asset();
            asset.id = id;
            asset.name = name;
            asset.type = type;
            asset.serialNo = serialNo;
            asset.bluetoothMac = bluetoothMac;
            asset.department = department;
            asset.assignedTo = assignedTo;
            asset.currentLocation = (currentLocation != null && !currentLocation.isBlank()) ? currentLocation : "HQ Main Facility";
            asset.allowedLocations = (allowedLocations != null && !allowedLocations.isBlank()) ? allowedLocations : "HQ Main Facility, Dispatch Office, Server Room";
            return asset;
        }
    }

    @PostMapping("/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody AssetInput input) {
        tracking.save(input.toAsset(null));
    }

    @PutMapping("/assets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable Long id, @Valid @RequestBody AssetInput input) {
        tracking.save(input.toAsset(id));
    }

    @DeleteMapping("/assets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tracking.delete(id);
    }

    @PostMapping("/assets/{id}/simulate/{status}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void simulate(@PathVariable Long id, @PathVariable Asset.Status status) {
        if (!mode.equals("demo") || status == Asset.Status.UNKNOWN)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulation requires demo mode and CONNECTED or DISCONNECTED.");
        tracking.transition(id, status, "Demo simulation");
    }

    public record ShiftInput(@NotBlank @Size(max = 150) String toLocation) {}

    @PostMapping("/assets/{id}/shift")
    public TrackingService.ShiftResult shift(@PathVariable Long id, @Valid @RequestBody ShiftInput input, Authentication auth) {
        String operator = auth != null ? auth.getName() : "operator";
        return tracking.shiftLocation(id, input.toLocation(), operator);
    }

    @GetMapping("/blockchain")
    public Map<String, Object> blockchain() {
        return Map.of(
            "blocks", blockRepository.findAllByOrderByBlockIndexDesc(),
            "verification", blockchainService.verifyChain()
        );
    }

    @GetMapping("/blockchain/verify")
    public BlockchainService.ChainVerificationResult verifyBlockchain() {
        return blockchainService.verifyChain();
    }

    @GetMapping("/facility-zones")
    public List<String> facilityZones() {
        return List.of(
            "HQ Main Facility",
            "Server Room B-4",
            "Dispatch Office",
            "Warehouse Area A",
            "Maintenance Lab",
            "Executive Briefing Room",
            "Transit Sector 9",
            "Restricted Perimeter (Unauthorized)",
            "External Off-Site (Unauthorized)"
        );
    }

    @GetMapping("/bluetooth/devices")
    public List<BluetoothMonitor.DeviceScanDto> bluetoothDevices() {
        return bluetoothMonitor.scanAllDevices();
    }

    @PostMapping("/bluetooth/sync")
    public List<Asset> syncBluetoothDevices() {
        return bluetoothMonitor.syncAllPairedDevices();
    }
}
