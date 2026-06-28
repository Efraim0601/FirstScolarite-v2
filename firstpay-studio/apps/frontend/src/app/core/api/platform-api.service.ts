import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PlatformSettings {
  smtpHost: string;
  smtpPort: number;
  smtpUsername: string;
  smtpPassword: string;
  smtpFromEmail: string;
  smtpFromName: string;
  smtpUseTls: boolean;
  smtpEnabled: boolean;
  appBaseUrl: string;
  passwordSet?: boolean;
  aggEnabled: boolean;
  aggBaseUrl: string;
  aggAppId: string;
  aggSecret: string;
  aggSecretSet?: boolean;
}

/** Paramètres plateforme (config SMTP) — réservés à l'admin banque. */
@Injectable({ providedIn: 'root' })
export class PlatformApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/settings/platform`;

  get(): Observable<PlatformSettings | null> {
    return this.http.get<PlatformSettings>(this.base).pipe(catchError(() => of(null)));
  }

  save(settings: PlatformSettings): Observable<PlatformSettings | null> {
    return this.http.put<PlatformSettings>(this.base, settings).pipe(catchError(() => of(null)));
  }

  test(to: string): Observable<{ sent: boolean } | null> {
    return this.http.post<{ sent: boolean }>(`${this.base}/test`, { to }).pipe(catchError(() => of(null)));
  }
}
