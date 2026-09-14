import { useCallback, useEffect, useRef, useState } from 'react';

const DEFAULT_REFRESH_DURATION = 600;

export function useRefreshFeedback(duration = DEFAULT_REFRESH_DURATION) {
  const [isRefreshing, setIsRefreshing] = useState(false);
  const refreshTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (refreshTimeoutRef.current) {
        clearTimeout(refreshTimeoutRef.current);
      }
    },
    [],
  );

  const triggerRefresh = useCallback(() => {
    if (refreshTimeoutRef.current) return;

    setIsRefreshing(true);
    refreshTimeoutRef.current = setTimeout(() => {
      setIsRefreshing(false);
      refreshTimeoutRef.current = null;
    }, duration);
  }, [duration]);

  return { isRefreshing, triggerRefresh };
}
