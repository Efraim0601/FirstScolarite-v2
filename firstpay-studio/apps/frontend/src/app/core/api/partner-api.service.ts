import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PaymentInterface } from '../models/interface.model';
import { Transaction } from '../models/transaction.model';

export interface ApiInterfaceDto {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  sector?: string;
  slug: string;
  customSlug?: string;
  status: string;
  tx: number;
  collected: number;
  amountType: string;
  fixedAmount?: string;
  minAmount?: string;
  maxAmount?: string;
  currency: string;
  presets: { id: number; label: string; amount: string }[];
  multiSelect: boolean;
  refType: string;
  refLabel?: string;
  refFormat?: string;
  customFields: { id: string; type: string; label: string; required: boolean; options?: string[] }[];
  methods: Record<string, boolean>;
  qrCodes: Record<string, boolean>;
}

export interface ApiTransactionDto {
  id: string;
  tenantId?: string;
  interfaceId?: string;
  externalRef: string;
  amount: number;
  currency: string;
  status: string;
  type: string;
  method?: string;
  payer?: string;
  reference?: string;
  phone?: string;
  createdAt: string;
  processedAt?: string;
}

export interface PartnerListDto {
  id: string;
  code: string;
  shortCode: string;
  name: string;
  sector: string;
  status: string;
  interfaceCount: number;
}

export interface CreatePartnerRequest {
  name: string;
  sector: string;
  adminName: string;
  adminEmail: string;
  settlementAccount: string;
  accountHolder: string;
  settlementBank: string;
}

export interface CreatePartnerResponse {
  partner: PartnerListDto;
  apiKey: string;
  adminEmail?: string;
  tempPassword?: string;
}

@Injectable({ providedIn: 'root' })
export class PartnerApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  getInterfaces(): Observable<PaymentInterface[]> {
    return this.http.get<ApiInterfaceDto[]>(`${this.base}/api/v1/interfaces`).pipe(
      catchError(() => of([])),
      // map in pipe via switchMap alternative - use map operator
    ) as unknown as Observable<PaymentInterface[]>;
  }

  fetchInterfaces(): Observable<PaymentInterface[]> {
    return new Observable((observer) => {
      this.http.get<ApiInterfaceDto[]>(`${this.base}/api/v1/interfaces`).subscribe({
        next: (rows) => { observer.next(rows.map(mapInterface)); observer.complete(); },
        error: () => { observer.next([]); observer.complete(); },
      });
    });
  }

  fetchTransactions(): Observable<Transaction[]> {
    return new Observable((observer) => {
      this.http.get<ApiTransactionDto[]>(`${this.base}/api/v1/transactions?limit=200`).subscribe({
        next: (rows) => { observer.next(rows.map(mapTransaction)); observer.complete(); },
        error: () => { observer.next([]); observer.complete(); },
      });
    });
  }

  saveInterface(payload: Partial<PaymentInterface>): Observable<PaymentInterface | null> {
    const body = toSavePayload(payload);
    const isNew = !payload.id || payload.id.startsWith('new-');
    const url = isNew
      ? `${this.base}/api/v1/interfaces`
      : `${this.base}/api/v1/interfaces/${payload.id}`;
    const req = isNew
      ? this.http.post<ApiInterfaceDto>(url, body)
      : this.http.put<ApiInterfaceDto>(url, body);
    return new Observable((observer) => {
      req.subscribe({
        next: (row) => { observer.next(mapInterface(row)); observer.complete(); },
        error: () => { observer.next(null); observer.complete(); },
      });
    });
  }

  deleteInterface(id: string): Observable<boolean> {
    return new Observable((observer) => {
      this.http.delete(`${this.base}/api/v1/interfaces/${id}`).subscribe({
        next: () => { observer.next(true); observer.complete(); },
        error: () => { observer.next(false); observer.complete(); },
      });
    });
  }

  listPartners(): Observable<PartnerListDto[]> {
    return this.http.get<PartnerListDto[]>(`${this.base}/api/v1/partners`).pipe(
      catchError(() => of([])),
    );
  }

  /** Création d'un partenaire (admin banque). Renvoie le partenaire + l'API-key (une fois). */
  createPartner(req: CreatePartnerRequest): Observable<CreatePartnerResponse | null> {
    return this.http.post<CreatePartnerResponse>(`${this.base}/api/v1/partners`, req).pipe(
      catchError(() => of(null)),
    );
  }

  fetchUsers(): Observable<ApiUserDto[] | null> {
    return this.http.get<ApiUserDto[]>(`${this.base}/api/v1/users`).pipe(
      catchError(() => of(null)),
    );
  }

  saveUser(user: ApiUserDto): Observable<ApiUserDto | null> {
    return this.http.post<ApiUserDto>(`${this.base}/api/v1/users`, user).pipe(
      catchError(() => of(null)),
    );
  }

  deleteUser(id: string): Observable<boolean> {
    return this.http.delete(`${this.base}/api/v1/users/${id}`).pipe(
      map(() => true),
      catchError(() => of(false)),
    );
  }

  fetchSettings(): Observable<ApiSettingsDto | null> {
    return this.http.get<ApiSettingsDto>(`${this.base}/api/v1/settings`).pipe(
      catchError(() => of(null)),
    );
  }

  saveSettings(settings: ApiSettingsDto): Observable<ApiSettingsDto | null> {
    return this.http.put<ApiSettingsDto>(`${this.base}/api/v1/settings`, settings).pipe(
      catchError(() => of(null)),
    );
  }
}

export interface ApiUserDto {
  id: string;
  name: string;
  email: string;
  role: string;
  status: string;
}

export interface ApiSettingsDto {
  tenantId?: string;
  logoUrl?: string | null;
  logoName?: string | null;
  brandColor: string;
  notifications: Record<string, unknown>;
}

function mapInterface(d: ApiInterfaceDto): PaymentInterface {
  return {
    id: d.id,
    name: d.name,
    description: d.description ?? '',
    sector: d.sector ?? '',
    slug: d.slug,
    customSlug: d.customSlug ?? d.slug,
    status: d.status as PaymentInterface['status'],
    tx: d.tx,
    collected: d.collected,
    amountType: d.amountType as PaymentInterface['amountType'],
    fixedAmount: d.fixedAmount ?? '',
    minAmount: d.minAmount ?? '',
    maxAmount: d.maxAmount ?? '',
    currency: d.currency,
    presets: d.presets ?? [],
    multiSelect: d.multiSelect,
    refType: d.refType as PaymentInterface['refType'],
    refLabel: d.refLabel ?? '',
    refFormat: d.refFormat ?? 'any',
    customFields: (d.customFields ?? []).map((f) => ({
      id: f.id,
      type: f.type as 'text' | 'select',
      label: f.label,
      required: f.required,
      options: f.options,
    })),
    methods: {
      orange: !!d.methods?.['orange'],
      mtn: !!d.methods?.['mtn'],
      card: !!d.methods?.['card'],
      transfer: !!d.methods?.['transfer'],
    },
    qrCodes: d.qrCodes ?? {},
  };
}

function mapTransaction(d: ApiTransactionDto): Transaction {
  const status = d.status.toLowerCase() as Transaction['status'];
  return {
    id: d.id,
    reference: d.reference ?? d.externalRef,
    payer: d.payer ?? '—',
    phone: d.phone ?? '',
    interfaceId: d.interfaceId ?? '',
    interfaceName: '',
    method: (d.method ?? 'orange') as Transaction['method'],
    status: status === 'success' ? 'success' : status === 'failed' ? 'failed' : status === 'pending' ? 'pending' : 'pending',
    amount: Number(d.amount),
    date: d.createdAt,
    fields: {},
  };
}

function toSavePayload(p: Partial<PaymentInterface>) {
  return {
    id: p.id?.startsWith('new-') ? null : p.id,
    name: p.name,
    description: p.description,
    sector: p.sector,
    customSlug: p.customSlug,
    status: p.status,
    amountType: p.amountType,
    fixedAmount: p.fixedAmount,
    minAmount: p.minAmount,
    maxAmount: p.maxAmount,
    currency: p.currency,
    presets: p.presets,
    multiSelect: p.multiSelect,
    refType: p.refType,
    refLabel: p.refLabel,
    refFormat: p.refFormat,
    customFields: p.customFields,
    methods: p.methods,
    qrCodes: p.qrCodes,
  };
}
