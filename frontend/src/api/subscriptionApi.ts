import type {
  SubscriptionApiResponse,
  SubscriptionStatus,
} from '@/types/subscriptionApi.types';

import { apiClient } from './apiClient';
import type { PageResponse } from './api.types';

interface GetSubscriptionsParams {
  status?: SubscriptionStatus;
  category?: string;
  page?: number;
  size?: number;
}

export const subscriptionApi = {
  getSubscriptions: ({
    status,
    category,
    page = 0,
    size = 100,
  }: GetSubscriptionsParams = {}) =>
    apiClient.get<PageResponse<SubscriptionApiResponse>>('/v1/subscriptions', {
      query: { status, category, page, size },
    }),
};
