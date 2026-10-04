import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { Brand, Icon } from '../components/Icon';
import { Link, usePath } from '../utils/router';
import { initials } from '../utils/format';
export function AppLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const path = usePath();
  const context = path.startsWith('/projects/') ? 'Project workspace' : path === '/projects' ? 'Projects' : path === '/account' ? 'Your account' : 'Dashboard';
  if (!user) return null;
  return <div className="app-layout"><a className="skip-link" href="#content">Skip to content</a><aside className="sidebar">
    <Link href="/dashboard" className="brand-link" aria-label="ReleasePilot dashboard"><Brand /></Link>
    <div className="workspace-label"><span className="workspace-avatar">{initials(user.name)}</span><div><strong>Personal workspace</strong><span>Feature management</span></div></div>
    <span className="nav-label">WORKSPACE</span><nav aria-label="Main navigation">
      <Link href="/dashboard" className={`nav-link ${path === '/dashboard' ? 'active' : ''}`} aria-current={path === '/dashboard' ? 'page' : undefined}><Icon name="grid" />Dashboard</Link>
      <Link href="/projects" className={`nav-link ${path.startsWith('/projects') ? 'active' : ''}`} aria-current={path.startsWith('/projects') ? 'page' : undefined}><Icon name="folder" />Projects</Link>
    </nav>
    <div className="sidebar-note"><Icon name="bolt" /><p>Ship with confidence.<span>Control every release.</span></p></div>
    <div className="sidebar-account"><Link href="/account" className="account-link"><span className="avatar">{initials(user.name)}</span><span><strong>{user.name}</strong><small>{user.email}</small></span></Link><button className="icon-button" onClick={logout} aria-label="Logout" title="Logout"><Icon name="logout" /></button></div>
  </aside><div className="main-shell"><header className="topbar"><div className="breadcrumb"><Link href="/dashboard">Workspace</Link><Icon name="chevron" size={12} /><span>{context}</span></div><span className="workspace-tag"><span className="status-dot" />Personal workspace</span></header><main id="content" className="main-content" tabIndex={-1}>{children}</main><footer className="app-footer"><span>ReleasePilot</span><span>Feature Management &amp; Progressive Delivery</span></footer></div></div>;
}
