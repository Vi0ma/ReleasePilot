import { useEffect } from 'react';
import { AuthProvider, useAuth } from './auth/AuthProvider';
import { routeAccess } from './auth/session';
import { ToastProvider } from './components/ToastProvider';
import { Brand } from './components/Icon';
import { EmptyState, Loading } from './components/Feedback';
import { AppLayout } from './layouts/AppLayout';
import { AuthPage } from './pages/AuthPage';
import { AccountPage } from './pages/AccountPage';
import { DashboardPage } from './pages/DashboardPage';
import { ProjectsPage } from './pages/ProjectsPage';
import { ProjectPage, type ProjectSection } from './pages/ProjectPage';
import { Link, Redirect, usePath } from './utils/router';

function Routes() {
  const path = usePath();
  const auth = useAuth();
  const isPublic = path === '/login' || path === '/register';
  const access = routeAccess(auth.status, isPublic);
  useEffect(() => { document.title = `${isPublic ? path === '/register' ? 'Create account' : 'Sign in' : 'Workspace'} · ReleasePilot`; }, [path, isPublic]);
  if (access === 'loading') return <main className="session-loading"><Brand />{auth.error ? <div className="error-panel" role="alert"><strong>Could not restore your session</strong><p>{auth.error}</p><div className="button-group"><button className="button primary" onClick={auth.retry}>Try again</button><button className="button secondary" onClick={auth.logout}>Return to sign in</button></div></div> : <Loading label="Restoring your session…" />}</main>;
  if (access === 'login') return <Redirect to="/login" />;
  if (access === 'dashboard' || path === '/') return <Redirect to="/dashboard" />;
  if (isPublic) return <AuthPage key={path} register={path === '/register'} />;
  const match = path.match(/^\/projects\/([1-9]\d*)(?:\/(flags|api-keys|audit)(?:\/([1-9]\d*))?)?\/?$/);
  const projectId = match ? Number(match[1]) : 0;
  const flagId = match?.[3] ? Number(match[3]) : undefined;
  let page;
  if (path === '/dashboard') page = <DashboardPage />;
  else if (path === '/projects') page = <ProjectsPage />;
  else if (path === '/account') page = <AccountPage />;
  else if (match && Number.isSafeInteger(projectId) && (flagId === undefined || (match[2] === 'flags' && Number.isSafeInteger(flagId)))) page = <ProjectPage key={projectId} projectId={projectId} section={(match[2] ?? 'overview') as ProjectSection} flagId={flagId} />;
  else page = <EmptyState title="Page not found" description="This page does not exist in your workspace." action={<Link className="button primary" href="/dashboard">Back to dashboard</Link>} />;
  return <AppLayout>{page}</AppLayout>;
}
export default function App() { return <AuthProvider><ToastProvider><Routes /></ToastProvider></AuthProvider>; }
