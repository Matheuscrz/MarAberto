import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

/**
 * Deriva os assets de marca (PWA, web e Android) das artes originais.
 *
 * As artes-fonte ficam em `src/assets/` (JPEG do designer, sem transparência):
 *
 * - `Icone_mobile_pwa_{branco,escuro}.jpg` — símbolo quadrado (2048²)
 * - `Splash_Scree_fundo_{branco,escuro}.jpg` — splash retrato com símbolo + nome
 * - `logo_horizontal_web_fundo_{branco,escuro}.jpg` — assinatura horizontal
 *
 * Nada aqui inventa arte: o script só recorta o conteúdo, recentraliza e
 * redimensiona. O fundo de cada peça é amostrado da própria arte, então as
 * camadas do ícone adaptativo e as sobras não aparecem.
 *
 * Rode `npm run icons` depois de trocar qualquer arte.
 *
 * Saídas:
 * - `public/icons/*` — PWA (`manifest.webmanifest`) e `apple-touch-icon`
 * - `public/brand/*` — marca usada nas telas (mark e assinatura horizontal)
 * - `android/app/src/main/res/*` — launcher (legado, redondo e adaptativo, com
 *   variante noturna) e splash por densidade/orientação (claro e escuro)
 *
 * As artes-fonte não são versionadas; as saídas são. Sem os JPEGs o script
 * falha, mas o app continua buildando com o que já está gerado.
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = join(ROOT, 'src/assets');
const ANDROID_RES = join(ROOT, 'android/app/src/main/res');

const SOURCE = {
  iconLight: join(ASSETS, 'Icone_mobile_pwa_branco.jpg'),
  iconDark: join(ASSETS, 'Icone_mobile_pwa_escuro.jpg'),
  splashLight: join(ASSETS, 'Splash_Scree_fundo_branco.jpg'),
  splashDark: join(ASSETS, 'Splash_Scree_fundo_escuro.jpg'),
  logoLight: join(ASSETS, 'logo_horizontal_web_fundo_branco.jpg'),
  logoDark: join(ASSETS, 'logo_horizontal_web_fundo_escuro.jpg'),
};

/** Densidades do Android: fator em relação a mdpi. */
const DENSITIES = [
  ['mdpi', 1],
  ['hdpi', 1.5],
  ['xhdpi', 2],
  ['xxhdpi', 3],
  ['xxxhdpi', 4],
];

/** Tamanhos do splash que o Capacitor já usa, por densidade. */
const SPLASH_PORTRAIT = { mdpi: [320, 480], hdpi: [480, 800], xhdpi: [720, 1280], xxhdpi: [960, 1600], xxxhdpi: [1280, 1920] };

/** Fração do canvas ocupada pela diagonal do conteúdo. */
const SHARE = {
  pwa: 0.8,
  maskable: 0.72,
  appleTouch: 0.84,
  mark: 0.94,
  launcher: 0.86,
  // Ícone adaptativo: o conteúdo precisa caber nos 66dp centrais de 108dp.
  adaptive: 0.6,
};

const written = [];

async function write(path, data) {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, data);
  written.push(path.replace(`${ROOT}/`, ''));
}

/** Cor de fundo da arte, amostrada no canto superior esquerdo. */
async function backgroundOf(file) {
  const size = 16;
  const { data, info } = await sharp(file)
    .extract({ left: 0, top: 0, width: size, height: size })
    .raw()
    .toBuffer({ resolveWithObject: true });

  let r = 0;
  let g = 0;
  let b = 0;
  const pixels = info.width * info.height;
  for (let index = 0; index < pixels; index += 1) {
    const offset = index * info.channels;
    r += data[offset];
    g += data[offset + 1];
    b += data[offset + 2];
  }
  return {
    r: Math.round(r / pixels),
    g: Math.round(g / pixels),
    b: Math.round(b / pixels),
  };
}

/** Recorta o conteúdo da arte, descartando a moldura de fundo. */
async function trimToContent(file, threshold = 18) {
  const { data, info } = await sharp(file)
    .trim({ threshold })
    .toBuffer({ resolveWithObject: true });
  return { data, width: info.width, height: info.height };
}

/**
 * Símbolo centralizado em um canvas quadrado, com o fundo da própria arte.
 *
 * `share` é a fração da diagonal do canvas que o conteúdo ocupa — o jeito
 * previsível de manter o mesmo enquadramento em qualquer proporção de arte.
 */
async function squareMark(file, canvas, share) {
  const [background, art] = await Promise.all([backgroundOf(file), trimToContent(file)]);
  const scale = (canvas * share) / Math.hypot(art.width, art.height);
  const width = Math.max(1, Math.round(art.width * scale));
  const height = Math.max(1, Math.round(art.height * scale));
  const resized = await sharp(art.data).resize(width, height, { fit: 'fill' }).png().toBuffer();

  return sharp({
    create: { width: canvas, height: canvas, channels: 4, background: { ...background, alpha: 1 } },
  })
    .composite([
      {
        input: resized,
        left: Math.round((canvas - width) / 2),
        top: Math.round((canvas - height) / 2),
      },
    ])
    .png()
    .toBuffer();
}

/** Aplica a máscara circular do launcher redondo. */
async function toCircle(buffer, canvas) {
  const radius = canvas / 2;
  const mask = Buffer.from(
    `<svg width="${canvas}" height="${canvas}"><circle cx="${radius}" cy="${radius}" r="${radius}" fill="#fff"/></svg>`,
  );
  return sharp(buffer).composite([{ input: mask, blend: 'dest-in' }]).png().toBuffer();
}

/** Assinatura horizontal recortada, mantendo o fundo original. */
async function horizontalLogo(file, width) {
  const art = await trimToContent(file);
  return sharp(art.data)
    .resize({ width, fit: 'inside' })
    .png({ compressionLevel: 9 })
    .toBuffer();
}

/** Splash retrato: a arte fornecida já tem a composição (símbolo + nome). */
async function portraitSplash(file, width, height) {
  return sharp(file).resize(width, height, { fit: 'cover', position: 'centre' }).png().toBuffer();
}

/**
 * Splash paisagem: a arte é retrato, então em vez de cortar o símbolo
 * montamos fundo + assinatura horizontal centralizada.
 */
async function landscapeSplash(logoFile, width, height) {
  const background = await backgroundOf(logoFile);
  const logo = await horizontalLogo(logoFile, Math.round(width * 0.42));
  const { width: logoWidth, height: logoHeight } = await sharp(logo).metadata();

  return sharp({
    create: { width, height, channels: 4, background: { ...background, alpha: 1 } },
  })
    .composite([
      {
        input: logo,
        left: Math.round((width - logoWidth) / 2),
        top: Math.round((height - logoHeight) / 2),
      },
    ])
    .png()
    .toBuffer();
}

async function generatePwa() {
  const target = join(ROOT, 'public/icons');
  await write(join(target, 'icon-192.png'), await squareMark(SOURCE.iconLight, 192, SHARE.pwa));
  await write(join(target, 'icon-512.png'), await squareMark(SOURCE.iconLight, 512, SHARE.pwa));
  await write(
    join(target, 'icon-maskable-512.png'),
    await squareMark(SOURCE.iconLight, 512, SHARE.maskable),
  );
  await write(
    join(target, 'icon-180.png'),
    await squareMark(SOURCE.iconLight, 180, SHARE.appleTouch),
  );
  await write(join(target, 'icon-32.png'), await squareMark(SOURCE.iconLight, 32, SHARE.appleTouch));
}

async function generateWebBrand() {
  const target = join(ROOT, 'public/brand');
  for (const size of [96, 192]) {
    await write(join(target, `mark-${size}.png`), await squareMark(SOURCE.iconLight, size, SHARE.mark));
  }
  await write(join(target, 'logo-light.png'), await horizontalLogo(SOURCE.logoLight, 720));
  await write(join(target, 'logo-dark.png'), await horizontalLogo(SOURCE.logoDark, 720));
}

async function generateLauncher() {
  for (const [density, factor] of DENSITIES) {
    const legacy = Math.round(48 * factor);
    const light = await squareMark(SOURCE.iconLight, legacy, SHARE.launcher);
    const dark = await squareMark(SOURCE.iconDark, legacy, SHARE.launcher);
    const adaptiveLight = await squareMark(SOURCE.iconLight, Math.round(108 * factor), SHARE.adaptive);
    const adaptiveDark = await squareMark(SOURCE.iconDark, Math.round(108 * factor), SHARE.adaptive);

    await write(join(ANDROID_RES, `mipmap-${density}/ic_launcher.png`), light);
    await write(join(ANDROID_RES, `mipmap-${density}/ic_launcher_round.png`), await toCircle(light, legacy));
    await write(join(ANDROID_RES, `mipmap-${density}/ic_launcher_foreground.png`), adaptiveLight);
    // Variante noturna: mesma estrutura, arte escura. Harmless em quem não
    // aplica o qualificador; o ícone claro continua sendo o padrão.
    await write(join(ANDROID_RES, `mipmap-night-${density}/ic_launcher.png`), dark);
    await write(
      join(ANDROID_RES, `mipmap-night-${density}/ic_launcher_round.png`),
      await toCircle(dark, legacy),
    );
    await write(
      join(ANDROID_RES, `mipmap-night-${density}/ic_launcher_foreground.png`),
      adaptiveDark,
    );
  }
}

async function generateSplash() {
  const light = {};
  const dark = {};
  for (const [density] of DENSITIES) {
    const [width, height] = SPLASH_PORTRAIT[density];
    light[density] = {
      portrait: await portraitSplash(SOURCE.splashLight, width, height),
      landscape: await landscapeSplash(SOURCE.logoLight, height, width),
    };
    dark[density] = {
      portrait: await portraitSplash(SOURCE.splashDark, width, height),
      landscape: await landscapeSplash(SOURCE.logoDark, height, width),
    };

    await write(join(ANDROID_RES, `drawable-port-${density}/splash.png`), light[density].portrait);
    await write(join(ANDROID_RES, `drawable-land-${density}/splash.png`), light[density].landscape);
    await write(
      join(ANDROID_RES, `drawable-port-night-${density}/splash.png`),
      dark[density].portrait,
    );
    await write(
      join(ANDROID_RES, `drawable-land-night-${density}/splash.png`),
      dark[density].landscape,
    );
  }

  // Splash base (sem qualificador), usado por telas fora das densidades acima.
  await write(join(ANDROID_RES, 'drawable/splash.png'), light.mdpi.landscape);
  await write(join(ANDROID_RES, 'drawable-night/splash.png'), dark.mdpi.landscape);
}

async function generateAndroidColors() {
  // Fundo da camada do ícone adaptativo, igual ao fundo de cada arte.
  const light = await backgroundOf(SOURCE.iconLight);
  const dark = await backgroundOf(SOURCE.iconDark);
  const hex = ({ r, g, b }) =>
    `#${[r, g, b].map((value) => value.toString(16).padStart(2, '0')).join('').toUpperCase()}`;

  await write(
    join(ANDROID_RES, 'values/ic_launcher_background.xml'),
    `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">${hex(light)}</color>\n    <color name="splash_background">${hex(light)}</color>\n</resources>\n`,
  );
  await write(
    join(ANDROID_RES, 'values-night/ic_launcher_background.xml'),
    `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">${hex(dark)}</color>\n    <color name="splash_background">${hex(dark)}</color>\n</resources>\n`,
  );
}

await generatePwa();
await generateWebBrand();
await generateAndroidColors();
await generateLauncher();
await generateSplash();

console.log(`Assets gerados (${written.length}):`);
for (const path of written) console.log(`  ${path}`);
