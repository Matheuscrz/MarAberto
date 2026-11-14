import { ApiError, apiRequest } from "@/shared/lib/api-client";

/**
 * Foto da sinalização enviada ao backend.
 *
 * <p>O envio é sempre em base64, nunca multipart: no app empacotado o HTTP é o
 * nativo (`CapacitorHttp`) e o corpo precisa ser uma string reenviável, porque
 * um 401 no meio do turno repete a requisição depois de renovar a sessão. O
 * servidor decodifica, remove EXIF/GPS, reduz o maior lado e guarda o JPEG.
 */
export const MAX_PHOTO_BYTES = 10 * 1024 * 1024;

export const ACCEPTED_PHOTO_TYPES = ["image/jpeg", "image/png"] as const;

/** Quantas fotos um lançamento de sinalização aceita. */
export const MAX_SIGNAL_PHOTOS = 3;

export interface UploadedPhoto {
  readonly id: string;
  readonly url: string;
  readonly contentType: string;
  readonly widthPx: number | null;
  readonly heightPx: number | null;
  readonly sizeBytes: number;
}

/** Motivo para recusar o arquivo antes de enviar; `null` quando está tudo bem. */
export function photoProblem(file: File): string | null {
  if (file.size === 0) {
    return "O arquivo está vazio.";
  }
  if (!(ACCEPTED_PHOTO_TYPES as readonly string[]).includes(file.type)) {
    return "Envie uma foto JPEG ou PNG.";
  }
  if (file.size > MAX_PHOTO_BYTES) {
    return "A foto deve ter no máximo 10 MB.";
  }
  return null;
}

/** Envia um arquivo e devolve o identificador usado no lançamento. */
export async function uploadPhoto(file: File): Promise<UploadedPhoto> {
  const problem = photoProblem(file);
  if (problem) {
    throw new Error(problem);
  }
  return apiRequest<UploadedPhoto>(
    "/api/v1/media",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ contentBase64: await readAsBase64(file) }),
    },
    // A rede do celular em campo é lenta; o envio da foto ganha mais folga que
    // uma consulta comum.
    30_000,
  );
}

export function mediaErrorMessage(error: unknown): string {
  if (error instanceof Error && !(error instanceof ApiError)) {
    return error.message;
  }
  if (error instanceof ApiError) {
    return (
      error.problem?.detail ??
      error.problem?.title ??
      (error.network
        ? "Sem conexão com o servidor."
        : `Falha (${error.status}) ao enviar a foto.`)
    );
  }
  return "Não foi possível enviar a foto agora.";
}

function readAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Não foi possível ler o arquivo."));
    reader.onload = () => {
      const result = typeof reader.result === "string" ? reader.result : "";
      const separator = result.indexOf(",");
      if (separator < 0) {
        reject(new Error("Não foi possível ler o arquivo."));
        return;
      }
      resolve(result.slice(separator + 1));
    };
    reader.readAsDataURL(file);
  });
}
