export type DeviceKind = "mobile" | "desktop" | "unknown";

export interface DeviceDescriptor {
  readonly label: string;
  readonly kind: DeviceKind;
}

/**
 * Resume o cabeçalho `User-Agent` em uma identificação curta e legível para a
 * tela de dispositivos conectados (sem depender de bibliotecas pesadas).
 */
export function describeDevice(
  userAgent: string | null | undefined,
): DeviceDescriptor {
  const ua = userAgent?.trim() ?? "";
  if (!ua) {
    return { label: "Dispositivo não identificado", kind: "unknown" };
  }

  const lower = ua.toLowerCase();
  const os = detectOs(lower);
  const browser = detectBrowser(lower);

  const kind: DeviceKind =
    os === "Android" || os === "iPhone" || os === "iPad"
      ? "mobile"
      : os !== null
        ? "desktop"
        : "unknown";

  if (browser && os) {
    return { label: `${browser} · ${os}`, kind };
  }
  if (browser) {
    return { label: browser, kind };
  }
  if (os) {
    return { label: os, kind };
  }
  return { label: "Navegador ou aplicativo", kind };
}

function detectOs(ua: string): string | null {
  if (ua.includes("iphone")) return "iPhone";
  if (ua.includes("ipad")) return "iPad";
  if (ua.includes("android")) return "Android";
  if (ua.includes("windows")) return "Windows";
  if (ua.includes("mac os x") || ua.includes("macintosh")) return "macOS";
  if (ua.includes("cros")) return "ChromeOS";
  if (ua.includes("linux")) return "Linux";
  return null;
}

function detectBrowser(ua: string): string | null {
  if (ua.includes("wv)") || ua.includes("capacitor")) return "App MarAberto";
  if (ua.includes("edg/") || ua.includes("edga/") || ua.includes("edgios/")) {
    return "Edge";
  }
  if (ua.includes("opr/") || ua.includes("opera")) return "Opera";
  if (ua.includes("firefox/") || ua.includes("fxios/")) return "Firefox";
  if (ua.includes("chrome/") || ua.includes("crios/")) return "Chrome";
  if (ua.includes("safari/")) return "Safari";
  return null;
}
