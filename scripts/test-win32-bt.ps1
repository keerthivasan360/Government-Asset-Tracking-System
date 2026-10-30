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

public class Win32Bluetooth {
    [DllImport("bthprops.cpl", SetLastError = true)]
    public static extern IntPtr BluetoothFindFirstDevice(ref BLUETOOTH_DEVICE_SEARCH_PARAMS searchParams, ref BLUETOOTH_DEVICE_INFO deviceInfo);

    [DllImport("bthprops.cpl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool BluetoothFindNextDevice(IntPtr hFind, ref BLUETOOTH_DEVICE_INFO deviceInfo);

    [DllImport("bthprops.cpl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool BluetoothFindDeviceClose(IntPtr hFind);

    public static void QueryDevices() {
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
                    string mac = string.Format("{0:X12}", dev.Address);
                    // Format MAC as XX:XX:XX:XX:XX:XX
                    string formattedMac = string.Format("{0}:{1}:{2}:{3}:{4}:{5}",
                        mac.Substring(0, 2), mac.Substring(2, 2), mac.Substring(4, 2),
                        mac.Substring(6, 2), mac.Substring(8, 2), mac.Substring(10, 2));

                    Console.WriteLine("DEVICE: " + dev.szName + " | MAC: " + formattedMac + " | Connected: " + dev.fConnected + " | Remembered: " + dev.fRemembered);
                } while (BluetoothFindNextDevice(hFind, ref dev));
            } finally {
                BluetoothFindDeviceClose(hFind);
            }
        } else {
            Console.WriteLine("No Bluetooth devices found or error: " + Marshal.GetLastWin32Error());
        }
    }

    public static bool IsDeviceConnected(string targetMac) {
        string cleanMac = targetMac.Replace(":", "").Replace("-", "").Trim().ToUpper();
        ulong targetAddress = Convert.ToUInt64(cleanMac, 16);

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

Add-Type -TypeDefinition $code
[Win32Bluetooth]::QueryDevices()
Write-Host "Target 7C:F0:E5:4B:73:2C connected?: $([Win32Bluetooth]::IsDeviceConnected('7C:F0:E5:4B:73:2C'))"

