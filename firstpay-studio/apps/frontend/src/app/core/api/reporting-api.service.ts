import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, retry } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ReportSummary {
  totalTx: number;
  successCount: number;
  failedCount: number;
  amountTotal: number;
  successRate: number;
}

export interface LiveStats {
  tpm: number;
  successRate: number;
  p99LatencyMs: number;
}

@Injectable({ providedIn: 'root' })
export class ReportingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/reports`;

  summary(): Observable<ReportSummary | null> {
    return this.http.get<ReportSummary>(`${this.base}/summary`).pipe(catchError(() => of(null)));
  }

  /** Flux SSE temps réel (reporting-service). */
  liveStats(): Observable<LiveStats> {
    return new Observable<LiveStats>((observer) => {
      const es = new EventSource(`${this.base}/live-stats`, { withCredentials: true });
      es.addEventListener('stats', (e) => observer.next(JSON.parse((e as MessageEvent).data)));
      es.onerror = (err) => observer.error(err);
      return () => es.close();
    }).pipe(retry({ delay: 3000 }));
  }
}
