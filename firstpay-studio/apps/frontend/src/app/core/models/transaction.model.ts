import { Method } from './interface.model';

export type TxStatus = 'success' | 'pending' | 'failed';

export interface Transaction {
  id: string;
  reference: string;
  payer: string;
  phone: string;
  interfaceId: string;
  interfaceName: string;
  method: Method;
  status: TxStatus;
  amount: number;
  date: string;       // ISO
  fields: Record<string, string>;
}
