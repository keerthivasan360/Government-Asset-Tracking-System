package gov.assets;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name="assets")
public class Asset {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @NotBlank @Size(max=100) public String name;
    @NotBlank @Size(max=50) public String type;
    @NotBlank @Size(max=100) @Column(unique=true) public String serialNo;
    @NotBlank @Pattern(regexp="(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}", message="Use a MAC address such as AA:BB:CC:DD:EE:FF") @Column(unique=true) public String bluetoothMac;
    @NotBlank @Size(max=100) public String department;
    @NotBlank @Size(max=100) public String assignedTo;
    @Size(max=150) public String currentLocation = "HQ Main Facility";
    @Size(max=500) public String allowedLocations = "HQ Main Facility, Dispatch Office, Server Room";
    @Enumerated(EnumType.STRING) public Status status=Status.UNKNOWN;
    public Long getId(){return id;} public String getName(){return name;} public String getType(){return type;}
    public String getSerialNo(){return serialNo;} public String getBluetoothMac(){return bluetoothMac;}
    public String getDepartment(){return department;} public String getAssignedTo(){return assignedTo;}
    public String getCurrentLocation(){return currentLocation;}
    public String getAllowedLocations(){return allowedLocations;}
    public Status getStatus(){return status;}
    public enum Status { UNKNOWN, CONNECTED, DISCONNECTED }
}
