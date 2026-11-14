import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

/**
 * Fronteiras do frontend feature-based (ADR-015).
 *
 * - `shared/` nunca importa de `app/` nem de `features/`.
 * - Uma feature importa de si mesma e de `shared/`; entre features só pelas
 *   arestas permitidas (`operator/management -> occurrences`, `admin -> stations`,
 *   qualquer feature -> auth). Toda outra composição fica em `app/`.
 */
const FEATURES = [
  'map',
  'beaches',
  'stations',
  'signals',
  'sos',
  'tides',
  'learn',
  'search',
  'auth',
  'occurrences',
  'operator',
  'management',
  'admin',
]

const ALLOWED_CROSS_FEATURE_IMPORTS = {
  // O operador registra ocorrências, lança a bandeira e escolhe a praia/setor da
  // ocorrência. A ficha da praia mostra a sinalização vigente/desatualizada. A
  // coordenação revisa ocorrências; a TI mantém postos/bases.
  operator: ['occurrences', 'stations', 'beaches', 'signals'],
  beaches: ['signals'],
  management: ['occurrences', 'beaches', 'signals'],
  admin: ['stations'],
}

/**
 * O WebView (Capacitor, ADR-019) é detalhe de plataforma: só
 * `shared/lib/platform.ts` (detecção) e `shared/lib/native.ts` (adaptadores)
 * falam com ele. O resto do app usa `shared/lib/geolocation.ts` e
 * `shared/lib/pwa.ts`, que têm implementação web como padrão.
 */
const CAPACITOR_IMPORT = {
  group: ['@capacitor/*'],
  message:
    'Só shared/lib/platform.ts e shared/lib/native.ts importam @capacitor/*: use os adaptadores de shared/ (ADR-019).',
}

const featureBoundaryConfigs = FEATURES.map((feature) => {
  const allowed = new Set([
    feature,
    'auth',
    ...(ALLOWED_CROSS_FEATURE_IMPORTS[feature] ?? []),
  ])

  return {
    files: [`src/features/${feature}/**/*.{ts,tsx}`],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/app/*'],
              message:
                'Uma feature não importa de app/: a composição acontece em app/.',
            },
            ...FEATURES.filter((other) => !allowed.has(other)).map((other) => ({
              group: [`@/features/${other}`, `@/features/${other}/*`],
              message: `Importe de outra feature apenas pelo index.ts e só nas arestas permitidas (ADR-015). "${other}" não é permitida em "${feature}".`,
            })),
            CAPACITOR_IMPORT,
          ],
        },
      ],
    },
  }
})

export default defineConfig([
  // O projeto nativo do Capacitor (ADR-019) não é código do app: é gerado pelo
  // `npx cap sync` e compilado pelo Gradle/Xcode.
  globalIgnores(['dist', 'android', 'ios']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    files: ['src/shared/**/*.{ts,tsx}'],
    // Os únicos módulos que conhecem o Capacitor (ADR-019): detecção de
    // plataforma e adaptadores dos plugins nativos.
    ignores: ['src/shared/lib/platform.ts', 'src/shared/lib/native.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/app/*', '@/features/*'],
              message:
                'shared/ é reutilizável: não pode importar de app/ nem de features/.',
            },
            CAPACITOR_IMPORT,
          ],
        },
      ],
    },
  },
  {
    files: ['src/app/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [CAPACITOR_IMPORT] }],
    },
  },
  ...featureBoundaryConfigs,
])
