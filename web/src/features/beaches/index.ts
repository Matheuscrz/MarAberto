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
export {
  useRiskZones,
  type RiskZonesState,
  type RiskZonesStatus,
} from "@/features/beaches/hooks/use-risk-zones";
export { areaShapes, sectorShapes } from "@/features/beaches/lib/beach-map";
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
  type RiskPersistence,
  type RiskType,
  type RiskZoneCollection,
  type RiskZoneProperties,
} from "@/features/beaches/types";
