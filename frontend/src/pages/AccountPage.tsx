import { useAuth } from '../auth/AuthProvider';
import { Icon } from '../components/Icon';
import { dateTime, initials } from '../utils/format';
export function AccountPage() {
  const { user, logout } = useAuth();
  if (!user) return null;
  return <><div className="page-heading"><div><span className="eyebrow">PERSONAL WORKSPACE</span><h1>Your account</h1><p>Your identity and session.</p></div></div><section className="account-card"><span className="avatar large">{initials(user.name)}</span><h2>{user.name}</h2><p>{user.email}</p><dl><div><dt>Role</dt><dd>{user.role}</dd></div><div><dt>Joined</dt><dd>{dateTime(user.createdAt)}</dd></div><div><dt>Session</dt><dd>This browser tab</dd></div></dl><button className="button secondary" onClick={logout}><Icon name="logout" />Logout</button></section></>;
}
