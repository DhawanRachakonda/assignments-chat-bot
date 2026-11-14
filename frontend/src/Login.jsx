import { useState } from 'react';
import { login } from './auth.js';

export default function Login({ onSuccess }) {
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    if (!code || busy) return;
    setBusy(true);
    setError('');
    try {
      await login(code);
      setCode('');
      onSuccess();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <form className="login-card" onSubmit={submit}>
        <h1>Assignments Assistant</h1>
        <p>Enter the access code to continue.</p>
        <input
          type="password"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="Access code"
          autoComplete="current-password"
          autoFocus
        />
        {error && <div className="error" role="alert">{error}</div>}
        <button type="submit" disabled={!code || busy}>{busy ? 'Checking…' : 'Sign in'}</button>
      </form>
    </div>
  );
}
