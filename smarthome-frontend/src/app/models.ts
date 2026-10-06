export interface User {
  id: number;
  firstname: string;
  lastname: string;
  email: string;
  role: 'USER' | 'ADMIN';
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Device {
  id: number;
  name: string;
  type: string;
  location: string | null;
  active: boolean;
}

export interface Reading {
  id: number;
  deviceId: number;
  deviceName: string;
  consumptionKwh: number;
  recordedAt: string;
}

export interface Alert {
  id: number;
  message: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  acknowledged: boolean;
  createdAt: string;
}

export interface DashboardSummary {
  deviceCount: number;
  activeDeviceCount: number;
  todayKwh: number;
  monthKwh: number;
  unreadAlerts: number;
}

export interface Forecast {
  forecast: { date: string; kwh: number }[];
  daily_average_kwh: number;
  trend: 'increasing' | 'decreasing' | 'stable';
  model: string;
  recommendations: string[];
}

export interface AdminUser extends User {
  createdAt: string | null;
}
