import { sanitizeText } from "@/shared/lib/sanitize";
import type { MapMarker, MapMarkerKind } from "@/features/map/types";
import { MARKER_SYMBOLS, type SymbolKey } from "@/features/map/lib/marker-symbols";

const SVG_NS = "http://www.w3.org/2000/svg";

/** Cor de fallback por tipo, caso o marcador não traga uma cor explícita. */
const DEFAULT_PIN_COLORS: Record<MapMarkerKind, string> = {
  STATION_FIXED: "#1c6f8d",
  STATION_MOBILE: "#d6923f",
  SIGNAL: "#ef4444",
};

function createSymbolSvg(key: SymbolKey): SVGSVGElement {
  const svg = document.createElementNS(SVG_NS, "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("fill", "none");
  svg.setAttribute("stroke", "currentColor");
  svg.setAttribute("stroke-width", "2");
  svg.setAttribute("stroke-linecap", "round");
  svg.setAttribute("stroke-linejoin", "round");
  svg.setAttribute("aria-hidden", "true");

  for (const primitive of MARKER_SYMBOLS[key]) {
    if (primitive.kind === "path") {
      const path = document.createElementNS(SVG_NS, "path");
      path.setAttribute("d", primitive.d);
      svg.appendChild(path);
    } else if (primitive.kind === "circle") {
      const circle = document.createElementNS(SVG_NS, "circle");
      circle.setAttribute("cx", String(primitive.cx));
      circle.setAttribute("cy", String(primitive.cy));
      circle.setAttribute("r", String(primitive.r));
      svg.appendChild(circle);
    } else {
      const line = document.createElementNS(SVG_NS, "line");
      line.setAttribute("x1", String(primitive.x1));
      line.setAttribute("y1", String(primitive.y1));
      line.setAttribute("x2", String(primitive.x2));
      line.setAttribute("y2", String(primitive.y2));
      svg.appendChild(line);
    }
  }

  return svg;
}

export interface MarkerHandles {
  readonly element: HTMLButtonElement;
  readonly inner: HTMLSpanElement;
}

function symbolOf(kind: MapMarkerKind): SymbolKey {
  switch (kind) {
    case "STATION_FIXED":
      return "STATION_FIXED";
    case "STATION_MOBILE":
      return "STATION_MOBILE";
    case "SIGNAL":
      return "SIGNAL";
  }
}

function accessibleLabel(marker: MapMarker): string {
  const label = sanitizeText(marker.label, 60);
  switch (marker.kind) {
    case "STATION_FIXED":
      return `Posto de guarda-vidas: ${label}`;
    case "STATION_MOBILE":
      return `Base móvel de guarda-vidas: ${label}`;
    case "SIGNAL":
      return `Sinalização com bandeira: ${label}`;
  }
}

/**
 * Monta o conteúdo visual dentro do `.mld-marker-inner`. A raiz do marcador é
 * só âncora: o MapLibre escreve `transform: translate(...)` inline nela, então
 * hover/seleção/escala acontecem sempre neste filho.
 */
function renderInner(inner: HTMLSpanElement, marker: MapMarker): void {
  inner.replaceChildren();
  inner.removeAttribute("style");

  const pin = document.createElement("span");
  pin.className = "mld-map-pin pointer-events-none";
  pin.style.setProperty(
    "--pin-color",
    marker.color ?? DEFAULT_PIN_COLORS[marker.kind],
  );

  if (marker.kind === "STATION_MOBILE") {
    pin.classList.add("mld-map-pin--dark-icon");
  }

  pin.appendChild(createSymbolSvg(symbolOf(marker.kind)));
  inner.appendChild(pin);

  if (marker.kind === "SIGNAL" && marker.hasPhoto) {
    const badge = document.createElement("span");
    badge.className = "mld-photo-badge pointer-events-none";
    badge.appendChild(createSymbolSvg("CAMERA"));
    inner.appendChild(badge);
  }
}

/** Todos os pinos apontam a ponta para a coordenada. */
export function markerAnchor(): "bottom" {
  return "bottom";
}

/**
 * Cria o marcador uma única vez. Atualizações (posição, cor, selo de foto)
 * reaproveitam o mesmo elemento via `updateMarkerElement`.
 */
export function createMarkerElement(
  marker: MapMarker,
  onSelect: (id: string) => void,
): MarkerHandles {
  const button = document.createElement("button");
  button.type = "button";
  button.dataset.mapMarker = "true";
  button.className = "mld-marker";
  button.setAttribute("aria-label", accessibleLabel(marker));

  const inner = document.createElement("span");
  inner.className = "mld-marker-inner";
  renderInner(inner, marker);
  button.appendChild(inner);

  button.addEventListener("click", (event) => {
    event.preventDefault();
    event.stopPropagation();
    if ("stopImmediatePropagation" in event) {
      event.stopImmediatePropagation();
    }
    onSelect(marker.id);
  });

  return { element: button, inner };
}

export function updateMarkerElement(
  handles: MarkerHandles,
  marker: MapMarker,
): void {
  handles.element.setAttribute("aria-label", accessibleLabel(marker));
  renderInner(handles.inner, marker);
}
