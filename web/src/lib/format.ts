export function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'data indisponível';
  }
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function formatDay(value: string): string {
  const date = new Date(`${value}T12:00:00`);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
  }).format(date);
}

export function formatHeight(heightMeters: number): string {
  return `${heightMeters.toFixed(1).replace('.', ',')} m`;
}

export function formatAccuracy(accuracyMeters?: number): string {
  if (accuracyMeters === undefined || !Number.isFinite(accuracyMeters)) {
    return 'precisão desconhecida';
  }
  return `±${Math.round(accuracyMeters)} m`;
}

export function formatOccupancy(percent: number): string {
  return `${Math.round(percent)}%`;
}
