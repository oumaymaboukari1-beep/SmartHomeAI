import { Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  standalone: false,
  template: `
    <ion-content>
      <main class="login-wrap">
        <ion-card>
          <ion-card-header>
            <p class="eyebrow">SMART HOME AI</p>
            <ion-card-title>{{ registering ? 'Créer un compte' : 'Bienvenue chez vous' }}</ion-card-title>
            <ion-card-subtitle>Suivez votre consommation d’énergie simplement.</ion-card-subtitle>
          </ion-card-header>
          <ion-card-content>
            <form (ngSubmit)="submit()">
              <ion-item *ngIf="registering">
                <ion-input label="Prénom" labelPlacement="stacked" name="firstname"
                  [(ngModel)]="firstname" required maxlength="80"></ion-input>
              </ion-item>
              <ion-item *ngIf="registering">
                <ion-input label="Nom" labelPlacement="stacked" name="lastname"
                  [(ngModel)]="lastname" required maxlength="80"></ion-input>
              </ion-item>
              <ion-item>
                <ion-input type="email" label="Adresse e-mail" labelPlacement="stacked" name="email"
                  [(ngModel)]="email" required autocomplete="email"></ion-input>
              </ion-item>
              <ion-item>
                <ion-input type="password" label="Mot de passe" labelPlacement="stacked" name="password"
                  [(ngModel)]="password" required [minlength]="registering ? 8 : 1"
                  autocomplete="current-password"></ion-input>
              </ion-item>
              <ion-text color="danger" *ngIf="errorMessage"><p>{{ errorMessage }}</p></ion-text>
              <ion-button expand="block" type="submit" [disabled]="loading" class="submit">
                {{ loading ? 'Veuillez patienter…' : registering ? 'Créer mon compte' : 'Se connecter' }}
              </ion-button>
            </form>
            <ion-button fill="clear" expand="block" (click)="toggleMode()" [disabled]="loading">
              {{ registering ? 'J’ai déjà un compte' : 'Créer un compte' }}
            </ion-button>
          </ion-card-content>
        </ion-card>
      </main>
    </ion-content>
  `,
  styles: [`
    .login-wrap { min-height: 100%; display: grid; place-items: center; padding: 20px; }
    ion-card { width: min(100%, 440px); padding: 14px; }
    .eyebrow { color: #167f78; font-size: .75rem; font-weight: 800; letter-spacing: .16em; }
    ion-item { margin: 8px 0; }
    .submit { margin-top: 20px; }
  `],
})
export class LoginPage {
  firstname = '';
  lastname = '';
  email = '';
  password = '';
  registering = false;
  loading = false;
  errorMessage = '';

  constructor(private readonly api: ApiService, private readonly auth: AuthService, private readonly router: Router) {}

  toggleMode(): void {
    this.registering = !this.registering;
    this.errorMessage = '';
  }

  submit(): void {
    if (this.loading) return;
    this.loading = true;
    this.errorMessage = '';
    const request = this.registering
      ? this.api.register(this.firstname, this.lastname, this.email, this.password)
      : this.api.login(this.email, this.password);
    request.subscribe({
      next: response => {
        this.auth.save(response);
        void this.router.navigateByUrl('/app');
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = error.status === 0
          ? 'Impossible de joindre le serveur. Vérifiez que l’API Spring Boot est démarrée.'
          : error.status === 409
            ? 'Cette adresse e-mail est déjà utilisée.'
            : error.status === 400
              ? 'Vérifiez les informations saisies (mot de passe : 8 caractères minimum).'
              : 'Connexion impossible. Vérifiez votre adresse e-mail et votre mot de passe.';
        this.loading = false;
      },
      complete: () => { this.loading = false; },
    });
  }
}
