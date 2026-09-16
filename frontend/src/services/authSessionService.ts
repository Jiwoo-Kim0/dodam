import { clearAuthTokens } from '@/services/authTokenStorage';

type AuthSessionStatus = 'unknown' | 'active' | 'ended' | 'expired';
type SessionExpiredListener = () => void;

let status: AuthSessionStatus = 'unknown';
let sessionVersion = 0;
const expiredListeners = new Set<SessionExpiredListener>();

export function markAuthSessionActive() {
  status = 'active';
  sessionVersion += 1;
}

export function getAuthSessionVersion() {
  return sessionVersion;
}

export function isAuthSessionVersionCurrent(version: number) {
  return version === sessionVersion && status !== 'ended' && status !== 'expired';
}

export async function endAuthSession() {
  status = 'ended';
  sessionVersion += 1;
  await clearAuthTokens();
}

export async function expireAuthSession() {
  if (status === 'ended') {
    await clearAuthTokens();
    return;
  }

  const shouldNotify = status !== 'expired';
  status = 'expired';
  sessionVersion += 1;
  await clearAuthTokens();

  if (shouldNotify) {
    expiredListeners.forEach((listener) => listener());
  }
}

export function subscribeAuthSessionExpired(listener: SessionExpiredListener) {
  expiredListeners.add(listener);

  if (status === 'expired') {
    Promise.resolve().then(listener);
  }

  return () => {
    expiredListeners.delete(listener);
  };
}
