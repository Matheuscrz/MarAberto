import { PhotoGallery } from "@/shared/components/PhotoGallery";

interface SignalPhotosProps {
  /** Caminhos devolvidos pelo backend (`/api/v1/media/…`). */
  readonly urls: readonly string[];
  /** Contexto do lançamento, usado no texto alternativo da foto. */
  readonly label: string;
  /** Miniatura pequena: usada na ficha da praia e na lista do console. */
  readonly compact?: boolean;
}

/**
 * Fotos de um lançamento de sinalização.
 *
 * <p>A foto é do lançamento (a bandeira plantada), não do guarda-vidas: o
 * servidor já removeu EXIF/GPS do arquivo e a imagem é servida pelo próprio
 * MarAberto, então o pin do mapa continua sendo a coordenada que o operador
 * enviou — nada é lido da imagem. A galeria é a mesma das estruturas.
 */
export function SignalPhotos({ urls, label, compact = false }: SignalPhotosProps) {
  return (
    <PhotoGallery
      urls={urls}
      label={`sinalização de ${label}`}
      compact={compact}
      openLabel="Ampliar foto da sinalização"
    />
  );
}
