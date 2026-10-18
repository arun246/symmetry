export interface Contract {
  id: string;
  moduleId: string;
  version: string;
  taskKind: string;
  language: string;
  workKind: string;
  lifecycleState: string;
  disclosureTier: string;
  taskDocument: string;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export async function listContracts(page = 0, size = 50): Promise<Page<Contract>> {
  const response = await fetch(`/api/contracts?page=${page}&size=${size}`);
  if (!response.ok) {
    throw new Error(`Failed to load contracts (${response.status})`);
  }
  return response.json();
}
