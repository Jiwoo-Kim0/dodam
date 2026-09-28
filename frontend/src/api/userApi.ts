import type {
  FinancialProfile,
  UpdateFinancialProfileRequest,
  UpdateUserProfileRequest,
  UpdateUserSettingsRequest,
  UserProfile,
  UserSettings,
} from '@/types/user.types';

import { apiClient } from './apiClient';

const USER_PATH = '/v1/users/me';

export function getMe() {
  return apiClient.get<UserProfile>(USER_PATH);
}

export function updateMe(request: UpdateUserProfileRequest) {
  return apiClient.patch<UserProfile>(USER_PATH, { body: request });
}

export function getSettings() {
  return apiClient.get<UserSettings>(`${USER_PATH}/settings`);
}

export function updateSettings(request: UpdateUserSettingsRequest) {
  return apiClient.patch<UserSettings>(`${USER_PATH}/settings`, { body: request });
}

export function getFinancialProfile() {
  return apiClient.get<FinancialProfile>(`${USER_PATH}/financial-profile`);
}

export function updateFinancialProfile(request: UpdateFinancialProfileRequest) {
  return apiClient.patch<FinancialProfile>(`${USER_PATH}/financial-profile`, {
    body: request,
  });
}

export const userApi = {
  getMe,
  updateMe,
  getSettings,
  updateSettings,
  getFinancialProfile,
  updateFinancialProfile,
};
