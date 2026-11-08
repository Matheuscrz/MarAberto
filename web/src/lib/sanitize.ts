import DOMPurify from 'dompurify';

/**
 * Sanitização no cliente é defesa em profundidade, não a barreira principal.
 * O backend valida e sanitiza todo dado recebido; aqui evitamos renderizar
 * markup perigoso e nunca usamos HTML não confiável em `dangerouslySetInnerHTML`.
 *
 * `stripControlCharacters` remove caracteres de controle (exceto tabulação e
 * quebra de linha) sem usar regex de faixa de controle, que dispara o lint
 * `no-control-regex`.
 */
function stripControlCharacters(value: string): string {
  let result = '';
  for (const character of value) {
    const code = character.codePointAt(0) ?? 0;
    const isAllowedWhitespace = code === 0x09 || code === 0x0a;
    const isControl = (code < 0x20 && !isAllowedWhitespace) || code === 0x7f;
    if (!isControl) {
      result += character;
    }
  }
  return result;
}

export function sanitizeText(value: string, maxLength = 500): string {
  return stripControlCharacters(value.normalize('NFKC'))
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, maxLength);
}

export function sanitizeHtml(value: string): string {
  return DOMPurify.sanitize(value, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'ul', 'ol', 'li', 'blockquote', 'a'],
    ALLOWED_ATTR: ['href', 'rel', 'target'],
    ALLOW_DATA_ATTR: false,
    FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'svg'],
    FORBID_ATTR: ['style', 'onerror', 'onclick', 'onload'],
  });
}

export function safeExternalUrl(value: string): string | null {
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') {
      return null;
    }
    if (url.username || url.password) {
      return null;
    }
    return url.toString();
  } catch {
    return null;
  }
}

export function isValidYoutubeId(value: string): boolean {
  return /^[A-Za-z0-9_-]{11}$/.test(value);
}

export function maskCpf(value: string): string {
  const digits = value.replace(/\D/g, '');
  if (digits.length !== 11) {
    return '***';
  }
  return `***.${digits.slice(3, 6)}.${digits.slice(6, 9)}-**`;
}

export function safeProblemDetail(detail: string | undefined, fallback: string): string {
  if (!detail) {
    return fallback;
  }
  return sanitizeText(detail, 240) || fallback;
}
