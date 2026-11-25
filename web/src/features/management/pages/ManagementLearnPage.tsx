import { useCallback, useEffect, useState } from "react";
import {
  ArrowDown,
  ArrowUp,
  BookOpen,
  ExternalLink,
  Eye,
  EyeOff,
  Megaphone,
  Pencil,
  Plus,
  Trash2,
  Video,
  X,
} from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import {
  isValidYoutubeId,
  safeExternalUrl,
  sanitizeText,
} from "@/shared/lib/sanitize";
import type { ContentKind, EducationalContentItem } from "@/shared/types/content";
import {
  contentErrorMessage,
  createManagementContent,
  deleteManagementContent,
  fetchManagementContent,
  reorderManagementContent,
  setManagementContentPublished,
  updateManagementContent,
  type ContentUpsertInput,
} from "@/features/management/api/content";

interface FormState {
  kind: ContentKind;
  title: string;
  description: string;
  category: string;
  tagsText: string;
  youtubeId: string;
  duration: string;
  externalUrl: string;
  externalLinkLabel: string;
  published: boolean;
}

const EMPTY_FORM: FormState = {
  kind: "VIDEO",
  title: "",
  description: "",
  category: "Prevenção",
  tagsText: "",
  youtubeId: "",
  duration: "",
  externalUrl: "",
  externalLinkLabel: "",
  published: true,
};

const KIND_LABEL: Record<ContentKind, string> = {
  VIDEO: "Vídeo",
  GUIDE: "Guia",
  CAMPAIGN: "Divulgação",
};

const KIND_ICON: Record<ContentKind, typeof Video> = {
  VIDEO: Video,
  GUIDE: BookOpen,
  CAMPAIGN: Megaphone,
};

export function ManagementLearnPage() {
  const [items, setItems] = useState<readonly EducationalContentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const [kindFilter, setKindFilter] = useState<"ALL" | ContentKind>("ALL");
  const [publishFilter, setPublishFilter] = useState<
    "ALL" | "PUBLISHED" | "DRAFT"
  >("ALL");

  const [editorOpen, setEditorOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchManagementContent();
      setItems(data);
    } catch (err) {
      setError(contentErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    fetchManagementContent()
      .then((data) => {
        if (active) {
          setItems(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (active) {
          setError(contentErrorMessage(err));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const filteredItems = items.filter((item) => {
    if (kindFilter !== "ALL" && item.kind !== kindFilter) return false;
    if (publishFilter === "PUBLISHED" && !item.published) return false;
    if (publishFilter === "DRAFT" && item.published) return false;
    return true;
  });

  function openCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setError(null);
    setFeedback(null);
    setEditorOpen(true);
  }

  function openEdit(item: EducationalContentItem) {
    setEditingId(item.id);
    setForm({
      kind: item.kind,
      title: item.title,
      description: item.description,
      category: item.category,
      tagsText: item.tags.join(", "),
      youtubeId: item.youtubeId ?? "",
      duration: item.duration ?? "",
      externalUrl: item.externalUrl ?? "",
      externalLinkLabel: item.externalLinkLabel ?? "",
      published: item.published,
    });
    setError(null);
    setFeedback(null);
    setEditorOpen(true);
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setFeedback(null);

    const trimmedYoutube = form.youtubeId.trim();
    if (form.kind === "VIDEO" && !trimmedYoutube) {
      setError("Vídeos exigem o ID de 11 caracteres do YouTube.");
      return;
    }
    if (trimmedYoutube && !isValidYoutubeId(trimmedYoutube)) {
      setError(
        "ID do YouTube inválido: informe exatamente 11 caracteres (letras, números, _ ou -).",
      );
      return;
    }

    const trimmedUrl = form.externalUrl.trim();
    if (trimmedUrl && !safeExternalUrl(trimmedUrl)) {
      setError("URL externa inválida: informe um endereço http:// ou https://.");
      return;
    }

    const tags = form.tagsText
      .split(",")
      .map((t) => t.trim())
      .filter(Boolean);

    const payload: ContentUpsertInput = {
      kind: form.kind,
      title: form.title.trim(),
      description: form.description.trim(),
      category: form.category.trim(),
      tags,
      externalUrl: trimmedUrl || null,
      externalLinkLabel: form.externalLinkLabel.trim() || null,
      youtubeId: trimmedYoutube || null,
      duration: form.duration.trim() || null,
      published: form.published,
    };

    setSaving(true);
    try {
      if (editingId) {
        await updateManagementContent(editingId, payload);
        setFeedback("Conteúdo atualizado e publicado na Central Aprender.");
      } else {
        await createManagementContent(payload);
        setFeedback("Conteúdo criado com sucesso.");
      }
      setEditorOpen(false);
      setEditingId(null);
      await load();
    } catch (err) {
      setError(contentErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleTogglePublished(item: EducationalContentItem) {
    setBusyId(item.id);
    setError(null);
    setFeedback(null);
    try {
      await setManagementContentPublished(item.id, !item.published);
      setFeedback(
        item.published
          ? "Conteúdo despublicado (oculto da tela /aprender)."
          : "Conteúdo publicado na tela /aprender.",
      );
      await load();
    } catch (err) {
      setError(contentErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  async function handleMove(index: number, direction: -1 | 1) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= items.length) return;

    const reordered = [...items];
    const [moved] = reordered.splice(index, 1);
    reordered.splice(targetIndex, 0, moved);

    setBusyId(moved.id);
    setError(null);
    setFeedback(null);
    try {
      const updated = await reorderManagementContent(reordered.map((i) => i.id));
      setItems(updated);
      setFeedback("Ordem de exibição atualizada.");
    } catch (err) {
      setError(contentErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(id: string) {
    setBusyId(id);
    setError(null);
    setFeedback(null);
    try {
      await deleteManagementContent(id);
      setConfirmDeleteId(null);
      setFeedback("Conteúdo removido.");
      await load();
    } catch (err) {
      setError(contentErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  const canReorder = kindFilter === "ALL" && publishFilter === "ALL";

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <PageHeader
          title="Central Aprender (CMS)"
          subtitle="Cadastre, ordene e publique vídeos educativos (youtube-nocookie), guias de prevenção e materiais de divulgação para /aprender."
        />
        <button
          type="button"
          onClick={openCreate}
          className="inline-flex items-center gap-1.5 rounded-xl bg-ocean-950 px-3.5 py-2 text-xs font-bold text-white shadow-sm transition hover:bg-ocean-900"
        >
          <Plus aria-hidden="true" className="size-4" />
          Novo conteúdo
        </button>
      </div>

      {error ? (
        <Card className="border-red-200 bg-red-50 p-3 text-xs font-semibold text-red-900">
          {error}
        </Card>
      ) : null}

      {feedback ? (
        <Card className="border-emerald-200 bg-emerald-50 p-3 text-xs font-semibold text-emerald-900">
          {feedback}
        </Card>
      ) : null}

      {editorOpen ? (
        <Card className="p-4">
          <div className="mb-3 flex items-center justify-between gap-2">
            <h2 className="text-sm font-black text-ocean-950">
              {editingId ? "Editar conteúdo" : "Novo conteúdo"}
            </h2>
            <button
              type="button"
              onClick={() => setEditorOpen(false)}
              aria-label="Fechar editor"
              className="rounded-lg p-1 text-ocean-600 hover:bg-ocean-50"
            >
              <X aria-hidden="true" className="size-4" />
            </button>
          </div>

          <form onSubmit={(e) => void handleSubmit(e)} className="grid gap-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="grid gap-1 text-xs font-bold text-ocean-900">
                Tipo
                <select
                  value={form.kind}
                  onChange={(e) =>
                    setForm((curr) => ({
                      ...curr,
                      kind: e.target.value as ContentKind,
                    }))
                  }
                  className="rounded-lg border border-ocean-200 bg-white px-2.5 py-2 text-xs font-medium text-ocean-950"
                >
                  <option value="VIDEO">Vídeo educativo (YouTube)</option>
                  <option value="GUIDE">Guia de prevenção</option>
                  <option value="CAMPAIGN">Aviso / Divulgação</option>
                </select>
              </label>

              <label className="grid gap-1 text-xs font-bold text-ocean-900">
                Categoria
                <input
                  type="text"
                  required
                  maxLength={60}
                  value={form.category}
                  onChange={(e) =>
                    setForm((curr) => ({ ...curr, category: e.target.value }))
                  }
                  placeholder="Ex.: Corrente de Retorno, Campanha Verão"
                  className="rounded-lg border border-ocean-200 bg-white px-2.5 py-2 text-xs font-medium text-ocean-950"
                />
              </label>
            </div>

            <label className="grid gap-1 text-xs font-bold text-ocean-900">
              Título
              <input
                type="text"
                required
                maxLength={160}
                value={form.title}
                onChange={(e) =>
                  setForm((curr) => ({ ...curr, title: e.target.value }))
                }
                placeholder="Título exibido na Central Aprender"
                className="rounded-lg border border-ocean-200 bg-white px-2.5 py-2 text-xs font-medium text-ocean-950"
              />
            </label>

            <label className="grid gap-1 text-xs font-bold text-ocean-900">
              Descrição / Texto
              <textarea
                required
                rows={4}
                maxLength={2000}
                value={form.description}
                onChange={(e) =>
                  setForm((curr) => ({ ...curr, description: e.target.value }))
                }
                placeholder="Orientações, resumo do vídeo ou texto de divulgação"
                className="rounded-lg border border-ocean-200 bg-white px-2.5 py-2 text-xs font-medium text-ocean-950"
              />
            </label>

            <div className="grid gap-3 sm:grid-cols-2">
              <label className="grid gap-1 text-xs font-bold text-ocean-900">
                ID do vídeo no YouTube (11 caracteres)
                <input
                  type="text"
                  maxLength={11}
                  required={form.kind === "VIDEO"}
                  value={form.youtubeId}
                  onChange={(e) =>
                    setForm((curr) => ({ ...curr, youtubeId: e.target.value }))
                  }
                  placeholder="Ex.: dQw4w9WgXcQ"
                  className="rounded-lg border border-ocean-200 bg-white px-2.5 py-2 font-mono text-xs text-ocean-950"
                />
              </label>

              <label className="grid gap-1 text-xs font-bold text-ocean-900">
                Duração (opcional)
                <input
                  type="text"
                  maxLength={16}
                  value={form.duration}
                  onChange={(e) =>
                    setForm((curr) => ({ ...curr, duration: e.target.value }))
                  }
                  placeholder="Ex.: 03:15"
                  className="rounded-lg border border-ocean-200 bg-white px-2.5 py-2 text-xs font-medium text-ocean-950"
                />
              </label>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <label className="grid gap-1 text-xs font-bold text-ocean-900">
                Link externo (opcional, http/https)
                <input
                  type="url"
                  maxLength={500}
                  value={form.externalUrl}
                  onChange={(e) =>
                    setForm((curr) => ({
                      ...curr,
                      externalUrl: e.target.value,
                    }))
                  }
                  placeholder="https://..."
                  className="rounded-lg border border-ocean-200 bg-white px-2.5 py-2 text-xs font-medium text-ocean-950"
                />
              </label>

              <label className="grid gap-1 text-xs font-bold text-ocean-900">
                Rótulo do link externo (opcional)
                <input
                  type="text"
                  maxLength={80}
                  value={form.externalLinkLabel}
                  onChange={(e) =>
                    setForm((curr) => ({
                      ...curr,
                      externalLinkLabel: e.target.value,
                    }))
                  }
                  placeholder="Ex.: Baixar cartilha em PDF"
                  className="rounded-lg border border-ocean-200 bg-white px-2.5 py-2 text-xs font-medium text-ocean-950"
                />
              </label>
            </div>

            <label className="grid gap-1 text-xs font-bold text-ocean-900">
              Tags (separadas por vírgula)
              <input
                type="text"
                value={form.tagsText}
                onChange={(e) =>
                  setForm((curr) => ({ ...curr, tagsText: e.target.value }))
                }
                placeholder="Ex.: corrente, prevenção, crianças"
                className="rounded-lg border border-ocean-200 bg-white px-2.5 py-2 text-xs font-medium text-ocean-950"
              />
            </label>

            <label className="inline-flex items-center gap-2 text-xs font-bold text-ocean-900">
              <input
                type="checkbox"
                checked={form.published}
                onChange={(e) =>
                  setForm((curr) => ({ ...curr, published: e.target.checked }))
                }
                className="size-4 rounded border-ocean-300"
              />
              Publicado imediatamente em /aprender
            </label>

            <div className="flex flex-wrap justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => setEditorOpen(false)}
                className="rounded-lg border border-ocean-200 bg-white px-3 py-1.5 text-xs font-bold text-ocean-700 hover:bg-ocean-50"
              >
                Cancelar
              </button>
              <button
                type="submit"
                disabled={saving}
                className="rounded-lg bg-ocean-950 px-4 py-1.5 text-xs font-bold text-white hover:bg-ocean-900 disabled:opacity-50"
              >
                {saving
                  ? "Salvando..."
                  : editingId
                    ? "Salvar alterações"
                    : "Cadastrar conteúdo"}
              </button>
            </div>
          </form>
        </Card>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap gap-1.5">
          {(["ALL", "VIDEO", "GUIDE", "CAMPAIGN"] as const).map((kind) => (
            <button
              key={kind}
              type="button"
              onClick={() => setKindFilter(kind)}
              className={[
                "rounded-full px-3 py-1 text-xs font-bold transition",
                kindFilter === kind
                  ? "bg-ocean-950 text-white"
                  : "border border-ocean-200 bg-white text-ocean-700 hover:bg-ocean-50",
              ].join(" ")}
            >
              {kind === "ALL" ? "Todos os tipos" : KIND_LABEL[kind]}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap gap-1.5">
          {(
            [
              { value: "ALL", label: "Todos" },
              { value: "PUBLISHED", label: "Publicados" },
              { value: "DRAFT", label: "Despublicados" },
            ] as const
          ).map((tab) => (
            <button
              key={tab.value}
              type="button"
              onClick={() => setPublishFilter(tab.value)}
              className={[
                "rounded-lg px-2.5 py-1 text-xs font-bold transition",
                publishFilter === tab.value
                  ? "bg-ocean-800 text-white"
                  : "bg-ocean-50 text-ocean-700 hover:bg-ocean-100",
              ].join(" ")}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {!canReorder && items.length > 1 ? (
        <p className="text-[11px] text-ocean-600">
          Para reordenar a exibição, limpe os filtros para visualizar a lista
          completa.
        </p>
      ) : null}

      {loading && items.length === 0 ? (
        <Card className="p-6 text-center text-sm text-ocean-700">
          Carregando catálogo educativo...
        </Card>
      ) : null}

      {!loading && filteredItems.length === 0 ? (
        <Card className="p-6 text-center text-sm text-ocean-700">
          Nenhum conteúdo encontrado. Clique em “Novo conteúdo” para cadastrar.
        </Card>
      ) : null}

      <ul className="grid gap-2.5">
        {filteredItems.map((item, idx) => {
          const Icon = KIND_ICON[item.kind];
          const isBusy = busyId === item.id;
          const safeUrl = item.externalUrl
            ? safeExternalUrl(item.externalUrl)
            : null;

          return (
            <li key={item.id}>
              <Card className="flex flex-col gap-2.5 p-3.5 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0 flex-1 space-y-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="inline-flex items-center gap-1 rounded bg-ocean-100 px-2 py-0.5 text-[10px] font-extrabold uppercase text-ocean-800">
                      <Icon aria-hidden="true" className="size-3" />
                      {KIND_LABEL[item.kind]}
                    </span>
                    <span className="rounded bg-ocean-50 px-2 py-0.5 text-[10px] font-bold text-ocean-700">
                      {sanitizeText(item.category, 60)}
                    </span>
                    <span
                      className={
                        item.published
                          ? "rounded bg-emerald-100 px-2 py-0.5 text-[10px] font-extrabold uppercase text-emerald-800"
                          : "rounded bg-slate-200 px-2 py-0.5 text-[10px] font-extrabold uppercase text-slate-700"
                      }
                    >
                      {item.published ? "Publicado" : "Despublicado"}
                    </span>
                  </div>

                  <h3 className="text-sm font-black text-ocean-950">
                    {sanitizeText(item.title, 160)}
                  </h3>
                  <p className="line-clamp-2 text-xs text-ocean-700">
                    {sanitizeText(item.description, 300)}
                  </p>

                  <div className="flex flex-wrap items-center gap-3 text-[11px] text-ocean-600">
                    {item.youtubeId ? (
                      <span className="font-mono">
                        YouTube: {item.youtubeId}
                        {item.duration ? ` (${item.duration})` : ""}
                      </span>
                    ) : null}
                    {safeUrl ? (
                      <a
                        href={safeUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 font-semibold text-ocean-800 underline"
                      >
                        <ExternalLink aria-hidden="true" className="size-3" />
                        {item.externalLinkLabel ?? "Link externo"}
                      </a>
                    ) : null}
                  </div>
                </div>

                <div className="flex shrink-0 flex-wrap items-center gap-1.5">
                  {canReorder ? (
                    <>
                      <button
                        type="button"
                        disabled={isBusy || idx === 0}
                        onClick={() => void handleMove(idx, -1)}
                        aria-label={`Mover ${item.title} para cima`}
                        className="rounded-lg border border-ocean-200 bg-white p-1.5 text-ocean-700 hover:bg-ocean-50 disabled:opacity-40"
                      >
                        <ArrowUp aria-hidden="true" className="size-3.5" />
                      </button>
                      <button
                        type="button"
                        disabled={isBusy || idx === filteredItems.length - 1}
                        onClick={() => void handleMove(idx, 1)}
                        aria-label={`Mover ${item.title} para baixo`}
                        className="rounded-lg border border-ocean-200 bg-white p-1.5 text-ocean-700 hover:bg-ocean-50 disabled:opacity-40"
                      >
                        <ArrowDown aria-hidden="true" className="size-3.5" />
                      </button>
                    </>
                  ) : null}

                  <button
                    type="button"
                    disabled={isBusy}
                    onClick={() => void handleTogglePublished(item)}
                    className="inline-flex items-center gap-1 rounded-lg border border-ocean-200 bg-white px-2.5 py-1.5 text-xs font-bold text-ocean-800 hover:bg-ocean-50 disabled:opacity-50"
                  >
                    {item.published ? (
                      <>
                        <EyeOff aria-hidden="true" className="size-3.5" />
                        Despublicar
                      </>
                    ) : (
                      <>
                        <Eye aria-hidden="true" className="size-3.5" />
                        Publicar
                      </>
                    )}
                  </button>

                  <button
                    type="button"
                    disabled={isBusy}
                    onClick={() => openEdit(item)}
                    aria-label={`Editar ${item.title}`}
                    className="rounded-lg border border-ocean-200 bg-white p-1.5 text-ocean-800 hover:bg-ocean-50 disabled:opacity-50"
                  >
                    <Pencil aria-hidden="true" className="size-3.5" />
                  </button>

                  {confirmDeleteId === item.id ? (
                    <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => void handleDelete(item.id)}
                      className="rounded-lg bg-red-600 px-2.5 py-1.5 text-xs font-bold text-white hover:bg-red-700 disabled:opacity-50"
                    >
                      Confirmar
                    </button>
                  ) : (
                    <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => setConfirmDeleteId(item.id)}
                      aria-label={`Excluir ${item.title}`}
                      className="rounded-lg border border-red-200 bg-red-50 p-1.5 text-red-700 hover:bg-red-100 disabled:opacity-50"
                    >
                      <Trash2 aria-hidden="true" className="size-3.5" />
                    </button>
                  )}
                </div>
              </Card>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
