export { OccurrenceList, OccurrenceEmptyHint } from "@/features/occurrences/components/OccurrenceList";
export { OccurrenceSheet } from "@/features/occurrences/components/OccurrenceSheet";
export { useOccurrences } from "@/features/occurrences/hooks/use-occurrences";
export type { OccurrencesState } from "@/features/occurrences/hooks/use-occurrences";
export {
  closeOccurrence,
  createOccurrence,
  fetchOccurrence,
  fetchOccurrenceByEmergencyCall,
  fetchOccurrences,
  occurrenceErrorMessage,
  revealOccurrenceVictim,
  updateOccurrence,
} from "@/features/occurrences/api/occurrences";
export type { OccurrenceQuery } from "@/features/occurrences/api/occurrences";
export {
  CIOSP_LABEL,
  NATURE_LABEL,
  ORIGIN_LABEL,
  OUTCOME_LABEL,
  STATUS_LABEL,
  isActiveOccurrence,
  type CiospStatus,
  type CloseOccurrenceInput,
  type CreateOccurrenceInput,
  type CreateOccurrenceResponse,
  type DuplicateCandidate,
  type ManualOccurrenceOrigin,
  type Occurrence,
  type OccurrenceCursor,
  type OccurrenceDetail,
  type OccurrenceNature,
  type OccurrenceOrigin,
  type OccurrenceOutcome,
  type OccurrencePage,
  type OccurrenceStatus,
  type UpdateOccurrenceInput,
  type VictimView,
} from "@/features/occurrences/types";
