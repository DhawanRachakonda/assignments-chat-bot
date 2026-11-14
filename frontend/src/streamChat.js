/**
 * POSTs a question and consumes the Server-Sent Events stream from Spring Boot.
 * (EventSource only supports GET, so we parse the SSE frames from fetch ourselves.)
 * Calls onToken for each token, resolves when the "done" event arrives.
 */
export async function streamChat({ message, conversationId, onToken, signal }) {
  // Empty in dev (Vite proxies /api); set VITE_API_URL to the backend URL in production.
  const base = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');
  const res = await fetch(`${base}/api/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ message, conversationId }),
    signal,
  });
  if (!res.ok || !res.body) throw new Error(`Request failed (${res.status})`);

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  const handleFrame = (frame) => {
    let event = 'message';
    const data = [];
    for (const line of frame.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      // Spring writes "data:<token>" with no separator space, so a leading space IS token text
      // (e.g. " world"). Do not strip it, or words run together.
      else if (line.startsWith('data:')) data.push(line.slice(5));
    }
    const payload = data.join('\n');
    if (event === 'token') onToken(payload);
    else if (event === 'error') throw new Error(payload);
    return event === 'done';
  };

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split(/\r?\n\r?\n/);
    buffer = frames.pop();
    for (const f of frames) if (f.trim() && handleFrame(f)) return;
  }
  if (buffer.trim()) handleFrame(buffer);
}
