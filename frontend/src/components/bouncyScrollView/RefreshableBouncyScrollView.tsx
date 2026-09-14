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
>;

export const RefreshableBouncyScrollView = forwardRef<
  BouncyScrollViewHandle,
  RefreshableBouncyScrollViewProps
>(function RefreshableBouncyScrollView(props, ref) {
  const { isRefreshing, triggerRefresh } = useRefreshFeedback();

  return (
    <BouncyScrollView
      {...props}
      onRefresh={triggerRefresh}
      ref={ref}
      refreshBackgroundColor={props.refreshBackgroundColor ?? colors.background}
      refreshIndicatorColor={props.refreshIndicatorColor ?? colors.primary.green}
      refreshing={isRefreshing}
    />
  );
});
