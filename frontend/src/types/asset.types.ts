export type AssetCompositionTone = 'strong' | 'medium' | 'light';

export interface AssetComposition {
  label: string;
  amount: number;
  tone: AssetCompositionTone;
}

export interface AssetOverview {
  totalAsset: number;
  changeFromLastMonth: number | null;
  composition: readonly AssetComposition[];
}

export type AssetSectionId = 'checking' | 'saving' | 'loan' | 'card';

export interface AssetItem {
  id: string;
  institutionId: string;
  productName: string;
  description: string;
  amount: number;
}

export interface AssetSection {
  id: AssetSectionId;
  title: string;
  summaryPrefix?: string;
  items: readonly AssetItem[];
}
