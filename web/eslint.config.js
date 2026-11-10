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
  // O operador registra ocorrências, cadastra o ponto em campo e escolhe a
  // praia/setor da ocorrência. A coordenação revisa ocorrências; a TI mantém
  // postos/bases.
  operator: ['occurrences', 'stations', 'beaches'],
  management: ['occurrences'],
  admin: ['stations'],
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
          ],
        },
      ],
    },
  }
})

export default defineConfig([
  globalIgnores(['dist']),
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
          ],
        },
      ],
    },
  },
  ...featureBoundaryConfigs,
])
