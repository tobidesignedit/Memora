import { initializeApp, getApps, type FirebaseApp } from 'firebase/app';
import {
  createUserWithEmailAndPassword,
  EmailAuthProvider,
  getAuth,
  GoogleAuthProvider,
  linkWithCredential,
  signInAnonymously,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
  type Auth,
} from 'firebase/auth';
import {
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  getFirestore,
  type Firestore,
} from 'firebase/firestore';
import {
  initializeAppCheck,
  ReCaptchaEnterpriseProvider,
  ReCaptchaV3Provider,
  type AppCheck,
} from 'firebase/app-check';
import {
  getRemoteConfig,
  fetchAndActivate,
  getString,
  getBoolean,
  getNumber,
  type RemoteConfig,
} from 'firebase/remote-config';
import { getAI, GoogleAIBackend, type AI } from 'firebase/ai';
import type { PrivacyRoutingMode, RemoteConfigValues } from '../types/memora';

export const DEFAULT_REMOTE_CONFIG: RemoteConfigValues = {
  cloudModelFast: 'gemini-3.5-flash-lite',
  cloudModelPro: 'gemini-3.8-flash',
  ttsModel: 'gemini-3.1-flash-tts-preview',
  defaultInferenceMode: 'prefer_on_device',
  enableTtsRecap: true,
  maxContextMemories: 24,
};

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || '',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || '',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || '',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || '',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '',
  appId: import.meta.env.VITE_FIREBASE_APP_ID || '',
};

export const isFirebaseConfigured = Boolean(
  firebaseConfig.apiKey && firebaseConfig.projectId && firebaseConfig.appId
);

let app: FirebaseApp | null = null;
let auth: Auth | null = null;
let db: Firestore | null = null;
let ai: AI | null = null;
let appCheck: AppCheck | null = null;
let remoteConfig: RemoteConfig | null = null;
let appCheckStatus: 'verified_debug' | 'verified_recaptcha' | 'not_configured' = 'not_configured';

if (isFirebaseConfigured) {
  app = getApps().length > 0 ? getApps()[0] : initializeApp(firebaseConfig);

  // Firebase App Check — activate reCAPTCHA in production, debug token strictly on localhost
  const debugToken = import.meta.env.VITE_FIREBASE_APPCHECK_DEBUG_TOKEN;
  const recaptchaSiteKey = import.meta.env.VITE_RECAPTCHA_SITE_KEY;
  const recaptchaEnterpriseKey = import.meta.env.VITE_RECAPTCHA_ENTERPRISE_KEY;
  const isLocalHost =
    typeof window !== 'undefined' &&
    (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1');

  if (isLocalHost) {
    const g = self as unknown as Record<string, unknown>;
    if (debugToken) {
      g.FIREBASE_APPCHECK_DEBUG_TOKEN = debugToken === 'true' ? true : debugToken;
    } else {
      g.FIREBASE_APPCHECK_DEBUG_TOKEN = true;
    }
  }

  try {
    if (recaptchaEnterpriseKey) {
      appCheck = initializeAppCheck(app, {
        provider: new ReCaptchaEnterpriseProvider(recaptchaEnterpriseKey),
        isTokenAutoRefreshEnabled: true,
      });
      appCheckStatus = isLocalHost && debugToken ? 'verified_debug' : 'verified_recaptcha';
    } else if (recaptchaSiteKey) {
      appCheck = initializeAppCheck(app, {
        provider: new ReCaptchaV3Provider(recaptchaSiteKey),
        isTokenAutoRefreshEnabled: true,
      });
      appCheckStatus = isLocalHost && debugToken ? 'verified_debug' : 'verified_recaptcha';
    } else if (isLocalHost) {
      // Debug token path strictly for local dev when reCAPTCHA key isn't provisioned yet
      appCheck = initializeAppCheck(app, {
        provider: new ReCaptchaV3Provider('6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI'),
        isTokenAutoRefreshEnabled: true,
      });
      appCheckStatus = 'verified_debug';
    }
  } catch (err) {
    console.warn('[MEMORA] App Check init note:', err);
  }

  auth = getAuth(app);

  // Firestore with offline multi-tab IndexedDB persistence
  try {
    db = initializeFirestore(app, {
      localCache: persistentLocalCache({
        tabManager: persistentMultipleTabManager(),
      }),
    });
  } catch {
    db = getFirestore(app);
  }

  // Firebase AI Logic (Gemini Developer API backend + hybrid Chrome Gemini Nano 4 support)
  ai = getAI(app, { backend: new GoogleAIBackend() });

  // Firebase Remote Config with offline-safe defaults
  try {
    remoteConfig = getRemoteConfig(app);
    remoteConfig.settings.minimumFetchIntervalMillis = 60_000;
    remoteConfig.defaultConfig = {
      cloud_model_fast: DEFAULT_REMOTE_CONFIG.cloudModelFast,
      cloud_model_pro: DEFAULT_REMOTE_CONFIG.cloudModelPro,
      tts_model: DEFAULT_REMOTE_CONFIG.ttsModel,
      default_inference_mode: DEFAULT_REMOTE_CONFIG.defaultInferenceMode,
      enable_tts_recap: DEFAULT_REMOTE_CONFIG.enableTtsRecap,
      max_context_memories: DEFAULT_REMOTE_CONFIG.maxContextMemories,
    };
  } catch (err) {
    console.warn('[MEMORA] Remote Config init note:', err);
  }
}

export async function loadRemoteConfigValues(): Promise<RemoteConfigValues> {
  if (!remoteConfig) return DEFAULT_REMOTE_CONFIG;
  try {
    await fetchAndActivate(remoteConfig);
    const rawMode = getString(remoteConfig, 'default_inference_mode');
    const mode: PrivacyRoutingMode =
      rawMode === 'only_on_device' || rawMode === 'prefer_in_cloud'
        ? rawMode
        : 'prefer_on_device';
    return {
      cloudModelFast: getString(remoteConfig, 'cloud_model_fast') || DEFAULT_REMOTE_CONFIG.cloudModelFast,
      cloudModelPro: getString(remoteConfig, 'cloud_model_pro') || DEFAULT_REMOTE_CONFIG.cloudModelPro,
      ttsModel: getString(remoteConfig, 'tts_model') || DEFAULT_REMOTE_CONFIG.ttsModel,
      defaultInferenceMode: mode,
      enableTtsRecap: getBoolean(remoteConfig, 'enable_tts_recap'),
      maxContextMemories: getNumber(remoteConfig, 'max_context_memories') || DEFAULT_REMOTE_CONFIG.maxContextMemories,
    };
  } catch {
    return DEFAULT_REMOTE_CONFIG;
  }
}

export async function signInGuest() {
  if (!auth) return null;
  const cred = await signInAnonymously(auth);
  return cred.user;
}

export async function signInWithGoogleAccount() {
  if (!auth) return null;
  const provider = new GoogleAuthProvider();
  const cred = await signInWithPopup(auth, provider);
  return cred.user;
}

export async function signInWithEmailAccount(email: string, password: string) {
  if (!auth) throw new Error('Firebase Auth is not configured.');
  const cred = await signInWithEmailAndPassword(auth, email.trim(), password);
  return cred.user;
}

export async function signUpWithEmailAccount(email: string, password: string) {
  if (!auth) throw new Error('Firebase Auth is not configured.');
  const cleanEmail = email.trim();
  if (auth.currentUser && auth.currentUser.isAnonymous) {
    try {
      const credential = EmailAuthProvider.credential(cleanEmail, password);
      const linked = await linkWithCredential(auth.currentUser, credential);
      return linked.user;
    } catch {
      // Fall back to fresh account creation if linking isn't applicable
    }
  }
  const cred = await createUserWithEmailAndPassword(auth, cleanEmail, password);
  return cred.user;
}

export async function signOutAccount() {
  if (!auth) return;
  await signOut(auth);
}

export { app, auth, db, ai, appCheck, remoteConfig, appCheckStatus, firebaseConfig };
