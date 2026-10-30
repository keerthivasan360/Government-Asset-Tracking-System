import React from 'react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from './App';
import { getSession, request } from './api';

vi.mock('./api', () => ({ getSession: vi.fn(), request: vi.fn() }));

const admin = { authenticated: true, username: 'admin', admin: true };
const asset = {
  id: 1, name: 'Demo phone', type: 'Phone', serialNo: 'SN1',
  bluetoothMac: 'AA:BB:CC:DD:EE:FF', department: 'IT', assignedTo: 'Officer',
  currentLocation: 'HQ Main Facility', allowedLocations: 'HQ Main Facility, Dispatch Office',
  status: 'CONNECTED'
};
const summary = { assets: [asset], alerts: [], openAlerts: 0, mode: 'demo', totalBlocks: 1 };
const blockchainData = {
  blocks: [
    { id: 1, blockIndex: 0, action: 'GENESIS', previousHash: '0'.repeat(64), hash: '0000abc123', timestamp: new Date().toISOString(), nonce: 42, operator: 'system', details: 'Genesis block', authorized: true }
  ],
  verification: { valid: true, totalBlocks: 1 }
};

function mount(path = '/') { return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>); }

beforeEach(() => {
  vi.resetAllMocks();
  window.scrollTo = vi.fn();
  getSession.mockResolvedValue(admin);
  request.mockImplementation(path => {
    if (path === '/api/summary') return Promise.resolve(summary);
    if (path === '/api/facility-zones') return Promise.resolve(['HQ Main Facility', 'Dispatch Office', 'Restricted Perimeter']);
    if (path === '/api/blockchain') return Promise.resolve(blockchainData);
    if (path === '/api/blockchain/verify') return Promise.resolve({ valid: true, totalBlocks: 1 });
    return Promise.resolve(summary);
  });
});

afterEach(() => { cleanup(); vi.useRealTimers(); });

describe('React monitoring workspace', () => {
  it('signs in and opens the protected workspace', async () => {
    getSession.mockResolvedValueOnce({ authenticated: false }).mockResolvedValue(admin);
    request.mockImplementation(path => Promise.resolve(path === '/api/login' ? null : summary));
    mount('/login');
    fireEvent.change(await screen.findByLabelText('Username'), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'test-password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in →' }));
    expect(await screen.findByRole('heading', { name: 'Asset overview' })).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith('/api/login', expect.objectContaining({ method: 'POST' }));
  });

  it('keeps search while polling changes connection status', async () => {
    vi.useFakeTimers();
    mount();
    await act(async () => {});
    fireEvent.change(screen.getByRole('textbox', { name: 'Search assets' }), { target: { value: 'Demo' } });
    request.mockResolvedValue({ ...summary, assets: [{ ...asset, status: 'DISCONNECTED' }] });
    await act(async () => { vi.advanceTimersByTime(5000); });
    expect(screen.getByRole('textbox', { name: 'Search assets' })).toHaveValue('Demo');
    expect(screen.getByText('DISCONNECTED')).toBeInTheDocument();
    expect(screen.getByText('Demo phone')).toBeInTheDocument();
  });

  it('shows read-only controls for officers', async () => {
    getSession.mockResolvedValue({ ...admin, admin: false, username: 'officer' });
    mount();
    expect(await screen.findByText('Demo phone')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Disconnect' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Register asset/ })).not.toBeInTheDocument();
  });

  it('preserves entered form data when a duplicate is rejected', async () => {
    request.mockRejectedValue(new Error('Serial number or Bluetooth address is already registered.'));
    mount('/assets/new');
    await screen.findByRole('heading', { name: 'Register asset' });
    for (const [label, value] of [['Asset name', 'New phone'], ['Asset type', 'Phone'], ['Serial number', 'SN1'], ['Bluetooth MAC address', 'AA:BB:CC:DD:EE:FF'], ['Department', 'IT'], ['Assigned officer', 'Officer']]) {
      fireEvent.change(screen.getByLabelText(new RegExp(`^${label}`)), { target: { value } });
    }
    fireEvent.click(screen.getByRole('button', { name: 'Save asset' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('already registered');
    expect(screen.getByLabelText('Asset name')).toHaveValue('New phone');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save asset' })).toBeEnabled());
  });

  it('simulates location shift and triggers breach alert for restricted zones', async () => {
    request.mockImplementation((path, options) => {
      if (path === '/api/summary') return Promise.resolve(summary);
      if (path === '/api/facility-zones') return Promise.resolve(['HQ Main Facility', 'Dispatch Office', 'Restricted Perimeter']);
      if (path.includes('/shift')) {
        return Promise.resolve({
          asset: { ...asset, currentLocation: 'Restricted Perimeter' },
          authorized: false,
          alertCreated: true,
          message: "SECURITY BREACH: Unauthorized movement to 'Restricted Perimeter'!",
          block: { blockIndex: 1, hash: '0000breach123' }
        });
      }
      return Promise.resolve(summary);
    });

    mount('/simulation');
    expect(await screen.findByRole('heading', { name: 'Location Shift Simulator' })).toBeInTheDocument();
    expect(screen.getAllByText('Demo phone').length).toBeGreaterThan(0);

    const restrictedBtn = await screen.findByRole('button', { name: /Restricted Perimeter/i });
    fireEvent.click(restrictedBtn);

    expect(await screen.findByRole('alert')).toHaveTextContent(/SECURITY BREACH ALERT TRIGGERED/i);
  });

  it('renders blockchain ledger and allows cryptographic auditing', async () => {
    mount('/blockchain');
    expect(await screen.findByRole('heading', { name: 'Local Blockchain Ledger' })).toBeInTheDocument();
    expect(screen.getByText(/BLOCKCHAIN INTEGRITY: 100% VERIFIED/i)).toBeInTheDocument();
    expect(screen.getByText('Block #0')).toBeInTheDocument();
    expect(screen.getByText('0000abc123')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Cryptographic Audit/i }));
    expect(await screen.findByText(/Cryptographic Verification Passed/i)).toBeInTheDocument();
  });
});
