import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Alert, AdminUser, AuthResponse, DashboardSummary, Device, Forecast, Reading, User } from './models';
import { environment } from '../environments/environment';

const API = environment.apiUrl;

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API}/auth/login`, { email, password });
  }

  register(firstname: string, lastname: string, email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API}/auth/register`, { firstname, lastname, email, password });
  }

  getProfile(): Observable<User> { return this.http.get<User>(`${API}/profile`); }
  updateProfile(profile: Pick<User, 'firstname' | 'lastname' | 'email'>): Observable<AuthResponse> {
    return this.http.put<AuthResponse>(`${API}/profile`, profile);
  }
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API}/profile/password`, { currentPassword, newPassword });
  }
  getDashboard(): Observable<DashboardSummary> { return this.http.get<DashboardSummary>(`${API}/dashboard`); }
  getDevices(): Observable<Device[]> { return this.http.get<Device[]>(`${API}/devices`); }
  addDevice(device: Pick<Device, 'name' | 'type' | 'location' | 'active'>): Observable<Device> {
    return this.http.post<Device>(`${API}/devices`, device);
  }
  updateDevice(device: Device): Observable<Device> {
    return this.http.put<Device>(`${API}/devices/${device.id}`, device);
  }
  deleteDevice(id: number): Observable<void> { return this.http.delete<void>(`${API}/devices/${id}`); }
  getHistory(days = 30): Observable<Reading[]> {
    return this.http.get<Reading[]>(`${API}/consumption`, { params: new HttpParams().set('days', days) });
  }
  addReading(deviceId: number, consumptionKwh: number): Observable<Reading> {
    return this.http.post<Reading>(`${API}/devices/${deviceId}/readings`, { consumptionKwh });
  }
  getAlerts(): Observable<Alert[]> { return this.http.get<Alert[]>(`${API}/alerts`); }
  acknowledgeAlert(id: number): Observable<Alert> { return this.http.post<Alert>(`${API}/alerts/${id}/acknowledge`, {}); }
  getForecast(): Observable<Forecast> { return this.http.post<Forecast>(`${API}/ai/forecast`, {}); }
  getUsers(): Observable<AdminUser[]> { return this.http.get<AdminUser[]>(`${API}/admin/users`); }
  setUserRole(id: number, role: User['role']): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${API}/admin/users/${id}/role`, { role });
  }
  deleteUser(id: number): Observable<void> { return this.http.delete<void>(`${API}/admin/users/${id}`); }
}
