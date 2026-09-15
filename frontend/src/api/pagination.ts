import type { PageResponse } from './api.types';

const MAX_PAGE_SIZE = 100;

type PageFetcher<T> = (
  page: number,
  size: number,
) => Promise<PageResponse<T>>;

export async function fetchAllPages<T>(fetchPage: PageFetcher<T>): Promise<T[]> {
  const items: T[] = [];
  let page = 0;

  while (true) {
    const response = await fetchPage(page, MAX_PAGE_SIZE);
    items.push(...response.content);

    if (response.last || page + 1 >= response.totalPages) {
      return items;
    }

    page += 1;
  }
}
