export type AssetCompositionTone = 'strong' | 'medium' | 'light';

export interface AssetComposition {
  label: string;
  amount: number;
  tone: AssetCompositionTone;
}

export interface AssetOverview {
  netAsset: number;
  changeFromLastMonth: number | null;
  composition: readonly AssetComposition[];
}

export interface AssetAnalysis {
  cashTotal: number;
  cashAccountCount: number;
  inactiveAccountCount: number;
  inactiveBalance: number;
  inactiveMonths: number;
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
  summaryAmount?: number;
  items: readonly AssetItem[];
}
