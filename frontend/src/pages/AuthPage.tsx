import { useState, type FormEvent } from 'react';
import { authApi } from '../api/resources';
import { useAuth } from '../auth/AuthProvider';
import { Brand, Icon } from '../components/Icon';
import { FieldError, FormError } from '../components/Feedback';
import { useToast } from '../components/ToastProvider';
import { Link, navigate } from '../utils/router';
export function AuthPage({ register = false }: { register?: boolean }) {
  const { login } = useAuth();
  const notify = useToast();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    if (register && !name.trim()) { setError(new Error('Enter your name.')); return; }
    if (new TextEncoder().encode(password).length > 72) { setError(new Error('Password must not exceed 72 UTF-8 bytes.')); return; }
    setBusy(true); setError(null);
    try {
      if (register) { await authApi.register(name.trim(), email.trim(), password); setPassword(''); notify('Account created. Sign in to your workspace.'); navigate('/login', true); }
      else { await login(email.trim(), password); setPassword(''); }
    } catch (failure) { setError(failure); } finally { setBusy(false); }
  }
  return <main className="auth-page"><section className="auth-story"><Brand /><div className="story-copy"><span className="eyebrow">RELEASE ON YOUR TERMS</span><h1>Small changes.<br />Confident releases<span>.</span></h1><p>One workspace to control feature flags, roll out progressively, and keep every change in view.</p><div className="release-visual" aria-hidden="true"><div className="visual-header"><span className="status-dot" />new_checkout<span className="visual-on">ON</span></div><div className="visual-track"><span /></div><div className="visual-meta"><span>Production</span><strong>30% rollout</strong></div><div className="visual-step"><Icon name="shield" />Deterministic. Observable. Under your control.</div></div></div><p className="auth-footnote">Feature Management &amp; Progressive Delivery</p></section><section className="auth-form-section"><div className="auth-card"><span className="eyebrow">YOUR RELEASE WORKSPACE</span><h2>{register ? 'Create your account' : 'Welcome back'}</h2><p>{register ? 'Start shipping at your own pace.' : 'Sign in to control your next release.'}</p><form onSubmit={submit}><FormError error={error} /><fieldset disabled={busy}>
    {register && <><label htmlFor="auth-name">Name</label><input id="auth-name" name="name" value={name} onChange={event => setName(event.target.value)} autoComplete="name" maxLength={100} required placeholder="Your name" /><FieldError error={error} field="name" /></>}
    <label htmlFor="auth-email">Email address</label><input id="auth-email" name="email" type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} maxLength={254} required placeholder="you@company.com" /><FieldError error={error} field="email" />
    <label htmlFor="auth-password">Password</label><input id="auth-password" name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} required minLength={register ? 12 : 1} maxLength={72} placeholder={register ? 'At least 12 characters' : 'Enter your password'} /><FieldError error={error} field="password" />
    {register && <p className="field-hint">At least 12 characters, at most 72 UTF-8 bytes.</p>}
    <button className="button primary full-width" disabled={busy} type="submit">{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}{!busy && <Icon name="arrow" size={16} />}</button>
  </fieldset></form><p className="auth-switch">{register ? 'Already have an account?' : 'New to ReleasePilot?'} <Link href={register ? '/login' : '/register'}>{register ? 'Sign in' : 'Create an account'}</Link></p><span className="auth-security"><Icon name="shield" size={14} />Your workspace. Your release controls.</span></div></section></main>;
}
