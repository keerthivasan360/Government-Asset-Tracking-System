param (
    [Parameter(Mandatory=$true)]
    [string]$MacAddress
)

$code = @"
using System;
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

public class Win32BluetoothChecker {
    [DllImport("bthprops.cpl", SetLastError = true)]
    public static extern IntPtr BluetoothFindFirstDevice(ref BLUETOOTH_DEVICE_SEARCH_PARAMS searchParams, ref BLUETOOTH_DEVICE_INFO deviceInfo);

    [DllImport("bthprops.cpl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool BluetoothFindNextDevice(IntPtr hFind, ref BLUETOOTH_DEVICE_INFO deviceInfo);

    [DllImport("bthprops.cpl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool BluetoothFindDeviceClose(IntPtr hFind);

    public static bool CheckConnection(string targetMac) {
        string cleanMac = targetMac.Replace(":", "").Replace("-", "").Trim().ToUpper();
        ulong targetAddress;
        if (!ulong.TryParse(cleanMac, System.Globalization.NumberStyles.HexNumber, null, out targetAddress)) {
            return false;
        }

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
                    if (dev.Address == targetAddress) {
                        return dev.fConnected;
                    }
                } while (BluetoothFindNextDevice(hFind, ref dev));
            } finally {
                BluetoothFindDeviceClose(hFind);
            }
        }
        return false;
    }
}
"@

if (-not ([System.Management.Automation.PSTypeName]'Win32BluetoothChecker').Type) {
    Add-Type -TypeDefinition $code
}

$isConnected = [Win32BluetoothChecker]::CheckConnection($MacAddress)

if ($isConnected) {
    Write-Output "CONNECTED"
} else {
    Write-Output "DISCONNECTED"
}

