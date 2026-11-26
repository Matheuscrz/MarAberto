import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  buildUnauthenticatedLoginTarget,
  isInternalPath,
  isPostLoginRedirectPath,
  resolvePostLoginTarget,
} from "./role-home.ts";

describe("role-home", () => {
  it("rejeita caminhos externos e malformados em isInternalPath", () => {
    assert.equal(isInternalPath("/admin"), true);
    assert.equal(isInternalPath("/operador/ocorrencias?from=2026-01-01"), true);
    assert.equal(isInternalPath(null), false);
    assert.equal(isInternalPath(""), false);
    assert.equal(isInternalPath("https://evil.example/admin"), false);
    assert.equal(isInternalPath("//evil.example/admin"), false);
    assert.equal(isInternalPath("/\\evil.example"), false);
  });

  it("exclui rotas transitórias de autenticação como destino pós-login", () => {
    assert.equal(isPostLoginRedirectPath("/trocar-senha"), false);
    assert.equal(isPostLoginRedirectPath("/trocar-senha/"), false);
    assert.equal(isPostLoginRedirectPath("/trocar-senha?foo=1"), false);
    assert.equal(isPostLoginRedirectPath("/login"), false);
    assert.equal(isPostLoginRedirectPath("/login?senha=alterada"), false);
    assert.equal(isPostLoginRedirectPath("/admin/usuarios"), true);
  });

  it("leva para /trocar-senha apenas quando mustChangePassword for verdadeiro", () => {
    assert.equal(
      resolvePostLoginTarget(
        { role: "ADMIN", mustChangePassword: true },
        "/admin/usuarios",
      ),
      "/trocar-senha",
    );

    assert.equal(
      resolvePostLoginTarget(
        { role: "ADMIN", mustChangePassword: false },
        "/trocar-senha",
      ),
      "/admin",
    );

    assert.equal(
      resolvePostLoginTarget(
        { role: "COORDINATOR", mustChangePassword: false },
        "/trocar-senha",
      ),
      "/gestao",
    );

    assert.equal(
      resolvePostLoginTarget(
        { role: "LIFEGUARD", mustChangePassword: false },
        "/login",
      ),
      "/operador",
    );
  });

  it("preserva destino interno legítimo quando mustChangePassword for falso", () => {
    assert.equal(
      resolvePostLoginTarget(
        { role: "ADMIN", mustChangePassword: false },
        "/admin/usuarios?ativos=true",
      ),
      "/admin/usuarios?ativos=true",
    );
  });

  it("monta destino de login sem anexar /trocar-senha em next e respeitando redirecionamento explícito", () => {
    assert.equal(
      buildUnauthenticatedLoginTarget("/trocar-senha", "/login?senha=alterada"),
      "/login?senha=alterada",
    );
    assert.equal(
      buildUnauthenticatedLoginTarget("/operador/ocorrencias", "/login"),
      "/login",
    );
    assert.equal(buildUnauthenticatedLoginTarget("/trocar-senha"), "/login");
    assert.equal(
      buildUnauthenticatedLoginTarget("/admin/usuarios"),
      "/login?next=%2Fadmin%2Fusuarios",
    );
  });
});
