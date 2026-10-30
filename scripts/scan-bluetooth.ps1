$code = @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;

[StructLayout(LayoutKind.Sequential)]
public struct SYSTEMTIME {
    public ushort wYear, wMonth, wDayOfWeek, wDay, wHour, wMinute, wSecond, wMilliseconds;
}

[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public struct BLUETOOTH_DEVICE_INFO {
    public int dwSize;
    public ulong Address;
    public uint ulClassofDevice;
    [MarshalAs(UnmanagedType.Bool)]
    public bool fConnected;
    [MarshalAs(UnmanagedType.Bool)]
    public bool fRemembered;
    [MarshalAs(UnmanagedType.Bool)]
    public bool fAuthenticated;
    public SYSTEMTIME stLastSeen;
    public SYSTEMTIME stLastUsed;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 248)]
    public string szName;
}

[StructLayout(LayoutKind.Sequential)]
public struct BLUETOOTH_DEVICE_SEARCH_PARAMS {
    public int dwSize;
    [MarshalAs(UnmanagedType.Bool)]
    public bool fReturnAuthenticated;
    [MarshalAs(UnmanagedType.Bool)]
    public bool fReturnRemembered;
    [MarshalAs(UnmanagedType.Bool)]
    public bool fReturnUnknown;
    [MarshalAs(UnmanagedType.Bool)]
    public bool fReturnConnected;
    [MarshalAs(UnmanagedType.Bool)]
    public bool fIssueInquiry;
    public byte cTimeoutMultiplier;
    public IntPtr hRadio;
}

public class DeviceDto {
    public string Name { get; set; }
    public string Mac { get; set; }
    public bool Connected { get; set; }
    public bool Remembered { get; set; }
    public uint DeviceClass { get; set; }
}

public class Win32BluetoothScanner {
    [DllImport("bthprops.cpl", SetLastError = true)]
    public static extern IntPtr BluetoothFindFirstDevice(ref BLUETOOTH_DEVICE_SEARCH_PARAMS searchParams, ref BLUETOOTH_DEVICE_INFO deviceInfo);

    [DllImport("bthprops.cpl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool BluetoothFindNextDevice(IntPtr hFind, ref BLUETOOTH_DEVICE_INFO deviceInfo);

    [DllImport("bthprops.cpl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool BluetoothFindDeviceClose(IntPtr hFind);

    public static List<DeviceDto> Scan() {
        List<DeviceDto> list = new List<DeviceDto>();

        BLUETOOTH_DEVICE_SEARCH_PARAMS sp = new BLUETOOTH_DEVICE_SEARCH_PARAMS();
        sp.dwSize = Marshal.SizeOf(typeof(BLUETOOTH_DEVICE_SEARCH_PARAMS));
        sp.fReturnAuthenticated = true;
        sp.fReturnRemembered = true;
        sp.fReturnUnknown = true;
        sp.fReturnConnected = true;
        sp.fIssueInquiry = false;
        sp.cTimeoutMultiplier = 2;
        sp.hRadio = IntPtr.Zero;

        BLUETOOTH_DEVICE_INFO dev = new BLUETOOTH_DEVICE_INFO();
        dev.dwSize = Marshal.SizeOf(typeof(BLUETOOTH_DEVICE_INFO));

        IntPtr hFind = BluetoothFindFirstDevice(ref sp, ref dev);
        if (hFind != IntPtr.Zero) {
            try {
                do {
                    string rawMac = string.Format("{0:X12}", dev.Address);
                    string mac = string.Format("{0}:{1}:{2}:{3}:{4}:{5}",
                        rawMac.Substring(0, 2), rawMac.Substring(2, 2), rawMac.Substring(4, 2),
                        rawMac.Substring(6, 2), rawMac.Substring(8, 2), rawMac.Substring(10, 2));

                    string name = string.IsNullOrWhiteSpace(dev.szName) ? ("Device-" + mac) : dev.szName;

                    DeviceDto dto = new DeviceDto();
                    dto.Name = name;
                    dto.Mac = mac;
                    dto.Connected = dev.fConnected;
                    dto.Remembered = dev.fRemembered;
                    dto.DeviceClass = dev.ulClassofDevice;
                    list.Add(dto);
                } while (BluetoothFindNextDevice(hFind, ref dev));
            } finally {
                BluetoothFindDeviceClose(hFind);
            }
        }
        return list;
    }
}
"@

if (-not ([System.Management.Automation.PSTypeName]'Win32BluetoothScanner').Type) {
    Add-Type -TypeDefinition $code
}

$devices = [Win32BluetoothScanner]::Scan()
$devices | ConvertTo-Json -Compress

