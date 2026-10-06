import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { AuthResponse, User } from './models';

const TOKEN_KEY = 'smarthome-token';
const USER_KEY = 'smarthome-user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private readonly router: Router) {}

  get token(): string | null { return localStorage.getItem(TOKEN_KEY); }
  get user(): User | null {
    const saved = localStorage.getItem(USER_KEY);
    if (!saved) return null;
    try {
      return JSON.parse(saved) as User;
    } catch {
      this.clearSession();
      return null;
    }
  }
  get isAuthenticated(): boolean { return this.token !== null; }

  save(response: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
  }

  logout(): void {
    this.clearSession();
    void this.router.navigateByUrl('/login');
  }

  private clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }
}
