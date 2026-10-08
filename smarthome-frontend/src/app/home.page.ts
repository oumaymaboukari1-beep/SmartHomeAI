import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { Alert, AdminUser, DashboardSummary, Device, Forecast, Reading, User } from './models';

type Section = 'overview' | 'devices' | 'history' | 'alerts' | 'insights' | 'profile' | 'admin';

interface DailyTotal {
  date: string;
  kwh: number;
}

@Component({
  selector: 'app-home',
  standalone: false,
  templateUrl: './home.page.html',
  styleUrls: ['./home.page.scss'],
})
export class HomePage implements OnInit {
  section: Section = 'overview';
  user: User | null;
  summary: DashboardSummary | null = null;
  devices: Device[] = [];
  readings: Reading[] = [];
  alerts: Alert[] = [];
  adminUsers: AdminUser[] = [];
  forecast: Forecast | null = null;
  dailyTotals: DailyTotal[] = [];
  errorMessage = '';
  successMessage = '';
  saving = false;
  loadingDevices = false;
  loadingHistory = false;
  historyDays = 30;

  deviceName = '';
  deviceType = 'Autre';
  deviceLocation = '';
  readingDeviceId: number | null = null;
  readingValue = 0;
  profileFirstname = '';
  profileLastname = '';
  profileEmail = '';
  currentPassword = '';
  newPassword = '';
  confirmNewPassword = '';

  constructor(private readonly api: ApiService, readonly auth: AuthService) {
    this.user = auth.user;
  }

  ngOnInit(): void {
    this.refresh();
  }

  get maxDailyKwh(): number {
    return Math.max(1, ...this.dailyTotals.map(item => item.kwh));
  }

  refresh(): void {
    this.errorMessage = '';
    this.api.getDashboard().subscribe({
      next: value => { this.summary = value; },
      error: error => this.showError(error),
    });
    this.refreshDevices();
    this.refreshHistory();
    this.api.getAlerts().subscribe({
      next: value => { this.alerts = value; },
      error: error => this.showError(error),
    });
    this.api.getProfile().subscribe({
      next: value => this.setProfile(value),
      error: error => this.showError(error),
    });
  }

  navigate(section: Section): void {
    this.section = section;
    this.errorMessage = '';
    if (section === 'devices') this.refreshDevices();
    if (section === 'admin' && this.user?.role === 'ADMIN') {
      this.api.getUsers().subscribe({
        next: users => { this.adminUsers = users; },
        error: error => this.showError(error),
      });
    }
  }

  refreshDevices(): void {
    this.loadingDevices = true;
    this.api.getDevices().pipe(finalize(() => { this.loadingDevices = false; })).subscribe({
      next: value => {
        this.devices = value;
        if (!value.some(device => device.id === this.readingDeviceId)) {
          this.readingDeviceId = value[0]?.id ?? null;
        }
      },
      error: error => this.showError(error),
    });
  }

  refreshHistory(): void {
    this.loadingHistory = true;
    this.api.getHistory(this.historyDays).pipe(finalize(() => { this.loadingHistory = false; })).subscribe({
      next: value => {
        this.readings = value;
        this.buildDailyTotals(value);
      },
      error: error => this.showError(error),
    });
  }

  onHistoryPeriodChange(event: CustomEvent<{ value?: string | number }>): void {
    const value = Number(event.detail.value);
    if ([7, 30, 90, 365].includes(value) && value !== this.historyDays) {
      this.historyDays = value;
      this.refreshHistory();
    }
  }

  get historyTotalKwh(): number {
    return this.readings.reduce((total, reading) => total + reading.consumptionKwh, 0);
  }

  onSectionChange(event: CustomEvent<{ value?: string | number }>): void {
    const value = event.detail.value;
    if (typeof value === 'string' && (value === 'overview' || value === 'devices' || value === 'history'
      || value === 'alerts' || value === 'insights' || value === 'profile' || value === 'admin')) {
      this.navigate(value);
      if (value === 'history') this.refreshHistory();
    }
  }

  onRoleChange(user: AdminUser, event: CustomEvent<{ value?: string }>): void {
    const value = event.detail.value;
    if (value === 'USER' || value === 'ADMIN') this.changeRole(user, value);
  }

  addDevice(): void {
    if (!this.deviceName.trim() || this.saving) return;
    this.saving = true;
    this.api.addDevice({
      name: this.deviceName.trim(),
      type: this.deviceType,
      location: this.deviceLocation.trim() || null,
      active: true,
    }).pipe(finalize(() => { this.saving = false; })).subscribe({
      next: () => {
        this.deviceName = '';
        this.deviceLocation = '';
        this.refresh();
      },
      error: error => this.showError(error),
    });
  }

  toggleDevice(device: Device): void {
    this.api.updateDevice({ ...device, active: !device.active }).subscribe({
      next: updated => {
        this.devices = this.devices.map(item => item.id === updated.id ? updated : item);
        this.refreshSummary();
      },
      error: error => this.showError(error),
    });
  }

  deleteDevice(device: Device): void {
    this.api.deleteDevice(device.id).subscribe({
      next: () => this.refresh(),
      error: error => this.showError(error),
    });
  }

  addReading(): void {
    if (this.readingDeviceId === null || this.readingValue < 0 || this.saving) return;
    this.saving = true;
    this.api.addReading(this.readingDeviceId, this.readingValue)
      .pipe(finalize(() => { this.saving = false; })).subscribe({
      next: () => {
        this.readingValue = 0;
        this.refresh();
      },
      error: error => this.showError(error),
    });
  }

  acknowledge(alert: Alert): void {
    this.api.acknowledgeAlert(alert.id).subscribe({
      next: updated => {
        this.alerts = this.alerts.map(item => item.id === updated.id ? updated : item);
        this.refreshSummary();
      },
      error: error => this.showError(error),
    });
  }

  runForecast(): void {
    this.saving = true;
    this.errorMessage = '';
    this.api.getForecast().pipe(finalize(() => { this.saving = false; })).subscribe({
      next: result => { this.forecast = result; },
      error: error => this.showError(error),
    });
  }

  saveProfile(): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.saving = true;
    this.api.updateProfile({
      firstname: this.profileFirstname,
      lastname: this.profileLastname,
      email: this.profileEmail,
    }).pipe(finalize(() => { this.saving = false; })).subscribe({
      next: response => {
        this.auth.save(response);
        this.user = response.user;
        this.successMessage = 'Profil mis à jour.';
      },
      error: error => this.showError(error),
    });
  }

  changePassword(): void {
    this.errorMessage = '';
    this.successMessage = '';
    if (this.newPassword !== this.confirmNewPassword) {
      this.errorMessage = 'La confirmation ne correspond pas au nouveau mot de passe.';
      return;
    }
    if (this.saving) return;

    this.saving = true;
    this.api.changePassword(this.currentPassword, this.newPassword)
      .pipe(finalize(() => { this.saving = false; })).subscribe({
        next: () => {
          this.currentPassword = '';
          this.newPassword = '';
          this.confirmNewPassword = '';
          this.successMessage = 'Mot de passe modifié avec succès.';
        },
        error: error => this.showError(error),
      });
  }

  changeRole(user: AdminUser, role: User['role']): void {
    this.api.setUserRole(user.id, role).subscribe({
      next: updated => {
        this.adminUsers = this.adminUsers.map(item => item.id === updated.id ? updated : item);
      },
      error: error => this.showError(error),
    });
  }

  removeUser(user: AdminUser): void {
    this.api.deleteUser(user.id).subscribe({
      next: () => { this.adminUsers = this.adminUsers.filter(item => item.id !== user.id); },
      error: error => this.showError(error),
    });
  }

  barWidth(kwh: number): number {
    return Math.max(2, Math.round((kwh / this.maxDailyKwh) * 100));
  }

  private refreshSummary(): void {
    this.api.getDashboard().subscribe({
      next: value => { this.summary = value; },
      error: error => this.showError(error),
    });
  }

  private setProfile(user: User): void {
    this.user = user;
    this.profileFirstname = user.firstname;
    this.profileLastname = user.lastname;
    this.profileEmail = user.email;
    this.auth.save({ token: this.auth.token ?? '', user });
  }

  private buildDailyTotals(readings: Reading[]): void {
    const totals = new Map<string, number>();
    readings.forEach(reading => {
      const day = reading.recordedAt.slice(0, 10);
      totals.set(day, (totals.get(day) ?? 0) + reading.consumptionKwh);
    });
    this.dailyTotals = Array.from(totals, ([date, kwh]) => ({ date, kwh }))
      .sort((a, b) => a.date.localeCompare(b.date))
      .slice(-14);
  }

  private showError(error: HttpErrorResponse): void {
    this.errorMessage = error.status === 0
      ? 'Le serveur est injoignable. Vérifiez les services API et base de données.'
      : error.status === 401
        ? 'Votre session a expiré. Reconnectez-vous.'
        : error.error?.detail ?? 'Une erreur est survenue lors de la requête.';
    if (error.status === 401) this.auth.logout();
  }
}
