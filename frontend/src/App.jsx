import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  ShieldCheck, LayoutDashboard, Package, Bell, History, LogOut, Plus, Search, Radio,
  ArrowLeft, Check, AlertTriangle, RefreshCw, MapPin, Move, Blocks, ShieldAlert, Lock
} from './icons';
import { getSession, request } from './api';

const Session = createContext(null);
const time = value => value ? new Date(value).toLocaleString() : '—';
const Status = ({ value }) => <span className={`status ${value}`}>{value}</span>;
const ErrorMessage = ({ children }) => children && <div className="error" role="alert">{children}</div>;
const Loading = () => <div className="quiet" role="status"><RefreshCw className="spin" size={22} /><p>Connecting to the monitoring station…</p></div>;

function playAlarmChime() {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const playTone = (freq, time, duration) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(freq, time);
      gain.gain.setValueAtTime(0.25, time);
      gain.gain.exponentialRampToValueAtTime(0.001, time + duration);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(time);
      osc.stop(time + duration);
    };
    playTone(880, ctx.currentTime, 0.15);
    playTone(587.33, ctx.currentTime + 0.18, 0.35);
  } catch(e) {}
}

function useResource(url, poll = false) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [version, setVersion] = useState(0);
  const [lastUpdated, setLastUpdated] = useState(null);
  useEffect(() => {
    let active = true, running = false;
    const load = async () => {
      if (running) return;
      running = true;
      try {
        const value = await request(url);
        if (active) {
          setData(value);
          setError('');
          setLastUpdated(new Date());
        }
      } catch (e) {
        if (active) setError(e.message);
      } finally {
        running = false;
      }
    };
    load();
    const pollInterval = typeof poll === 'number' ? poll : (poll ? 1500 : null);
    const interval = pollInterval ? setInterval(load, pollInterval) : null;
    return () => { active = false; if (interval) clearInterval(interval); };
  }, [url, poll, version]);
  return { data, error, reload: () => setVersion(v => v + 1), lastUpdated };
}

export default function App() {
  const [session, setSession] = useState(null);
  const [error, setError] = useState('');
  const load = () => getSession().then(s => { setSession(s); setError(''); }).catch(e => setError(e.message));
  useEffect(() => {
    load();
    const expired = () => setSession({ authenticated: false });
    window.addEventListener('session-expired', expired);
    return () => window.removeEventListener('session-expired', expired);
  }, []);
  if (!session) return <div className="startup">{error ? <><ErrorMessage>{error}</ErrorMessage><button className="button" onClick={load}>Retry connection</button></> : <Loading />}</div>;
  return <Session.Provider value={{ ...session, setSession }}><Routes>
    <Route path="/login" element={<Login />} />
    <Route element={<ProtectedLayout />}>
      <Route index element={<Dashboard />} />
      <Route path="simulation" element={<Simulation />} />
      <Route path="blockchain" element={<BlockchainExplorer />} />
      <Route path="reports" element={<Reports />} />
      <Route path="assets/new" element={<AssetForm />} />
      <Route path="assets/:id/edit" element={<AssetForm />} />
      <Route path="*" element={<div className="page-title"><div><h1>Page not found</h1><Link to="/" className="text-link">Return to overview</Link></div></div>} />
    </Route>
  </Routes></Session.Provider>;
}

function Login() {
  const session = useContext(Session);
  const location = useLocation();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  if (session.authenticated) return <Navigate to={location.state?.from || '/'} replace />;
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    const form = new FormData(event.currentTarget);
    try {
      await request('/api/login', { method: 'POST', body: new URLSearchParams({ username: form.get('username'), password: form.get('password') }) });
      // Request browser notification permission after successful login so we can
      // show real-time popups when Bluetooth disconnection alerts arrive.
      if ('Notification' in window && Notification.permission === 'default') {
        Notification.requestPermission();
      }
      session.setSession(await getSession());
    } catch (e) { setError(e.status === 401 ? 'Username or password is incorrect.' : e.message); }
    finally { setBusy(false); }
  }
  return <div className="login-body"><section className="login-card"><div className="brand"><ShieldCheck className="logo" size={34} /> AssetGuard</div><div className="eyebrow">GOVERNMENT ASSET TRACKING</div><h1>A clear view of<br />what matters.</h1><p>Sign in to your monitoring workspace.</p><ErrorMessage>{error}</ErrorMessage><form onSubmit={submit}><label>Username<input name="username" autoComplete="username" required autoFocus /></label><label>Password<input name="password" type="password" autoComplete="current-password" required /></label><button disabled={busy} className="button">{busy ? 'Signing in…' : 'Sign in →'}</button></form><small>Authorized government personnel only</small></section></div>;
}

function ProtectedLayout() {
  const session = useContext(Session);
  const location = useLocation();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (location.hash) document.getElementById(location.hash.slice(1))?.scrollIntoView({ behavior: 'smooth' }); else window.scrollTo(0, 0); }, [location.pathname, location.hash]);
  if (!session.authenticated) return <Navigate to="/login" state={{ from: location.pathname + location.hash }} replace />;
  async function logout() {
    setBusy(true); setError('');
    try { await request('/api/logout', { method: 'POST' }); session.setSession(await getSession()); }
    catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  return <><aside><Link to="/" className="brand"><ShieldCheck className="logo" size={31} /> AssetGuard</Link><div className="agency">GOVERNMENT OPERATIONS</div><nav>
    <NavLink end to="/"><LayoutDashboard /> Overview</NavLink>
    <NavLink to="/simulation"><Move /> Movement Simulator</NavLink>
    <NavLink to="/blockchain"><Blocks /> Blockchain Ledger</NavLink>
    <Link to="/#registry"><Package /> Asset registry</Link>
    <Link to="/#alerts"><Bell /> Alerts</Link>
    <NavLink to="/reports"><History /> Tracking history</NavLink>
  </nav><div className="aside-bottom"><span className="dot" /> Monitoring station<br /><small>Indoor asset protection & blockchain</small><button className="logout" disabled={busy} onClick={logout}><LogOut size={14} /> {busy ? 'Signing out…' : 'Sign out'}</button></div></aside><main><header><span>OPERATIONS / <strong>ASSET TRACKING & BLOCKCHAIN</strong></span><span className="user">● &nbsp; {session.username} <small>{session.admin ? 'ADMIN' : 'OFFICER'}</small></span></header><ErrorMessage>{error}</ErrorMessage><Outlet /><footer>AssetGuard / Government Asset Tracking & Blockchain Ledger <span>Connection status and location verified via cryptographic ledger.</span></footer></main></>;
}

function Dashboard() {
  const { admin } = useContext(Session);
  const { data, error, reload } = useResource('/api/summary', 1500);
  const { data: btDevices, reload: reloadBt } = useResource('/api/bluetooth/devices', 2000);
  const [search, setSearch] = useState('');
  const [actionError, setActionError] = useState('');
  const [busy, setBusy] = useState(null);
  const [syncingBt, setSyncingBt] = useState(false);

  // ── Bluetooth disconnect notifications & chime ────────────────────────────
  const prevOpenAlertsRef = useRef(0);
  useEffect(() => {
    if (!data) return;
    const current = data.openAlerts || 0;
    if (current > prevOpenAlertsRef.current) {
      playAlarmChime();
      if ('Notification' in window && Notification.permission === 'granted') {
        const newest = data.alerts.find(a => !a.resolved);
        new Notification('AssetGuard — Alert', {
          body: newest ? `${newest.assetName}: ${newest.message}` : `${current} open alert(s) require attention.`,
          icon: '/favicon.ico',
          tag: 'bt-disconnect'
        });
      }
    }
    prevOpenAlertsRef.current = current;
  }, [data?.openAlerts]);
  // ─────────────────────────────────────────────────────────────────────────

  async function syncBt() {
    setSyncingBt(true); setActionError('');
    try {
      await request('/api/bluetooth/sync', { method: 'POST' });
      reload();
      reloadBt();
    } catch (e) {
      setActionError(e.message);
    } finally {
      setSyncingBt(false);
    }
  }

  async function mutate(asset, kind) {
    if (kind === 'delete' && !window.confirm(`Delete “${asset.name}”? Its tracking history and blockchain records will be retained.`)) return;
    setBusy(asset.id); setActionError('');
    try {
      await request(kind === 'delete' ? `/api/assets/${asset.id}` : `/api/assets/${asset.id}/simulate/${asset.status === 'CONNECTED' ? 'DISCONNECTED' : 'CONNECTED'}`, { method: kind === 'delete' ? 'DELETE' : 'POST' });
      reload();
    } catch (e) { setActionError(e.message); }
    finally { setBusy(null); }
  }
  if (!data) return <><ErrorMessage>{error}</ErrorMessage>{error ? <button className="button" onClick={reload}>Retry</button> : <Loading />}</>;
  const visible = data.assets.filter(a => [a.name, a.type, a.serialNo, a.bluetoothMac, a.department, a.assignedTo, a.currentLocation, a.allowedLocations, a.status].join(' ').toLowerCase().includes(search.toLowerCase()));
  return <><div className="page-title"><div><div className="eyebrow">YOUR MONITORING WORKSPACE</div><h1>Asset overview</h1><p>Track locations, monitor connections, and audit immutable blockchain transactions.</p></div>{admin && <Link className="button" to="/assets/new"><Plus size={16} /> Register asset</Link>}</div>
    <div className="notice"><Radio size={28} /><div><strong>{data.mode} monitoring & local blockchain mode</strong><br />{data.openAlerts > 0 ? `${data.openAlerts} alert(s) active: Bluetooth connection lost or unauthorized asset movement detected.` : 'Bluetooth checks and immutable cryptographic ledger transactions run locally in real time.'}</div><span className="pill">{data.openAlerts > 0 ? `${data.openAlerts} OPEN ALERTS` : 'BLOCKCHAIN AUDITED'}</span></div>

    {data.openAlerts > 0 && (
      <div className="breach-banner">
        <AlertTriangle size={24} />
        <div>
          <strong>Security Notice: Bluetooth Connection Lost / Unauthorized Proximity Breach</strong>
          <p>{data.alerts.filter(a => !a.resolved).slice(0, 2).map(a => `${a.assetName}: ${a.message}`).join(' • ')}</p>
        </div>
        <span className="zone-tag restricted" style={{ marginLeft: 'auto' }}>{data.openAlerts} UNRESOLVED</span>
      </div>
    )}

    <ErrorMessage>{error ? `${error} Displaying the last received observations.` : actionError}</ErrorMessage>
    <section className="metrics">{[
      ['Registered assets', data.assets.length, 'Across all departments', ''],
      ['Connected', data.assets.filter(a => a.status === 'CONNECTED').length, 'Active proximity', 'green'],
      ['Open alerts', data.openAlerts, 'Awaiting review / resolution', data.openAlerts > 0 ? 'red' : ''],
      ['Blockchain ledger', data.totalBlocks || 1, 'Mined cryptographic blocks', 'green']
    ].map(([label, value, hint, color]) => <article key={label}><span>{label}</span><strong className={color}>{value}</strong><small>{hint}</small></article>)}</section>

    {btDevices && btDevices.length > 0 && <section className="panel"><div className="panel-head"><div><h2>Discovered Bluetooth devices <span className="count">{btDevices.length}</span></h2><p>Hardware detected on this PC. Active connections are monitored in real-time.</p></div>{admin && <button className="text-link" onClick={syncBt} disabled={syncingBt}><Radio size={14} /> {syncingBt ? 'Syncing…' : 'Sync all to registry'}</button>}</div><div className="table-wrap"><table><thead><tr><th>DEVICE NAME</th><th>MAC ADDRESS</th><th>STATUS</th><th>TRACKING</th></tr></thead><tbody>{btDevices.map(b => {
      const isTracked = data.assets.some(a => a.bluetoothMac && a.bluetoothMac.replace(/[:\-]/g,'').toLowerCase() === b.Mac.replace(/[:\-]/g,'').toLowerCase());
      return <tr key={b.Mac}><td><strong>{b.Name}</strong></td><td className="mono">{b.Mac}</td><td><Status value={b.Connected ? 'CONNECTED' : 'DISCONNECTED'} /></td><td>{isTracked ? <span className="pill">TRACKED</span> : admin && <button className="text-link" onClick={syncBt}>+ Track</button>}</td></tr>;
    })}</tbody></table></div></section>}

    <section className="panel" id="registry"><div className="panel-head"><div><h2>Asset registry <span className="count">{data.assets.length}</span></h2><p>Equipment, assigned locations and authorized movement zones.</p></div><div className="search-box"><Search size={15} /><input className="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search assets or zones…" aria-label="Search assets" /></div></div><div className="table-wrap"><table><thead><tr><th>ASSET / IDENTIFIERS</th><th>DEPARTMENT & OFFICER</th><th>CURRENT LOCATION & PERMITTED ZONES</th><th>STATUS</th>{admin && <th>ACTIONS</th>}</tr></thead><tbody>{visible.map(a => <tr key={a.id}><td><strong>{a.name}</strong><small>{a.type} · {a.serialNo}</small><small className="mono">{a.bluetoothMac}</small></td><td>{a.department}<small>{a.assignedTo}</small></td><td><div style={{display:'flex',alignItems:'center',gap:'5px',marginBottom:'4px'}}><MapPin size={13} style={{color:'#1e624f'}} /><strong>{a.currentLocation || 'HQ Main Facility'}</strong></div><small style={{lineHeight:'1.4'}}>Permitted: {a.allowedLocations || 'All'}</small></td><td><Status value={a.status} /></td>{admin && <td><div className="row-actions"><Link className="text-link" to="/simulation" state={{ selectAssetId: a.id }}><Move size={12} /> Shift</Link><Link className="text-link" to={`/assets/${a.id}/edit`}>Edit</Link>{data.mode === 'demo' && <button className="text-link" disabled={busy !== null} onClick={() => mutate(a, 'simulate')}>{a.status === 'CONNECTED' ? 'Disconnect' : 'Connect'}</button>}<button className="text-link red" disabled={busy !== null} onClick={() => mutate(a, 'delete')}>Delete</button></div></td>}</tr>)}{!visible.length && <tr><td colSpan={admin ? 5 : 4} className="empty"><Package className="empty-icon" size={32} /><h3>{search ? 'No matching assets' : 'Your asset registry starts here'}</h3><p>{search ? 'Try a different name, officer, or zone.' : 'Register an asset to configure its moveable places and monitor alerts.'}</p>{admin && !search && <Link to="/assets/new" className="text-link">Register your first asset →</Link>}</td></tr>}</tbody></table></div><div className="table-foot"><span>{visible.length} assets shown</span><span><span className="dot" /> Auto-updates in real-time</span></div></section>

    <section className="panel" id="alerts"><div className="panel-head"><div><h2>Recent security & proximity alerts</h2><p>Unauthorized relocations or connection changes · latest 100</p></div><span className="pill">{data.openAlerts} OPEN</span></div>{data.alerts.length ? data.alerts.map(a => <div className="alert-row" key={a.id}><span className="alert-icon">{a.resolved ? <Check size={17} /> : <AlertTriangle size={17} />}</span><div><strong>{a.assetName}</strong><p>{a.message}</p><small><time dateTime={a.createdAt}>{time(a.createdAt)}</time></small></div><Status value={a.resolved ? 'RESOLVED' : 'OPEN'} /></div>) : <div className="quiet"><Check className="green" size={26} /><strong>No alerts currently</strong><p>Disconnection and unauthorized movement alerts will appear here.</p></div>}</section></>;
}

const emptyAsset = {
  name: '', type: '', serialNo: '', bluetoothMac: '', department: '', assignedTo: '',
  currentLocation: 'HQ Main Facility',
  allowedLocations: 'HQ Main Facility, Dispatch Office, Server Room B-4'
};
const fields = [
  ['name', 'Asset name', 'e.g. Department mobile phone or secure laptop', 100],
  ['type', 'Asset type', 'Phone, Laptop, Specialized Hardware', 50],
  ['serialNo', 'Serial number', 'Unique manufacturer serial', 100],
  ['bluetoothMac', 'Bluetooth MAC address', 'AA:BB:CC:DD:EE:FF', 17],
  ['department', 'Department', 'e.g. Public Works or Security Operations', 100],
  ['assignedTo', 'Assigned officer', 'Responsible officer’s name', 100],
  ['currentLocation', 'Current location / facility zone', 'e.g. HQ Main Facility or Server Room B-4', 150],
  ['allowedLocations', 'Moveable / Permitted places (comma-separated)', 'e.g. HQ Main Facility, Dispatch Office, Server Room B-4', 500]
];

function AssetForm() {
  const { admin } = useContext(Session);
  const { id } = useParams();
  const navigate = useNavigate();
  const [asset, setAsset] = useState(emptyAsset);
  const [loading, setLoading] = useState(Boolean(id));
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    setError(''); setFieldErrors({}); setAsset(emptyAsset); setLoading(Boolean(id));
    if (id && admin) request(`/api/assets/${id}`).then(data => {
      if (active) setAsset(Object.fromEntries(fields.map(([key]) => [key, data[key] || ''])));
    }).catch(e => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, admin]);
  if (!admin) return <div className="page-title"><div><h1>Read-only access</h1><p>Only administrators can register or edit assets.</p><Link to="/" className="text-link">Return to overview</Link></div></div>;
  async function save(event) {
    event.preventDefault(); setBusy(true); setError(''); setFieldErrors({});
    try {
      await request(id ? `/api/assets/${id}` : '/api/assets', { method: id ? 'PUT' : 'POST', body: JSON.stringify(asset) });
      navigate('/');
    }
    catch (e) { setError(e.message); setFieldErrors(e.fields || {}); }
    finally { setBusy(false); }
  }
  return <><div className="page-title"><div><div className="eyebrow">ASSET REGISTRY & GEO-FENCING</div><h1>{id ? 'Edit asset' : 'Register asset'}</h1><p>Configure device identity, current station, and authorized moveable places.</p></div><Link to="/" className="text-link"><ArrowLeft size={14} /> Back to overview</Link></div><section className="panel form-panel"><ErrorMessage>{error}</ErrorMessage>{loading ? <Loading /> : <form onSubmit={save}><div className="form-grid">{fields.map(([key, label, placeholder, max]) => <label key={key} htmlFor={key}>{label}<input id={key} name={key} required={key !== 'currentLocation' && key !== 'allowedLocations'} maxLength={max} value={asset[key]} onChange={e => setAsset({ ...asset, [key]: e.target.value })} placeholder={placeholder} readOnly={Boolean(id && key === 'bluetoothMac')} pattern={key === 'bluetoothMac' ? '([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}' : undefined} aria-invalid={Boolean(fieldErrors[key])} aria-describedby={fieldErrors[key] ? `${key}-error` : undefined} />{fieldErrors[key] && <small className="red" id={`${key}-error`}>{fieldErrors[key]}</small>}{key === 'bluetoothMac' && <small>Use the paired device address. Address changes require a new record.</small>}{key === 'allowedLocations' && <small>Define comma-separated zones where this asset is allowed to move. Moving outside triggers an automated security alert.</small>}</label>)}</div><div className="form-actions"><Link to="/" className="text-link">Cancel</Link><button className="button" disabled={busy || Boolean(id && !asset.name)}>{busy ? 'Saving…' : 'Save asset'}</button></div></form>}</section></>;
}

function Simulation() {
  const { admin } = useContext(Session);
  const location = useLocation();
  const { data: summary, error, reload } = useResource('/api/summary', true);
  const { data: zones } = useResource('/api/facility-zones');
  const [selectedAssetId, setSelectedAssetId] = useState(location.state?.selectAssetId || null);
  const [customZone, setCustomZone] = useState('');
  const [feedback, setFeedback] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (summary?.assets?.length && !selectedAssetId) {
      setSelectedAssetId(summary.assets[0].id);
    }
  }, [summary, selectedAssetId]);

  const selectedAsset = summary?.assets?.find(a => a.id === Number(selectedAssetId));

  const isZoneAllowed = (asset, zoneName) => {
    if (!asset || !asset.allowedLocations) return true;
    const allowed = asset.allowedLocations.split(/[,;]/).map(s => s.trim().toLowerCase());
    return allowed.includes(zoneName.trim().toLowerCase());
  };

  async function handleShift(targetZone) {
    if (!selectedAsset) return;
    setBusy(true); setFeedback(null);
    try {
      const res = await request(`/api/assets/${selectedAsset.id}/shift`, {
        method: 'POST',
        body: JSON.stringify({ toLocation: targetZone })
      });
      setFeedback(res);
      reload();
    } catch (e) {
      setFeedback({ error: e.message });
    } finally {
      setBusy(false);
    }
  }

  if (!summary) return error ? <ErrorMessage>{error}</ErrorMessage> : <Loading />;

  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">INTERACTIVE ASSET RELOCATION & BLOCKCHAIN</div>
          <h1>Location Shift Simulator</h1>
          <p>Shift an asset between facility zones to test authorized relocations and unauthorized movement alerts.</p>
        </div>
        <Link to="/blockchain" className="secondary-button"><Blocks size={14} /> View Blockchain Ledger</Link>
      </div>

      {feedback && feedback.error && (
        <div className="error" role="alert">{feedback.error}</div>
      )}

      {feedback && feedback.alertCreated && (
        <div className="breach-banner" role="alert">
          <ShieldAlert size={28} />
          <div>
            <strong>SECURITY BREACH ALERT TRIGGERED!</strong>
            <p>{feedback.message}</p>
            <small style={{display:'block',marginTop:'6px',color:'#842c20',fontFamily:'monospace'}}>
              Mined to Blockchain Block #{feedback.block?.blockIndex} · Hash: {feedback.block?.hash}
            </small>
          </div>
        </div>
      )}

      {feedback && !feedback.alertCreated && !feedback.error && (
        <div className="success-banner" role="status">
          <Check size={28} />
          <div>
            <strong>Authorized Relocation Completed</strong>
            <p>{feedback.message}</p>
            <small style={{display:'block',marginTop:'6px',color:'#26614b',fontFamily:'monospace'}}>
              Recorded on Blockchain Block #{feedback.block?.blockIndex} · Hash: {feedback.block?.hash}
            </small>
          </div>
        </div>
      )}

      <div className="sim-layout">
        {/* Left column: Asset selection & details */}
        <div className="sim-card">
          <div className="sim-header">
            <h3><Package size={18} /> 1. Select Asset to Shift</h3>
            <span className="count">{summary.assets.length} Assets</span>
          </div>

          <div className="asset-selector">
            {summary.assets.map(a => (
              <div
                key={a.id}
                className={`asset-item-card ${selectedAssetId === a.id ? 'selected' : ''}`}
                onClick={() => { setSelectedAssetId(a.id); setFeedback(null); }}
              >
                <div className="asset-info">
                  <strong>{a.name}</strong>
                  <small>{a.type} · Serial: {a.serialNo}</small>
                  <div style={{marginTop:'6px',display:'flex',alignItems:'center',gap:'4px'}}>
                    <MapPin size={12} style={{color:'#1e624f'}} />
                    <span style={{fontSize:'11px',fontWeight:'600'}}>{a.currentLocation || 'HQ Main Facility'}</span>
                  </div>
                </div>
                <Status value={a.status} />
              </div>
            ))}
          </div>

          {selectedAsset && (
            <div style={{background:'#f6f9f6',borderRadius:'8px',padding:'16px',border:'1px solid #dce8df',marginTop:'18px'}}>
              <h4 style={{margin:'0 0 10px',fontSize:'13px',color:'#182c2b'}}>Active Asset Geo-Fence Rules</h4>
              <div style={{fontSize:'12px',marginBottom:'8px'}}>
                <span style={{color:'#67756f'}}>Current Location:</span> <strong>{selectedAsset.currentLocation || 'HQ Main Facility'}</strong>
              </div>
              <div style={{fontSize:'12px',lineHeight:'1.5'}}>
                <span style={{color:'#67756f'}}>Permitted / Moveable Places:</span>
                <div style={{marginTop:'6px'}}>
                  {(selectedAsset.allowedLocations || 'HQ Main Facility').split(/[,;]/).map((loc, idx) => (
                    <span key={idx} className="tag-pill" style={{background:'#e2f0e6',color:'#246445',border:'1px solid #c2e2cc'}}>
                      ✓ {loc.trim()}
                    </span>
                  ))}
                </div>
              </div>
              <p style={{fontSize:'11px',color:'#7d8a83',marginTop:'12px',fontStyle:'italic'}}>
                Moving this asset to any zone outside the permitted list will immediately trigger a critical security breach alert and commit a fraud block to the local blockchain.
              </p>
            </div>
          )}
        </div>

        {/* Right column: Target Destination Zones */}
        <div className="sim-card">
          <div className="sim-header">
            <h3><Move size={18} /> 2. Choose Destination Facility Zone</h3>
            <span className="pill">REAL-TIME SHIFT</span>
          </div>

          <p style={{fontSize:'12px',color:'#6b7c74'}}>
            Click any zone below to simulate moving <strong>{selectedAsset ? selectedAsset.name : 'the asset'}</strong>. Green zones are permitted; red zones are restricted and will trigger an alert!
          </p>

          <div className="zones-grid">
            {(zones || [
              'HQ Main Facility', 'Server Room B-4', 'Dispatch Office', 'Warehouse Area A',
              'Maintenance Lab', 'Executive Briefing Room', 'Transit Sector 9',
              'Restricted Perimeter (Unauthorized)', 'External Off-Site (Unauthorized)'
            ]).map(zone => {
              const isCurrent = selectedAsset?.currentLocation?.trim().toLowerCase() === zone.trim().toLowerCase();
              const allowed = selectedAsset ? isZoneAllowed(selectedAsset, zone) : true;
              return (
                <button
                  key={zone}
                  disabled={busy || isCurrent}
                  className={`zone-btn ${isCurrent ? 'current' : ''} ${!allowed ? 'unauthorized' : ''}`}
                  onClick={() => handleShift(zone)}
                >
                  <div className="zone-name">
                    <MapPin size={14} style={{color: !allowed ? '#b84133' : isCurrent ? '#1e624f' : '#6b7a73'}} />
                    {zone}
                  </div>
                  <div>
                    {isCurrent ? (
                      <span className="zone-tag" style={{background:'#cce6d6',color:'#18503b'}}>CURRENT STATION</span>
                    ) : allowed ? (
                      <span className="zone-tag allowed">PERMITTED MOVE</span>
                    ) : (
                      <span className="zone-tag restricted">RESTRICTED ZONE (BREACH)</span>
                    )}
                  </div>
                </button>
              );
            })}
          </div>

          <div style={{marginTop:'22px',paddingTop:'16px',borderTop:'1px solid var(--line)'}}>
            <h4 style={{margin:'0 0 8px',fontSize:'12px'}}>Or shift to a custom location:</h4>
            <div style={{display:'flex',gap:'8px'}}>
              <input
                placeholder="e.g. Loading Dock 3, Parking Lot B, Off-Site..."
                value={customZone}
                onChange={e => setCustomZone(e.target.value)}
                style={{margin:0}}
              />
              <button
                className="button"
                disabled={busy || !customZone.trim()}
                onClick={() => { handleShift(customZone.trim()); setCustomZone(''); }}
              >
                Shift Asset
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

function BlockchainExplorer() {
  const { data, error, reload } = useResource('/api/blockchain', true);
  const [verifying, setVerifying] = useState(false);
  const [auditMsg, setAuditMsg] = useState(null);

  async function handleVerify() {
    setVerifying(true); setAuditMsg(null);
    try {
      const res = await request('/api/blockchain/verify');
      setAuditMsg(res);
    } catch (e) {
      setAuditMsg({ valid: false, error: e.message });
    } finally {
      setVerifying(false);
    }
  }

  if (!data) return error ? <ErrorMessage>{error}</ErrorMessage> : <Loading />;

  const verification = auditMsg || data.verification || { valid: true, totalBlocks: data.blocks?.length || 0 };

  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">LOCAL CRYPTOGRAPHIC AUDIT TRAIL</div>
          <h1>Local Blockchain Ledger</h1>
          <p>Every asset registration, location transfer, and security breach is sealed in an immutable cryptographic block.</p>
        </div>
        <div style={{display:'flex',gap:'10px',alignItems:'center'}}>
          <button className="secondary-button" disabled={verifying} onClick={handleVerify}>
            <Lock size={14} /> {verifying ? 'Auditing…' : 'Cryptographic Audit'}
          </button>
          <button className="secondary-button" onClick={reload}><RefreshCw size={14} /> Refresh</button>
        </div>
      </div>

      <div style={{display:'flex',gap:'15px',alignItems:'center',marginBottom:'20px',flexWrap:'wrap'}}>
        <div className={`chain-badge ${verification.valid ? '' : 'invalid'}`}>
          <Lock size={15} />
          {verification.valid ? 'BLOCKCHAIN INTEGRITY: 100% VERIFIED' : 'BLOCKCHAIN COMPROMISED / TAMPER DETECTED'}
        </div>
        <span style={{fontSize:'12px',color:'#6b7a73'}}>
          Block Height: <strong>{data.blocks?.length || 0} Blocks</strong> · Algorithm: <strong>SHA-256 + Light PoW</strong>
        </span>
      </div>

      {auditMsg && (
        auditMsg.valid ? (
          <div className="success-banner" role="status">
            <Check size={24} />
            <div>
              <strong>Cryptographic Verification Passed</strong>
              <p>All {auditMsg.totalBlocks} blocks recalculate identical SHA-256 hashes and maintain valid cryptographic linkage back to Genesis.</p>
            </div>
          </div>
        ) : (
          <div className="breach-banner" role="alert">
            <ShieldAlert size={24} />
            <div>
              <strong>Audit Failure</strong>
              <p>{auditMsg.error}</p>
            </div>
          </div>
        )
      )}

      <section className="panel" style={{padding:'20px'}}>
        <div className="sim-header" style={{marginBottom:'14px'}}>
          <h2 style={{margin:0}}>Mined Blocks Stream</h2>
          <span className="count">{data.blocks?.length || 0} Blocks</span>
        </div>

        <div className="blockchain-list">
          {data.blocks && data.blocks.map(b => (
            <div className="block-card" key={b.id}>
              <div className="block-card-header">
                <div className="block-idx">
                  <Blocks size={16} /> Block #{b.blockIndex}
                </div>
                <span className={`block-action ${b.action}`}>{b.action}</span>
              </div>

              <div style={{fontSize:'13px',fontWeight:'600',color:'#182c2b',marginBottom:'6px'}}>
                {b.assetName} {b.assetId ? `(Asset #${b.assetId})` : ''}
              </div>

              <div style={{fontSize:'12px',color:'#5a6c65',marginBottom:'8px'}}>
                Path: <strong>{b.fromLocation}</strong> → <strong>{b.toLocation}</strong>
                <span style={{marginLeft:'12px',color: b.authorized ? '#287558' : '#b84133',fontWeight:'600'}}>
                  {b.authorized ? '✓ Authorized' : '⚠ Breach / Alert'}
                </span>
              </div>

              {b.details && (
                <div style={{fontSize:'11px',color:'#76857f',marginBottom:'8px',background:'#fcfdfb',padding:'6px 10px',borderRadius:'4px',border:'1px solid #edf2ed'}}>
                  {b.details}
                </div>
              )}

              <div className="block-hash">
                <span style={{color:'#889a93',marginRight:'6px'}}>HASH:</span>
                {b.hash}
              </div>
              <div className="block-hash" style={{fontSize:'10px',color:'#7f8b86'}}>
                <span style={{color:'#889a93',marginRight:'6px'}}>PREV:</span>
                {b.previousHash}
              </div>

              <div className="block-meta">
                <span>Timestamp: <strong><time dateTime={b.timestamp}>{time(b.timestamp)}</time></strong></span>
                <span>Mined Nonce: <strong>{b.nonce}</strong></span>
                <span>Signer / Operator: <strong>{b.operator}</strong></span>
              </div>
            </div>
          ))}
          {(!data.blocks || !data.blocks.length) && (
            <div className="quiet"><Blocks size={30} /><strong>No blocks recorded yet</strong><p>Register or move an asset to start the blockchain ledger.</p></div>
          )}
        </div>
      </section>
    </>
  );
}

function Reports() {
  const { data, error, reload } = useResource('/api/logs', true);
  return <><div className="page-title"><div><div className="eyebrow">AUDIT TRAIL</div><h1>Tracking history</h1><p>The latest 100 status changes and movements. Timestamps display in your local time.</p></div><button className="secondary-button" onClick={reload}><RefreshCw size={14} /> Refresh</button></div><ErrorMessage>{error}</ErrorMessage>{!data ? (error ? <button className="button" onClick={reload}>Retry</button> : <Loading />) : <section className="panel"><div className="table-wrap"><table><thead><tr><th>ASSET</th><th>OBSERVED STATUS</th><th>TIME</th><th>DETAILS / MOVEMENT</th></tr></thead><tbody>{data.map(l => <tr key={l.id}><td><strong>{l.assetName}</strong><small>Asset #{l.assetId}</small></td><td><Status value={l.status} /></td><td><time dateTime={l.timestamp}>{time(l.timestamp)}</time></td><td>{l.source}</td></tr>)}{!data.length && <tr><td colSpan="4" className="empty"><History className="empty-icon" size={30} /><h3>No status changes recorded yet</h3><p>Connect or shift an asset to start its audit trail.</p></td></tr>}</tbody></table></div></section>}</>;
}
