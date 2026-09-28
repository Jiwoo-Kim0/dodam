import type { AssetSection } from '@/types/asset.types';

export function getAssetSectionTotal(section: AssetSection): number {
  if (section.summaryAmount !== undefined) {
    return section.summaryAmount;
  }

  return section.items.reduce(
    (total, item) => total + item.amount,
    0,
  );
}
