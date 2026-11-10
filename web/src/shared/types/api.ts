/**
 * Contrato de erro RFC 9457 devolvido pela API (`application/problem+json`).
 * O `type` é um identificador estável do problema, não um redirecionamento.
 */
export interface FieldValidationError {
  readonly field: string;
  readonly message: string;
}

export interface ApiProblem {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail: string;
  readonly instance: string;
  readonly errors?: readonly FieldValidationError[];
}
