import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, retry } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TenantStats { tpm: number; successRate: number; p99LatencyMs: number; tpmTrend?: number; }

/** Service HTTP + SSE pour le domaine Transaction. */
@Injectable({ providedIn: 'root' })
export class TransactionApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/transactions`;

  getAll(): Observable<unknown[]> {
    return this.http.get<unknown[]>(this.base);
  }

  create(req: Record<string, unknown>): Observable<{ externalRef?: string; id?: string }> {
    return this.http.post<{ externalRef?: string; id?: string }>(this.base, req, {
      headers: { 'X-Idempotency-Key': crypto.randomUUID() },
    });
  }

  /** Flux temps réel des stats via Server-Sent Events (reconnexion auto). */
  liveStats(): Observable<TenantStats> {
    return new Observable<TenantStats>((observer) => {
      const es = new EventSource(`${this.base}/live-stats`, { withCredentials: true });
      es.addEventListener('stats', (e) => observer.next(JSON.parse((e as MessageEvent).data)));
      es.onerror = (err) => observer.error(err);
      return () => es.close();
    }).pipe(retry({ delay: 3000 }));
  }
}
