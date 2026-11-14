import { useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { streamChat } from './streamChat.js';
import Login from './Login.jsx';
import { checkSession, clearToken } from './auth.js';

const SUGGESTIONS = [
  'How many learners have submitted assignments?',
  'Who has submitted the maximum number of assignments?',
  'Which assignments have been submitted, and by how many learners each?',
  'What was submitted most recently?',
];

const MAX_MESSAGE_LENGTH = 500; // keep in sync with app.limits.message-max-length on the API

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

/** Gate: the chat is only rendered once the backend has confirmed a valid session. */
export default function App() {
  const [authed, setAuthed] = useState(null); // null = checking the stored token

  useEffect(() => {
    checkSession().then(setAuthed);
  }, []);

  function signOut() {
    clearToken();
    setAuthed(false);
  }

  if (authed === null) return <div className="login"><p className="thinking">Loading…</p></div>;
  if (!authed) return <Login onSuccess={() => setAuthed(true)} />;
  return <Chat onSignOut={signOut} />;
}

function Chat({ onSignOut }) {
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
      if (e.status === 401) {
        onSignOut(); // session expired or the access code changed
      } else if (e.name !== 'AbortError') {
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
        <div className="header-actions">
          <button className="ghost" onClick={reset}>New chat</button>
          <button className="ghost" onClick={onSignOut}>Sign out</button>
        </div>
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
          maxLength={MAX_MESSAGE_LENGTH}
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
