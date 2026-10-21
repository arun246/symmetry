export function formatDate(value?: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export function formatJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

export function formatCents(value: number | undefined, currency = "USD"): string {
  return new Intl.NumberFormat(undefined, { style: "currency", currency }).format((value ?? 0) / 100);
}

export function formatNumber(value: unknown): string {
  return new Intl.NumberFormat().format(Number(value ?? 0));
}
