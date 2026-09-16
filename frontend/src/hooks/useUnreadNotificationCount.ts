import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';

import { notificationApi } from '@/api';

export function useUnreadNotificationCount() {
  const [unreadCount, setUnreadCount] = useState<number | null>(null);

  useFocusEffect(
    useCallback(() => {
      let isActive = true;

      void notificationApi
        .getUnreadCount()
        .then((response) => {
          if (isActive) setUnreadCount(response.unreadCount);
        })
        .catch(() => {
          if (isActive) setUnreadCount(null);
        });

      return () => {
        isActive = false;
      };
    }, []),
  );

  return unreadCount;
}
