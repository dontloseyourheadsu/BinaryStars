import type { RefObject } from "react";
import ReactMarkdown from "react-markdown";
import rehypeRaw from "rehype-raw";
import remarkBreaks from "remark-breaks";
import remarkGfm from "remark-gfm";
import type { Note } from "../../types";

type Props = {
  notes: Note[];
  editingNoteId: string | null;
  noteName: string;
  noteContent: string;
  noteType: "Plaintext" | "Markdown";
  noteContentRef: RefObject<HTMLTextAreaElement | null>;
  setNoteName: (value: string) => void;
  setNoteContent: (value: string) => void;
  setNoteType: (value: "Plaintext" | "Markdown") => void;
  onOpenNote: (note: Note) => void;
  onDeleteNote: (noteId: string) => void;
  onSaveNote: () => void;
  onResetEditor: () => void;
  onWrapSelection: (before: string, after?: string) => void;
  onInsertAtSelection: (text: string) => void;
};

export default function NotesTab({
  notes,
  editingNoteId,
  noteName,
  noteContent,
  noteType,
  noteContentRef,
  setNoteName,
  setNoteContent,
  setNoteType,
  onOpenNote,
  onDeleteNote,
  onSaveNote,
  onResetEditor,
  onWrapSelection,
  onInsertAtSelection,
}: Props) {
  const isMarkdown = (value: unknown): boolean => {
    if (typeof value === "string") {
      const normalized = value.trim().toLowerCase();
      return normalized === "markdown";
    }

    if (typeof value === "number") {
      return value === 1;
    }

    return false;
  };

  return (
    <section className="panel-grid">
      <div className="panel">
        <div className="split-row">
          <h3>Notes</h3>
          <button onClick={onResetEditor} type="button">+ New</button>
        </div>
        <div className="list">
          {notes.length === 0 && <p className="empty-state">No notes yet. Create one to get started!</p>}
          {notes.map((note) => (
            <article className="row-card static" key={note.id}>
              <button className="link-row" onClick={() => onOpenNote(note)} type="button">
                <strong>{note.name}</strong>
                <span className="muted">{note.contentType} · {new Date(note.updatedAt).toLocaleString()}</span>
              </button>
              {isMarkdown(note.contentType) && (
                <div className="markdown-preview">
                  <ReactMarkdown
                    remarkPlugins={[remarkGfm, remarkBreaks]}
                    rehypePlugins={[rehypeRaw]}
                  >
                    {note.content || "_Preview_"}
                  </ReactMarkdown>
                </div>
              )}
              <button className="ghost" onClick={() => onDeleteNote(note.id)} type="button">
                Delete
              </button>
            </article>
          ))}
        </div>
      </div>
      <div className="panel">
        <h3>{editingNoteId ? "Edit Note" : "Create Note"}</h3>
        <label>
          Name
          <input value={noteName} onChange={(event) => setNoteName(event.target.value)} />
        </label>
        <label>
          Type
          <select
            onChange={(event) => setNoteType(event.target.value as "Plaintext" | "Markdown")}
            value={noteType}
          >
            <option value="Plaintext">Plaintext</option>
            <option value="Markdown">Markdown</option>
          </select>
        </label>
        <label>
          Content
          {isMarkdown(noteType) && (
            <div className="md-toolbar">
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("**")}><b>B</b></button>
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("*")}><i>I</i></button>
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("<u>", "</u>")}><u>U</u></button>
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("~~")}>S</button>
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("# ", "")}>H1</button>
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("## ", "")}>H2</button>
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("### ", "")}>H3</button>
              <button className="editor-icon" type="button" onClick={() => onInsertAtSelection("- item\n")}>•</button>
              <button className="editor-icon" type="button" onClick={() => onInsertAtSelection("1. item\n")}>1.</button>
              <button className="editor-icon" type="button" onClick={() => onInsertAtSelection("- [ ] task\n")}>☑</button>
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("```\n", "\n```")}>{"{ }"}</button>
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("`")}>`</button>
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("> ", "")}>❝</button>
              <button className="editor-icon" type="button" onClick={() => onWrapSelection("[", "](url)")}>🔗</button>
              <button className="editor-icon" type="button" onClick={() => onInsertAtSelection("\n---\n")}>—</button>
            </div>
          )}
          <textarea ref={noteContentRef} onChange={(event) => setNoteContent(event.target.value)} rows={10} value={noteContent} />
        </label>
        <div className="split-row">
          <button className="ghost" onClick={onResetEditor} type="button">Cancel</button>
          <button onClick={onSaveNote} type="button">Save</button>
        </div>
        {isMarkdown(noteType) && (
          <div className="markdown-preview">
            <ReactMarkdown
              remarkPlugins={[remarkGfm, remarkBreaks]}
              rehypePlugins={[rehypeRaw]}
            >
              {noteContent || "_Preview_"}
            </ReactMarkdown>
          </div>
        )}
      </div>
    </section>
  );
}
