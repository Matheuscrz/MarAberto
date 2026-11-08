import { deflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Gera os ícones PNG do PWA sem dependências externas (sem ImageMagick).
 *
 * O manifesto referencia ícones PNG porque a instalação em Android/Chrome e o
 * apple-touch-icon esperam bitmap com tamanho declarado. A arte é a mesma do
 * favicon.svg: fundo oceano, três ondas e um ponto coral.
 *
 * Rode `npm run icons` sempre que a arte mudar.
 */
const OUT_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '../public/icons');

const COLORS = {
  top: [28, 111, 141],
  bottom: [13, 40, 54],
  waves: [
    [238, 248, 251, 1],
    [214, 239, 246, 0.95],
    [122, 199, 221, 0.9],
  ],
  sun: [229, 31, 17, 1],
};

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let index = 0; index < 256; index += 1) {
    let value = index;
    for (let bit = 0; bit < 8; bit += 1) {
      value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
    }
    table[index] = value >>> 0;
  }
  return table;
})();

function crc32(buffer) {
  let crc = 0xffffffff;
  for (let index = 0; index < buffer.length; index += 1) {
    crc = CRC_TABLE[(crc ^ buffer[index]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const typeBuffer = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])));
  return Buffer.concat([length, typeBuffer, data, crc]);
}

function encodePng(width, height, rgba) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // bit depth
  header[9] = 6; // RGBA
  header[10] = 0;
  header[11] = 0;
  header[12] = 0;

  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y += 1) {
    raw[y * (stride + 1)] = 0;
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

const clamp01 = (value) => Math.min(1, Math.max(0, value));
const blend = (base, color, coverage) => [
  base[0] + (color[0] - base[0]) * coverage,
  base[1] + (color[1] - base[1]) * coverage,
  base[2] + (color[2] - base[2]) * coverage,
];

function renderIcon(size, { rounded, inset }) {
  const radius = rounded ? size * 0.19 : 0;
  const contentSize = size * (1 - 2 * inset);
  const origin = size * inset;
  const strokeHalf = size * 0.022;
  const waveBases = [0.55, 0.68, 0.81];
  const wavePhases = [0, 0.7, 1.4];
  const sunRadius = size * 0.072;
  const rgba = Buffer.alloc(size * size * 4);

  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      // 2x2 supersampling suaviza bordas curvas e ondas.
      let red = 0;
      let green = 0;
      let blue = 0;
      let alpha = 0;

      for (const [ox, oy] of [
        [0.25, 0.25],
        [0.75, 0.25],
        [0.25, 0.75],
        [0.75, 0.75],
      ]) {
        const px = x + ox;
        const py = y + oy;

        if (rounded) {
          const dx = Math.max(radius - px, 0, px - (size - radius));
          const dy = Math.max(radius - py, 0, py - (size - radius));
          if (dx * dx + dy * dy > radius * radius) {
            continue;
          }
        }

        const t = py / size;
        let color = [
          COLORS.top[0] + (COLORS.bottom[0] - COLORS.top[0]) * t,
          COLORS.top[1] + (COLORS.bottom[1] - COLORS.top[1]) * t,
          COLORS.top[2] + (COLORS.bottom[2] - COLORS.top[2]) * t,
        ];

        const u = (px - origin) / contentSize;
        const v = (py - origin) / contentSize;

        if (u >= 0 && u <= 1 && v >= 0 && v <= 1) {
          for (let index = 0; index < waveBases.length; index += 1) {
            const curve =
              waveBases[index] +
              0.05 * Math.sin(2 * Math.PI * 1.6 * u + wavePhases[index]);
            const distance = Math.abs(v - curve) * contentSize;
            const coverage = clamp01((strokeHalf - distance) / 1.5 + 0.5);
            if (coverage > 0) {
              const wave = COLORS.waves[index];
              color = blend(color, wave, coverage * wave[3]);
            }
          }

          const sunDistance = Math.hypot(
            (u - 0.77) * contentSize,
            (v - 0.25) * contentSize,
          );
          const sunCoverage = clamp01((sunRadius - sunDistance) / 1.5 + 0.5);
          if (sunCoverage > 0) {
            color = blend(color, COLORS.sun, sunCoverage);
          }
        }

        red += color[0];
        green += color[1];
        blue += color[2];
        alpha += 255;
      }

      const offset = (y * size + x) * 4;
      const samples = 4;
      const visible = alpha / 255;
      if (visible === 0) {
        rgba[offset] = 0;
        rgba[offset + 1] = 0;
        rgba[offset + 2] = 0;
        rgba[offset + 3] = 0;
      } else {
        rgba[offset] = Math.round(red / visible);
        rgba[offset + 1] = Math.round(green / visible);
        rgba[offset + 2] = Math.round(blue / visible);
        rgba[offset + 3] = Math.round((alpha / samples) | 0);
      }
    }
  }

  return encodePng(size, size, rgba);
}

const targets = [
  { file: 'icon-192.png', size: 192, rounded: true, inset: 0.08 },
  { file: 'icon-512.png', size: 512, rounded: true, inset: 0.08 },
  { file: 'icon-maskable-512.png', size: 512, rounded: false, inset: 0.16 },
  { file: 'icon-180.png', size: 180, rounded: false, inset: 0.1 },
];

mkdirSync(OUT_DIR, { recursive: true });

for (const target of targets) {
  const png = renderIcon(target.size, target);
  writeFileSync(resolve(OUT_DIR, target.file), png);
  console.log(`gerado public/icons/${target.file} (${png.length} bytes)`);
}
