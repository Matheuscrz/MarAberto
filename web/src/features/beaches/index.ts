export { BeachesPage } from "@/features/beaches/pages/BeachesPage";
export { BeachDetailPage } from "@/features/beaches/pages/BeachDetailPage";
export { BeachSheet } from "@/features/beaches/components/BeachSheet";
export { useNearbyBeaches } from "@/features/beaches/hooks/use-nearby-beaches";
export type {
  BeachesOutletContext,
  NearbyBeachesState,
  NearbyBeachesStatus,
} from "@/features/beaches/hooks/use-nearby-beaches";
export { useBeachMap } from "@/features/beaches/hooks/use-beach-map";
export { areaShapes, sectorShapes } from "@/features/beaches/lib/beach-map";
export { riskZonesOf } from "@/features/beaches/lib/risk-zones";
export {
  BEACH_KIND_LABEL,
  boundsTuple,
  sectorLabel,
  type Beach,
  type BeachDetail,
  type BeachKind,
  type BeachMap,
  type BeachMapBounds,
  type BeachMapSector,
  type BeachPolygon,
  type BeachPosition,
  type BeachSector,
  type BoundsTuple,
  type NearbyBeaches,
  type RiskType,
  type RiskZone,
} from "@/features/beaches/types";
