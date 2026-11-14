import { useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { streamChat } from './streamChat.js';

const SUGGESTIONS = [
  'How many learners have submitted assignments?',
  'Who has submitted the maximum number of assignments?',
  'Which assignments have been submitted, and by how many learners each?',
  'What was submitted most recently?',
];

const newId = () => crypto.randomUUID();

// Tables scroll horizontally on small screens; links open in a new tab.
const MD_COMPONENTS = {
  table: ({ node, ...props }) => (
    <div className="table-wrap">
      <table {...props} />
    </div>
  ),
  a: ({ node, ...props }) => <a {...props} target="_blank" rel="noopener noreferrer" />,
};

export default function App() {
  const [conversationId, setConversationId] = useState(newId);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const abortRef = useRef(null);
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages]);

  const patchLast = (fn) =>
    setMessages((prev) => prev.map((m, i) => (i === prev.length - 1 ? fn(m) : m)));

  async function send(text) {
    const message = text.trim();
    if (!message || busy) return;
    setInput('');
    setBusy(true);
    setMessages((prev) => [
      ...prev,
      { role: 'user', content: message },
      { role: 'assistant', content: '', pending: true },
    ]);
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    try {
      await streamChat({
        message,
        conversationId,
        signal: ctrl.signal,
        onToken: (t) => patchLast((m) => ({ ...m, content: m.content + t })),
      });
    } catch (e) {
      if (e.name !== 'AbortError') {
        patchLast((m) => ({ ...m, error: e.message || 'Something went wrong.' }));
      }
    } finally {
      patchLast((m) => ({ ...m, pending: false }));
      setBusy(false);
      abortRef.current = null;
    }
  }

  function reset() {
    abortRef.current?.abort();
    setMessages([]);
    setConversationId(newId());
    setBusy(false);
  }

  return (
    <div className="app">
      <header>
        <div>
          <h1>Assignments Assistant</h1>
          <p>Ask about learner assignment submissions</p>
        </div>
        <button className="ghost" onClick={reset}>New chat</button>
      </header>

      <main>
        {messages.length === 0 && (
          <div className="empty">
            <p>Try one of these:</p>
            <div className="chips">
              {SUGGESTIONS.map((s) => (
                <button key={s} className="chip" onClick={() => send(s)}>{s}</button>
              ))}
            </div>
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`msg ${m.role}`}>
            <div className="bubble">
              {m.role === 'assistant' ? (
                <>
                  {m.content ? (
                    <div className="md">
                      <ReactMarkdown remarkPlugins={[remarkGfm]} components={MD_COMPONENTS}>
                        {m.content}
                      </ReactMarkdown>
                    </div>
                  ) : (
                    m.pending && !m.error && <span className="thinking">Looking at the data…</span>
                  )}
                  {m.pending && m.content && <span className="cursor" />}
                  {m.error && <div className="error">{m.error}</div>}
                </>
              ) : (
                m.content
              )}
            </div>
          </div>
        ))}
        <div ref={bottomRef} />
      </main>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          send(input);
        }}
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask a question…"
          autoFocus
        />
        {busy ? (
          <button type="button" onClick={() => abortRef.current?.abort()}>Stop</button>
        ) : (
          <button type="submit" disabled={!input.trim()}>Send</button>
        )}
      </form>
    </div>
  );
}
