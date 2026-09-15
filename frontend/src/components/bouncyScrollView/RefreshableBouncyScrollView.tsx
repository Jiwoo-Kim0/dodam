import type { ComponentProps } from 'react';
import { forwardRef } from 'react';

import { useRefreshFeedback } from '@/hooks/useRefreshFeedback';
import { colors } from '@/styles';

import {
  BouncyScrollView,
  type BouncyScrollViewHandle,
} from './BouncyScrollView';

type RefreshableBouncyScrollViewProps = Omit<
  ComponentProps<typeof BouncyScrollView>,
  'onRefresh' | 'refreshing'
> & {
  onRefresh?: () => void;
  refreshing?: boolean;
};

export const RefreshableBouncyScrollView = forwardRef<
  BouncyScrollViewHandle,
  RefreshableBouncyScrollViewProps
>(function RefreshableBouncyScrollView(props, ref) {
  const {
    onRefresh,
    refreshing,
    ...scrollViewProps
  } = props;
  const { isRefreshing: isFeedbackRefreshing, triggerRefresh } = useRefreshFeedback();

  return (
    <BouncyScrollView
      {...scrollViewProps}
      onRefresh={onRefresh ?? triggerRefresh}
      ref={ref}
      refreshBackgroundColor={props.refreshBackgroundColor ?? colors.background}
      refreshIndicatorColor={props.refreshIndicatorColor ?? colors.primary.green}
      refreshing={refreshing ?? isFeedbackRefreshing}
    />
  );
});
