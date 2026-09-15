import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import type { AuthTokens } from '@/types/auth.types';

const AUTH_TOKENS_STORAGE_KEY = 'dodam.auth.tokens';

let cachedTokens: AuthTokens | null | undefined;
let pendingRead: Promise<AuthTokens | null> | null = null;

function isAuthTokens(value: unknown): value is AuthTokens {
  if (!value || typeof value !== 'object') return false;

  const tokens = value as Partial<AuthTokens>;

  return (
    tokens.tokenType === 'Bearer' &&
    typeof tokens.accessToken === 'string' &&
    typeof tokens.accessTokenExpiresAt === 'string' &&
    typeof tokens.refreshToken === 'string' &&
    typeof tokens.refreshTokenExpiresAt === 'string'
  );
}

async function readTokensFromStorage() {
  if (Platform.OS === 'web') return null;

  const storedValue = await SecureStore.getItemAsync(AUTH_TOKENS_STORAGE_KEY);
  if (!storedValue) return null;

  try {
    const parsedValue: unknown = JSON.parse(storedValue);

    if (isAuthTokens(parsedValue)) return parsedValue;
  } catch {
    // 손상되거나 이전 형식인 인증 정보는 아래에서 제거한다.
  }

  await SecureStore.deleteItemAsync(AUTH_TOKENS_STORAGE_KEY);
  return null;
}

export async function getAuthTokens(): Promise<AuthTokens | null> {
  if (cachedTokens !== undefined) return cachedTokens;
  if (pendingRead) return pendingRead;

  pendingRead = readTokensFromStorage()
    .then((tokens) => {
      cachedTokens = tokens;
      return tokens;
    })
    .finally(() => {
      pendingRead = null;
    });

  return pendingRead;
}

export async function setAuthTokens(tokens: AuthTokens) {
  if (Platform.OS !== 'web') {
    await SecureStore.setItemAsync(
      AUTH_TOKENS_STORAGE_KEY,
      JSON.stringify(tokens),
      { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY },
    );
  }

  cachedTokens = tokens;
}

export async function clearAuthTokens() {
  if (Platform.OS !== 'web') {
    await SecureStore.deleteItemAsync(AUTH_TOKENS_STORAGE_KEY);
  }

  cachedTokens = null;
}
