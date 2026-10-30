from pathlib import Path
import subprocess, sys

work=Path(__file__).resolve().parent
src=(work/'create_project_document.py').read_text(encoding='utf-8')
def swap(old,new):
    global src
    if old not in src: raise ValueError('Missing expected source: '+old[:100])
    src=src.replace(old,new)

swap("p('Government Asset Tracking System',style='Title')", "p('AssetGuard Complete System Documentation',style='Title')")
swap("p('Project documentation and current implementation',style='Subtitle')", "p('Government asset tracking features architecture and operation',style='Subtitle')")
swap("box(25,65,410,180,'Tracked phone',['OS pairing and active profile','Windows PnP or Linux BlueZ'])", "box(25,65,410,180,'Bluetooth devices',['Phones headsets and peripherals','Windows Win32 or Linux BlueZ'])")
swap("p('We are building a web application that registers government equipment, records who is responsible for it, monitors a Bluetooth observation from a laptop, and raises alerts when connectivity is lost. The current implementation also supports manually simulated transfers between facility zones and records important asset events in a local cryptographic audit ledger.')", "p('AssetGuard is a local web application for registering equipment, assigning responsibility, observing Bluetooth connectivity, raising alerts and recording an audit history. It now discovers Windows Bluetooth devices, imports detected devices into the asset registry and automatically registers connected devices. Administrators can also simulate transfers between facility zones. A local SHA-256 hash chain records asset lifecycle and tracking events.')")
swap("The project contains the React sources, Maven configuration, backend services, test definitions and build scripts. The default database is H2 and the default monitoring mode is Windows. A MySQL profile and Linux probe are available. Critical correctness limitations remain in Windows range detection, alert categorization and the guarantees advertised by the audit ledger.", "The project contains React sources, Maven configuration, backend services, test definitions and build scripts. H2 is the default database and Windows is the default monitor mode. The Windows path uses a native API connection flag rather than the older PnP health check. Linux and demo modes remain available. Important gaps remain in scan error handling, import authorization, password reset behavior and audit guarantees.")
swap("('16','Implementation map and terminology')", "('16','Implementation map and terminology'),('17 to 18','Bluetooth discovery and import plus notification delivery')")
swap("('Simulate connection state in demo mode','Yes','No')", "('Simulate connection state in demo mode','Yes','No'),('Import Bluetooth devices','Shown in UI','API currently permits import')")
swap("The assignment in assignedTo is a free text officer name.", "Windows auto-discovery also registers connected devices without a user action. Import is shown only to admins, but POST /api/bluetooth/sync currently requires authentication rather than ADMIN. This is an authorization gap. The assignment in assignedTo is a free text officer name.")
swap("('OS integration','PowerShell and BlueZ','Get-PnpDevice on Windows bluetoothctl on Linux')", "('OS integration','PowerShell C sharp Win32 and BlueZ','bthprops.cpl P Invoke on Windows; bluetoothctl on Linux')")
swap("('Web presentation','HTML CSS JavaScript JSX SVG','Custom styles icons and browser APIs')", "('Web presentation','HTML CSS JavaScript JSX SVG','Custom styles icons Fetch Web Audio and Notifications APIs')")
swap("('Backend tests','Spring Boot Test and Security Test','JUnit assertions and MockMvc integration tests')", "('Backend tests','Spring Boot Test and Security Test','JUnit assertions and MockMvc integration tests'),('Windows alerts','WinRT and Windows Forms','Native toast with balloon fallback plus system sound')")
swap("('/ overview','View registered assets connected count open alert count and total ledger blocks. Search by identity department officer zone or status.')", "('/ overview','View registered assets connected count open alerts ledger count and a security banner. Search records and inspect discovered Bluetooth devices; admins see import controls.')")
swap("The shared useResource hook requests data immediately and polls every five seconds where enabled.", "The shared useResource hook requests data immediately. The dashboard summary polls every 1500 ms and discovered Bluetooth devices every 2000 ms. Other pages using the true polling flag default to 1500 ms.")
swap("The dashboard compares the current open alert count with its previous count. If the count rises and browser notification permission is granted, it displays a notification using the most recent unresolved alert. A fixed notification tag replaces the preceding popup. This is a foreground dashboard feature, not background push delivery; it requires the page and browser support. Movement alerts can also trigger the notification even though its title describes a Bluetooth alert.", "When the dashboard open alert count increases, it attempts a two tone Web Audio chime and, if permission is granted, a browser notification with the newest unresolved alert. A visible security banner appears while alerts remain open. Separately, the backend starts a Windows notification helper when it creates a connection loss alert. Browser and operating system settings affect delivery; these channels have different triggers and are described in Section 18.")
swap("The backend validates the MAC format and required values, then stores an UNKNOWN connection state.", "The manual create API validates the MAC format and required values, then stores an UNKNOWN connection state.")
swap("On a transition to DISCONNECTED, the service writes a tracking log. If the asset has no unresolved alert, it creates a connection alert and a STATUS_ALERT block.", "On a transition to DISCONNECTED, the service writes a tracking log. If the asset has no unresolved alert, it creates a connection alert, a STATUS_ALERT block and starts the Windows notification helper when its script exists.")
swap("('GET /api/facility-zones','List of predefined named zones for the simulator.')", "('GET /api/facility-zones','List of predefined named zones for the simulator.'),('GET /api/bluetooth/devices','Windows scan objects with Name Mac Connected Remembered and DeviceClass; scan failures currently return an empty list.')")
swap("With five second polling, this creates", "With 1500 ms polling on the explorer, this creates")
swap("p('The endpoints below require the ADMIN role. Every request requires a session and a valid CSRF header. Asset bodies use an explicit input record, so callers cannot assign observed status or choose a creation identifier.')", "p('Asset mutations require ADMIN and a valid session CSRF token. The Bluetooth sync endpoint is an exception: the current security rules permit any authenticated user, even though the interface shows import controls only to admins. Asset DTOs prevent clients from assigning observed status or choosing creation identifiers.')")
swap("('POST /api/assets/{id}/shift','JSON with toLocation','200 JSON with asset authorized alertCreated message and block')", "('POST /api/assets/{id}/shift','JSON with toLocation','200 JSON with asset authorized alertCreated message and block'),('POST /api/bluetooth/sync','No body','200 JSON array of newly imported devices; current access is authenticated')")
start=src.index("page('10 Bluetooth monitoring implementation')")
end=src.index("page('11 Local cryptographic audit ledger')")
src=src[:start]+'''page('10 Bluetooth monitoring implementation')
table(['Mode','Probe and current behavior'],[
('windows default','Runs scripts/scan-bluetooth.ps1 through PowerShell. Embedded C sharp calls BluetoothFindFirstDevice and BluetoothFindNextDevice in bthprops.cpl. The scanner returns the native fConnected value along with identity and class data.'),
('linux','Runs bluetoothctl info for each registered MAC. A successful response with Connected yes becomes CONNECTED; another valid Connected value becomes DISCONNECTED.'),
('demo','Disables scheduled OS polling. Admin controls submit manual connection transitions. The Windows discovery endpoint is not disabled by demo mode and still invokes its platform script.')],[1.25,5.58],size=9.2)
h('Timing and scope')
p('The scheduler waits 2000 ms after each monitoring batch. A Windows batch performs one full host scan with a ten second timeout, imports newly connected devices and compares registered MACs with the scan. Linux probes run sequentially with a three second timeout per asset. Browser summary polling is separate at 1500 ms. These are scheduling settings, not a guaranteed detection latency.')
h('Windows observations')
p('A nonempty Windows scan becomes a MAC lookup map. Known records matching a connected device become CONNECTED; registered records absent from that nonempty map become DISCONNECTED. The scanner asks for connected, remembered, authenticated and unknown device records but sets fIssueInquiry to false. It enumerates records known to Windows; it does not actively search for every nearby radio or calculate distance.')
h('Empty results and stale states')
p('A missing script, timeout, malformed output or caught exception returns an empty device list. The Windows monitor immediately returns for an empty list, preserving all previous asset states. Consequently a genuine zero device result is indistinguishable from a failed scan and can leave the last device marked CONNECTED. A one device PowerShell result can also be serialized as a JSON object, while Java currently accepts only a JSON array. Check exit codes, output shape and scan health explicitly.')
h('Hardware and deployment boundaries')
p('A native connected flag is stronger evidence than the earlier PnP health value, but it still represents an OS connection observation rather than verified room or GPS location. Test the actual phone, active profile, radio off behavior and OS update latency. The external scanner script is resolved relative to the process working directory and is not included by the Maven resource configuration; deploy scripts alongside the JAR or configure an explicit path.')
p('Linux exceptions preserve the last state. Monitoring does not measure signal strength or meters. Nearby unpaired devices and Bluetooth Low Energy advertisements are not comprehensively discovered by this classic Windows enumeration path.')

''' + src[end:]
swap("Spring Security loads usernames, BCrypt password hashes and roles from the users repository. Bootstrap initializes admin and officer accounts only when absent. ADMIN_PASSWORD and OFFICER_PASSWORD are initialization inputs, not password reset mechanisms. If an input is blank, a random initial password is printed once to the startup log and its hash is saved. Credentials should be kept outside documentation and source control.", "Spring Security loads usernames, BCrypt password hashes and roles from the users repository. Bootstrap now upserts admin and officer on every startup and replaces their password hashes. ADMIN_PASSWORD and OFFICER_PASSWORD override the password; when blank, each password defaults to its username. Restarting without overrides therefore resets these built in accounts to weak predictable credentials. Configure strong secrets and change this initializer before shared deployment. Existing account roles are not reset by the upsert.")
swap("Officer access is read only across all departments.", "Officer asset management is intended to be read only across all departments, but the Bluetooth sync mutation currently remains accessible to any authenticated account.")
swap("Browser notification privacy", "Notification privacy")
swap("Notifications are generated from dashboard polling and are not an authenticated background delivery channel.", "Browser notifications are generated from dashboard polling. Native desktop notifications are spawned by the backend on the monitoring laptop, so they may also appear without an open dashboard.")
swap("('tracking.interval-ms','Backend monitor fixed delay; currently 5000')", "('tracking.interval-ms','Backend monitor fixed delay; currently 2000')")
swap("('ADMIN_PASSWORD and OFFICER_PASSWORD','Initial passwords for accounts not already stored')", "('ADMIN_PASSWORD and OFFICER_PASSWORD','Passwords reapplied to built in accounts at every startup')")
swap("p('The application is accessed at http://localhost:8080. To control dependency and build stages manually:')", "p('Run from the project root so scripts/scan-bluetooth.ps1 and scripts/notify-windows.ps1 can be found. The JAR alone does not contain those helpers. The interface is available at http://localhost:8080. To build manually:')")
swap("p('A register-phone.ps1 helper exists but embeds a particular device and a fixed login assumption. Parameterize the helper before reuse. Do not copy real device identifiers or credentials into shared project reports.')", "p('The scripts folder includes the runtime scanner and desktop notifier plus registration, API and Bluetooth diagnostic helpers. Several helpers embed a specific device or login assumption; parameterize them before reuse. Do not stop all Java processes just to restart this application: identify its process specifically. Do not publish real device identifiers or credentials in reports.')")
swap("Windows hardware accuracy, reliable notification delivery", "Windows scan output shapes, zero device scans, import authorization, account reset behavior, hardware accuracy, and notification delivery")
swap("MySQL connectivity, Windows scan output shapes, zero device scans, import authorization, account reset behavior, hardware accuracy, and notification delivery and deployment beyond localhost need environment specific acceptance testing.", "MySQL connectivity, zero and single device scans, import authorization, account reset behavior, hardware accuracy, notification delivery and deployment beyond localhost need environment specific acceptance testing.")
swap("('High','Windows PnP health is classified as a live connection. PowerShell error output can also become DISCONNECTED.','Use a validated device connection API or heartbeat. Check exit codes and represent probe failure separately.')", "('High','Empty or single device scans can preserve stale states. Missing scripts and scan failures share an empty response.','Return explicit probe health support zero one and many devices check exit codes and test OS behavior.'),('High','Bluetooth sync permits authenticated officers; startup resets passwords to predictable defaults without overrides.','Enforce ADMIN on sync and change initialization to preserve stored passwords or require explicit reset.'),('Medium','Import sets input status but save does not copy it. A deleted connected device is auto registered again.','Route initial observations through the transition service and define excluded device or decommissioning rules.')")
swap("('Medium','Probe health is not visible. Full ledger retrieval and verification repeat every five seconds.','Add last observation timestamps health indicators pagination and incremental refresh.')", "('Medium','Probe health is not visible. Ledger retrieval and verification repeat every 1500 ms; UI and scheduler may launch concurrent scans.','Add health indicators cache scan results paginate blocks and separate automatic polling from full audit work.')")
swap("('scripts/build.ps1 and register-phone.ps1','Build orchestration and device specific registration helper')", "('scripts/build.ps1 and register-phone.ps1','Build orchestration and device specific registration helper'),('scripts/scan-bluetooth.ps1','Embedded C sharp Win32 device enumeration and JSON output'),('scripts/notify-windows.ps1 and diagnostic helpers','Native toast sound balloon fallback and OS or API investigation')")
swap("'explicit_pages':17", "'explicit_pages':19")
swap("Government_Asset_Tracking_System_Project_Documentation.docx", "AssetGuard_Complete_System_Documentation.docx")
# Additional detailed chapters make the newer features explicit rather than hiding them in a change list.
anchor="dest=OUT/'AssetGuard_Complete_System_Documentation.docx'"
extra='''page('17 Bluetooth discovery and registry import')
h('Discover devices on the monitoring laptop')
p('The dashboard calls GET /api/bluetooth/devices every two seconds. BluetoothMonitor launches scripts/scan-bluetooth.ps1 with PowerShell in noninteractive mode. The script compiles embedded C sharp with Add-Type and uses P Invoke against bthprops.cpl. It closes the enumeration handle in a finally block and emits JSON for Java Jackson parsing.')
table(['Scan field','Meaning'],[('Name','Name known to the Windows Bluetooth stack'),('Mac','Six byte hardware address formatted with colons'),('Connected','Native BLUETOOTH_DEVICE_INFO fConnected flag'),('Remembered','Whether Windows remembers the device'),('DeviceClass','Bluetooth class of device numeric value')],[1.5,5.33])
h('Import and automatic registration')
p('Sync all to registry calls POST /api/bluetooth/sync. The service normalizes scanned and registered MACs by removing separators and compares them case insensitively. A matching asset is skipped; an unmatched device is registered. This import includes disconnected remembered devices returned by the scan, not just connected devices. The per row Track control calls the same bulk sync operation rather than importing only the selected row.')
p('In Windows monitoring mode, the scheduler also auto registers any connected scanned device that is not in the registry. It then submits connection observations for all registered assets. Type inference uses device name keywords and the major Bluetooth device class, with categories such as smartphone, audio or headset, peripheral, wearable and generic Bluetooth device.')
table(['Generated field','Import value'],[('serialNo','BT followed by the normalized MAC'),('department','Connected Peripherals'),('assignedTo','Laptop Bluetooth Host'),('currentLocation','Laptop Workstation'),('allowedLocations','Laptop Workstation Office and Desk')],[1.6,5.23])
h('Import edge cases and authority')
p('The import path assigns status on its input object, but TrackingService.save creates a fresh entity and does not copy that field. Manual sync records therefore initially remain UNKNOWN until an observation is processed; auto discovery is followed by a monitoring pass. Import bypasses the REST creation DTO, although entity constraints and database rules still apply. Deduplication repeatedly loads the registry and concurrent import requests can race. New registrations append ordinary ASSET_REGISTERED ledger blocks with a hard coded admin operator label.')

page('18 Alert delivery and notification behavior')
h('Stored alerts and visible warning banner')
p('Alerts persist in the database and feed the summary open alert count. The dashboard displays a warning banner while the count is positive, showing up to two unresolved messages from the recent alert list. The list below it includes recent open and resolved records. An initial disconnected observation can create an alert; identical status observations do not create duplicate transition logs.')
h('Browser sound and notification')
p('When the open count rises, playAlarmChime creates a Web Audio context and generates two sawtooth tones, approximately 880 Hz for 0.15 seconds and 587 Hz for 0.35 seconds. Browser autoplay policy may prevent sound until user interaction. If Notifications permission is granted, the dashboard also sends a popup using the newest unresolved alert. The same tag replaces the preceding popup. The login flow requests permission when the browser still reports its default state.')
p('The counter starts at zero on dashboard mount, so existing open alerts can trigger a notification on first load. A new alert can be missed when another alert resolves between polls and the total count does not increase. Movement alerts can trigger the same channel. These are count based alerts rather than guaranteed per event deliveries, and they require the dashboard to remain mounted.')
h('Native Windows desktop channel')
p('When TrackingService creates a new disconnection alert, it starts scripts/notify-windows.ps1 if the script exists. The helper plays the system Exclamation sound and attempts a Windows Runtime toast. If toast creation fails, it tries a Windows Forms NotifyIcon balloon and then disposes it. The service does not wait for delivery or record success, and it ignores startup exceptions. Desktop policy, user session state and script availability can prevent a popup.')
p('This native path is attached to connection alert creation only, not directly to movement breach creation. It is invoked inside the database operation before the transaction commits, so a notification is not proof that the transaction ultimately succeeded. The helper is not explicitly gated by tracking mode or OS; on an unsupported host process startup can fail silently.')
h('Operational expectations')
p('Dashboard and native channels can both fire for one connection loss, producing two sounds or popups. Notification text can expose asset details on the laptop desktop. XML text in the native toast is assembled from unescaped title and message strings, so special characters in an asset name can break toast parsing and invoke the fallback. Treat all delivery as best effort until tested. There is no email SMS background web push delivery receipt or acknowledgement workflow.')

'''
swap(anchor,extra+anchor)
generated=work/'create_current_document.py'
generated.write_text(src,encoding='utf-8')
subprocess.run([sys.executable,str(generated)],check=True)

# Produce a portable editable Markdown source alongside the checked PDF.
from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph
doc=Document(work.parent/'AssetGuard_Complete_System_Documentation.docx')
lines=[]
in_code=False
for b in doc.iter_inner_content():
    if isinstance(b,Paragraph):
        if b._p.xpath('.//a:blip'):
            lines.append('Diagram: Browser React -> Spring Boot REST API -> TrackingService -> H2 or MySQL. Windows Win32 or Linux BlueZ -> BluetoothMonitor -> TrackingService.')
            continue
        if not b.text.strip(): continue
        if b.runs and b.runs[0].font.name=='Consolas':
            if not in_code: lines.append('```text'); in_code=True
            lines.append(b.text)
            continue
        if in_code: lines.extend(['```','']); in_code=False
        prefix={'Title':'# ','Heading 1':'## ','Heading 2':'### ','List Bullet':'- '}.get(b.style.name,'')
        lines.extend([prefix+b.text,''])
    elif isinstance(b,Table):
        if in_code: lines.extend(['```','']); in_code=False
        rows=[[c.text.replace('|','\\|').replace('\n',' ') for c in row.cells] for row in b.rows]
        lines.append('| '+' | '.join(rows[0])+' |')
        lines.append('| '+' | '.join(['---']*len(rows[0]))+' |')
        lines.extend('| '+' | '.join(row)+' |' for row in rows[1:]); lines.append('')
if in_code: lines.extend(['```',''])
md=work.parents[1]/'output/AssetGuard_Complete_System_Documentation.md';md.parent.mkdir(exist_ok=True)
md.write_text('\n'.join(lines),encoding='utf-8')

pdfsrc=(work/'create_project_pdf.py').read_text(encoding='utf-8')
pdfsrc=pdfsrc.replace('Government_Asset_Tracking_System_Project_Documentation','AssetGuard_Complete_System_Documentation')
pdfsrc=pdfsrc.replace("'pdf_review'","'current_pdf_review'")
pdfscript=work/'create_current_pdf.py';pdfscript.write_text(pdfsrc,encoding='utf-8')
subprocess.run([sys.executable,str(pdfscript)],check=True)
print('Editable documentation: '+str(md))
