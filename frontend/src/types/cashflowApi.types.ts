export interface CashflowForecastApiResponse {
  forecastId: number;
  baseDate: string;
  horizonDays: number;
  endDate: string;
  status: string;
  currentBalance: number | null;
  availableToday: number | null;
  minimumBalance: number | null;
  riskDate: string | null;
  calculationVersion: string;
  createdAt: string;
  completedAt: string | null;
}

export interface CashflowEventApiResponse {
  eventId: number;
  eventDate: string;
  type: string;
  name: string;
  direction: 'INFLOW' | 'OUTFLOW';
  amount: number;
  sourceType: string;
  sourceId: string;
}

export interface CashflowEventListApiResponse {
  forecast: CashflowForecastApiResponse;
  startDate: string;
  endDate: string;
  type: string | null;
  events: CashflowEventApiResponse[];
}

export interface CashflowRiskApiResponse {
  riskId: number;
  riskDate: string;
  type: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  message: string;
  expectedBalance: number;
}

export interface CashflowRiskListApiResponse {
  forecast: CashflowForecastApiResponse;
  type: string | null;
  severity: CashflowRiskApiResponse['severity'] | null;
  risks: CashflowRiskApiResponse[];
}
