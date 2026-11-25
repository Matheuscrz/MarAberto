import { apiRequest } from "@/shared/lib/api-client";
import type {
  ContentKind,
  EducationalContentItem,
} from "@/features/learn/types";

export async function fetchPublicContent(
  kind?: ContentKind,
): Promise<readonly EducationalContentItem[]> {
  const query = kind ? `?kind=${encodeURIComponent(kind)}` : "";
  return apiRequest<readonly EducationalContentItem[]>(
    `/api/v1/content${query}`,
    {},
  );
}
