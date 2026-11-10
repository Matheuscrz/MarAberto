export interface EducationalVideo {
  readonly id: string;
  readonly title: string;
  readonly description: string;
  readonly topic: string;
  readonly duration: string;
}

export interface RiskGuide {
  readonly id: string;
  readonly category:
    | "RIP_CURRENT"
    | "WILDLIFE"
    | "SURF_HOLES"
    | "POLLUTION"
    | "GENERAL";
  readonly categoryLabel: string;
  readonly title: string;
  readonly description: string;
  readonly howToIdentify: readonly string[];
  readonly whatToDoIfAffected: readonly string[];
  readonly prevention: readonly string[];
  readonly video?: {
    readonly youtubeId: string;
    readonly title: string;
    readonly duration: string;
  };
}
