export { RequireRole } from "@/features/auth/guards";
export { useSessionBootstrap } from "@/features/auth/session-boot";
export { installAuthBridge } from "@/features/auth/api-auth";
export { useLogout } from "@/features/auth/use-logout";
export { MfaSettingsCard } from "@/features/auth/components/MfaSettingsCard";
export {
  useAuthSession,
  useCurrentUser,
  useHasRole,
  currentAccessToken,
} from "@/features/auth/session";
export {
  ROLE_HOME,
  ROLE_LABEL,
  ROLE_AREA_LABEL,
  buildUnauthenticatedLoginTarget,
  homePathForRole,
  isInternalPath,
  isPostLoginRedirectPath,
  resolvePostLoginTarget,
} from "@/features/auth/lib/role-home";
export { DEMO_AUTH_ENABLED } from "@/features/auth/demo";
export type {
  AuthSession,
  LoginCredentials,
  OperatorUser,
  Role,
} from "@/features/auth/types";
