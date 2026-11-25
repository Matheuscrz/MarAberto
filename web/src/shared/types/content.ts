export type ContentKind = "VIDEO" | "GUIDE" | "CAMPAIGN";

export interface EducationalContentItem {
  readonly id: string;
  readonly kind: ContentKind;
  readonly title: string;
  readonly description: string;
  readonly category: string;
  readonly tags: readonly string[];
  readonly externalUrl: string | null;
  readonly externalLinkLabel: string | null;
  readonly youtubeId: string | null;
  readonly duration: string | null;
  readonly displayOrder: number;
  readonly published: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}
