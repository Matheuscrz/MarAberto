import type { Beach, EducationalVideo, TideDay } from "../types";

export const DEMO_DATA_NOTICE =
  "A informação oficial é a do posto de guarda-vidas e a do 193.";

export const beaches: Beach[] = [];

export const tides: TideDay[] = [];

export const educationalVideos: EducationalVideo[] = [];

export function findBeach(id: string | undefined): Beach | undefined {
  return beaches.find((beach) => beach.id === id);
}
