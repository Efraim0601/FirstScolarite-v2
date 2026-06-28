import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AuditEventDto {
  id: string;
  kind: string;
  actor: string;
  target: string;
  partner: string;
  ts: string;
  level: 'info' | 'warning' | 'danger';
}

@Injectable({ providedIn: 'root' })
export class AuditApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  list(level = 'all', limit = 100): Observable<AuditEventDto[]> {
    return this.http
      .get<AuditEventDto[]>(`${this.base}/api/v1/audit`, { params: { level, limit } })
      .pipe(catchError(() => of([])));
  }

  log(action: string, targetType: string, targetId: string, partner?: string, detail?: string): Observable<void> {
    return this.http.post<void>(`${this.base}/api/v1/audit`, {
      action, targetType, targetId, partner, detail,
    }).pipe(catchError(() => of(undefined)));
  }
}
