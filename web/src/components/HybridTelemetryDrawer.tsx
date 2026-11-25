import { useEffect, useState } from 'react';
import {
  FolderOpen,
  Loader2,
  LogIn,
  LogOut,
  RefreshCw,
  X,
} from 'lucide-react';
import type { WebFolderScope } from '../services/folderAutoIndexer';
import {
  signInWithEmailAccount,
  signInWithGoogleAccount,
  signOutAccount,
  signUpWithEmailAccount,
} from '../services/firebase';
import type { OnDeviceCapability } from '../services/hybridAi';
import type {
  BenchmarkEntry,
  PrivacyRoutingMode,
  RemoteConfigValues,
} from '../types/memora';
import type { User } from 'firebase/auth';

interface HybridTelemetryDrawerProps {
  open: boolean;
  onClose: () => void;
  user: User | null;
  online: boolean;
  pendingCount: number;
  localOnlyCount: number;
  capability: OnDeviceCapability;
  defaultMode: PrivacyRoutingMode;
  remoteConfig: RemoteConfigValues;
  benchmarks: BenchmarkEntry[];
  onChangeDefaultMode: (mode: PrivacyRoutingMode) => void;
  onSyncNow: () => Promise<void>;
  onRefreshCapability: () => Promise<void>;
  onLoadDemoData: () => Promise<void>;
  connectedFolderName: string | null;
  folderScope: WebFolderScope;
  folderIndexedCount: number;
  folderScanning: boolean;
  folderApiSupported: boolean;
  onConnectFolder: () => Promise<void>;
  onDisconnectFolder: () => void;
  onChangeFolderScope: (scope: WebFolderScope) => void;
  onScanFolderNow: () => Promise<void>;
  onBenchmarkComplete?: () => Promise<void>;
}

export function HybridTelemetryDrawer({
  open,
  onClose,
  user,
  defaultMode,
  onChangeDefaultMode,
  onSyncNow,
  connectedFolderName,
  folderScope,
  folderScanning,
  folderApiSupported,
  onConnectFolder,
  onDisconnectFolder,
  onChangeFolderScope,
  onScanFolderNow,
}: HybridTelemetryDrawerProps) {
  const [authBusy, setAuthBusy] = useState(false);
  const [authMode, setAuthMode] = useState<'signin' | 'signup'>('signin');
  const [emailInput, setEmailInput] = useState('');
  const [passwordInput, setPasswordInput] = useState('');
  const [authError, setAuthError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end"
      style={{ background: 'rgba(17, 19, 24, 0.45)' }}
      onClick={onClose}
    >
      <aside
        className="memora-sheet w-full max-w-md h-full overflow-y-auto p-6 sm:p-7 flex flex-col gap-6 animate-memora-enter"
        onClick={(e) => e.stopPropagation()}
        aria-label="Account and Settings"
      >
        <div className="flex items-center justify-between">
          <h2 className="font-display text-2xl font-medium">Account &amp; Settings</h2>
          <button
            type="button"
            onClick={onClose}
            className="memora-press w-8 h-8 rounded-full flex items-center justify-center"
            style={{ background: 'var(--bg-soft)', color: 'var(--text-secondary)' }}
            aria-label="Close panel"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Account Login / Sign Up (Link Phone & PC) */}
        <section
          className="rounded-2xl p-4 border flex flex-col gap-3"
          style={{ background: 'var(--bg-soft)', borderColor: 'var(--border-subtle)' }}
        >
          <div className="flex items-center justify-between gap-2">
            <span className="text-sm font-semibold">Link Phone &amp; PC Vault</span>
            {user && !user.isAnonymous && (
              <span
                className="text-[11px] font-medium px-2.5 py-0.5 rounded-full"
                style={{
                  background: 'var(--semantic-ondevice-soft)',
                  color: 'var(--semantic-ondevice)',
                }}
              >
                Synced
              </span>
            )}
          </div>

          {user && !user.isAnonymous ? (
            <div className="flex flex-col gap-2.5">
              <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                Signed in as <strong style={{ color: 'var(--text-primary)' }}>{user.email || user.displayName}</strong>. Sign in with this same account in the MEMORA Android app to sync automatically.
              </p>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={onSyncNow}
                  className="memora-press flex-1 inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-full text-xs font-medium border"
                  style={{ background: 'var(--bg-surface)', borderColor: 'var(--border-strong)' }}
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  Sync Now
                </button>
                <button
                  type="button"
                  onClick={() => void signOutAccount()}
                  className="memora-press inline-flex items-center justify-center gap-1.5 py-2 px-3.5 rounded-full text-xs font-medium border"
                  style={{ background: 'var(--bg-surface)', borderColor: 'var(--border-strong)' }}
                >
                  <LogOut className="w-3.5 h-3.5" />
                  Sign Out
                </button>
              </div>
            </div>
          ) : (
            <form
              onSubmit={async (e) => {
                e.preventDefault();
                if (!emailInput.trim() || passwordInput.length < 6) {
                  setAuthError('Enter a valid email and at least 6-character password.');
                  return;
                }
                setAuthBusy(true);
                setAuthError(null);
                try {
                  if (authMode === 'signup') {
                    await signUpWithEmailAccount(emailInput, passwordInput);
                  } else {
                    await signInWithEmailAccount(emailInput, passwordInput);
                  }
                  setPasswordInput('');
                } catch (err) {
                  setAuthError(err instanceof Error ? err.message.replace('Firebase: ', '') : 'Authentication failed.');
                } finally {
                  setAuthBusy(false);
                }
              }}
              className="flex flex-col gap-2.5"
            >
              <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                Use the same email &amp; password on your PC and Android phone to link both vaults automatically.
              </p>
              <div className="grid grid-cols-2 gap-1 p-1 rounded-full" style={{ background: 'var(--bg-surface)' }}>
                <button
                  type="button"
                  onClick={() => { setAuthMode('signin'); setAuthError(null); }}
                  className="py-1 rounded-full text-xs font-medium"
                  style={{
                    background: authMode === 'signin' ? 'var(--accent-primary)' : 'transparent',
                    color: authMode === 'signin' ? '#FFFFFF' : 'var(--text-muted)',
                  }}
                >
                  Sign In
                </button>
                <button
                  type="button"
                  onClick={() => { setAuthMode('signup'); setAuthError(null); }}
                  className="py-1 rounded-full text-xs font-medium"
                  style={{
                    background: authMode === 'signup' ? 'var(--accent-primary)' : 'transparent',
                    color: authMode === 'signup' ? '#FFFFFF' : 'var(--text-muted)',
                  }}
                >
                  Sign Up
                </button>
              </div>
              <input
                type="email"
                required
                placeholder="Email address"
                value={emailInput}
                onChange={(e) => setEmailInput(e.target.value)}
                className="w-full px-3.5 py-2 rounded-xl text-xs border"
                style={{
                  background: 'var(--bg-surface)',
                  borderColor: 'var(--border-strong)',
                  color: 'var(--text-primary)',
                }}
              />
              <input
                type="password"
                required
                minLength={6}
                placeholder="Password (6+ chars)"
                value={passwordInput}
                onChange={(e) => setPasswordInput(e.target.value)}
                className="w-full px-3.5 py-2 rounded-xl text-xs border"
                style={{
                  background: 'var(--bg-surface)',
                  borderColor: 'var(--border-strong)',
                  color: 'var(--text-primary)',
                }}
              />
              {authError && (
                <p className="text-[11px]" style={{ color: 'var(--semantic-danger)' }}>
                  {authError}
                </p>
              )}
              <button
                type="submit"
                disabled={authBusy}
                className="memora-press w-full py-2 px-4 rounded-full text-xs font-semibold inline-flex items-center justify-center gap-1.5"
                style={{ background: 'var(--accent-primary)', color: '#FFFFFF' }}
              >
                {authBusy ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <LogIn className="w-3.5 h-3.5" />}
                <span>{authMode === 'signup' ? 'Create Synced Account' : 'Sign In & Sync'}</span>
              </button>
              <button
                type="button"
                disabled={authBusy}
                onClick={async () => {
                  setAuthBusy(true);
                  setAuthError(null);
                  try {
                    await signInWithGoogleAccount();
                  } catch (err) {
                    setAuthError(err instanceof Error ? err.message.replace('Firebase: ', '') : 'Google sign-in failed.');
                  } finally {
                    setAuthBusy(false);
                  }
                }}
                className="memora-press w-full py-2 px-4 rounded-full text-xs font-medium border"
                style={{ background: 'var(--bg-surface)', borderColor: 'var(--border-strong)' }}
              >
                Continue with Google
              </button>
            </form>
          )}
        </section>

        {/* Privacy Mode */}
        <section className="flex flex-col gap-2.5 pb-5 border-b" style={{ borderColor: 'var(--border-subtle)' }}>
          <h3 className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>
            Default Inference Policy
          </h3>
          <div className="grid grid-cols-3 gap-1.5 p-1 rounded-full" style={{ background: 'var(--bg-soft)' }}>
            {(
              [
                { id: 'prefer_on_device', label: 'Prefer Local' },
                { id: 'only_on_device', label: 'Local Only' },
                { id: 'prefer_in_cloud', label: 'Prefer Cloud' },
              ] as const
            ).map((opt) => {
              const active = defaultMode === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => onChangeDefaultMode(opt.id)}
                  className="memora-press py-1.5 px-2 rounded-full text-xs font-medium"
                  style={{
                    background: active ? 'var(--bg-surface)' : 'transparent',
                    color: active ? 'var(--text-primary)' : 'var(--text-muted)',
                    boxShadow: active ? '0 1px 3px rgba(0,0,0,0.08)' : undefined,
                  }}
                >
                  {opt.label}
                </button>
              );
            })}
          </div>
        </section>

        {/* Local Folder Auto-Indexing */}
        <section className="flex flex-col gap-3">
          <div className="flex items-center justify-between gap-2">
            <span className="text-sm font-semibold flex items-center gap-2">
              <FolderOpen className="w-4 h-4" style={{ color: 'var(--accent-primary)' }} />
              Auto-Index Local Folder
            </span>
          </div>
          {connectedFolderName ? (
            <div className="flex flex-col gap-2.5">
              <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                Connected to <strong style={{ color: 'var(--text-primary)' }}>{connectedFolderName}</strong>
              </p>
              <div className="grid grid-cols-2 gap-1.5 p-1 rounded-full" style={{ background: 'var(--bg-soft)' }}>
                {(
                  [
                    { id: 'screenshots_only', label: 'Screenshots Only' },
                    { id: 'all_in_folder', label: 'All Images' },
                  ] as const
                ).map((s) => (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => onChangeFolderScope(s.id)}
                    className="py-1 px-2 rounded-full text-xs font-medium"
                    style={{
                      background: folderScope === s.id ? 'var(--bg-surface)' : 'transparent',
                      color: folderScope === s.id ? 'var(--text-primary)' : 'var(--text-muted)',
                    }}
                  >
                    {s.label}
                  </button>
                ))}
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={folderScanning}
                  onClick={onScanFolderNow}
                  className="memora-press flex-1 py-2 px-3 rounded-full text-xs font-medium border"
                  style={{ borderColor: 'var(--border-strong)' }}
                >
                  {folderScanning ? 'Scanning…' : 'Scan Now'}
                </button>
                <button
                  type="button"
                  onClick={onDisconnectFolder}
                  className="memora-press py-2 px-4 rounded-full text-xs font-medium border"
                  style={{ borderColor: 'var(--border-strong)', color: 'var(--text-secondary)' }}
                >
                  Disconnect
                </button>
              </div>
            </div>
          ) : (
            <button
              type="button"
              disabled={!folderApiSupported}
              onClick={onConnectFolder}
              className="memora-press w-full py-2.5 px-4 rounded-full text-xs font-medium border"
              style={{
                background: 'var(--bg-soft)',
                borderColor: 'var(--border-subtle)',
                color: 'var(--text-primary)',
              }}
            >
              {folderApiSupported ? 'Connect Local Folder' : 'Folder Picker requires Chrome/Edge Desktop'}
            </button>
          )}
        </section>
      </aside>
    </div>
  );
}
