import Constants from 'expo-constants';
import { Platform } from 'react-native';

const DEFAULT_CHANNEL_ID = 'dodam-default';

export async function requestNotificationPermission(): Promise<boolean> {
  if (Constants.expoGoConfig !== null) {
    return false;
  }

  const Notifications = await import('expo-notifications');

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(DEFAULT_CHANNEL_ID, {
      name: '도담 알림',
      importance: Notifications.AndroidImportance.DEFAULT,
    });
  }

  const currentPermission = await Notifications.getPermissionsAsync();

  if (currentPermission.granted) {
    return true;
  }

  const requestedPermission = await Notifications.requestPermissionsAsync();
  return requestedPermission.granted;
}
