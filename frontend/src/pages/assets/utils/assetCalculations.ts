import type { AssetSection } from '@/types/asset.types';

export function getAssetSectionTotal(section: AssetSection): number {
  return section.items.reduce(
    (total, item) => total + item.amount,
    0,
  );
}