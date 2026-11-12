export { BeachesPage } from "@/features/beaches/pages/BeachesPage";
export { BeachDetailPage } from "@/features/beaches/pages/BeachDetailPage";
export { BeachSheet } from "@/features/beaches/components/BeachSheet";
export { useNearbyBeaches } from "@/features/beaches/hooks/use-nearby-beaches";
export type {
  BeachesOutletContext,
  NearbyBeachesState,
  NearbyBeachesStatus,
} from "@/features/beaches/hooks/use-nearby-beaches";
export { riskZonesOf } from "@/features/beaches/lib/risk-zones";
export {
  BEACH_KIND_LABEL,
  sectorLabel,
  type Beach,
  type BeachDetail,
  type BeachKind,
  type BeachSector,
  type RiskType,
  type RiskZone,
} from "@/features/beaches/types";
