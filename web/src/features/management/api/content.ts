import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type { ContentKind, EducationalContentItem } from "@/shared/types/content";

export interface ContentUpsertInput {
  readonly kind: ContentKind;
  readonly title: string;
  readonly description: string;
  readonly category: string;
  readonly tags: readonly string[];
  readonly externalUrl: string | null;
  readonly externalLinkLabel: string | null;
  readonly youtubeId: string | null;
  readonly duration: string | null;
  readonly displayOrder?: number;
  readonly published: boolean;
}

export async function fetchManagementContent(params?: {
  readonly kind?: ContentKind;
  readonly published?: boolean;
}): Promise<readonly EducationalContentItem[]> {
  const search = new URLSearchParams();
  if (params?.kind) {
    search.set("kind", params.kind);
  }
  if (params?.published !== undefined) {
    search.set("published", String(params.published));
  }
  const qs = search.toString();
  return apiRequest<readonly EducationalContentItem[]>(
    `/api/v1/management/content${qs ? `?${qs}` : ""}`,
    {},
  );
}

export async function createManagementContent(
  input: ContentUpsertInput,
): Promise<EducationalContentItem> {
  return apiRequest<EducationalContentItem>("/api/v1/management/content", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export async function updateManagementContent(
  id: string,
  input: ContentUpsertInput,
): Promise<EducationalContentItem> {
  return apiRequest<EducationalContentItem>(
    `/api/v1/management/content/${encodeURIComponent(id)}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
  );
}

export async function setManagementContentPublished(
  id: string,
  published: boolean,
): Promise<EducationalContentItem> {
  return apiRequest<EducationalContentItem>(
    `/api/v1/management/content/${encodeURIComponent(id)}/published`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ published }),
    },
  );
}

export async function reorderManagementContent(
  orderedIds: readonly string[],
): Promise<readonly EducationalContentItem[]> {
  return apiRequest<readonly EducationalContentItem[]>(
    "/api/v1/management/content/reorder",
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ orderedIds }),
    },
  );
}

export async function deleteManagementContent(id: string): Promise<void> {
  await apiRequest<void>(
    `/api/v1/management/content/${encodeURIComponent(id)}`,
    { method: "DELETE" },
  );
}

export function contentErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const firstFieldError = error.problem?.errors?.[0]?.message;
    return (
      firstFieldError ??
      error.problem?.detail ??
      error.problem?.title ??
      (error.network
        ? "Sem conexão com o servidor."
        : `Falha (${error.status}) ao salvar o conteúdo.`)
    );
  }
  return "Erro inesperado ao comunicar com o servidor.";
}
