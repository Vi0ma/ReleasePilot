import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { products, money, type Cart } from './catalog';
import { getVisitor, newVisitor, browserStorage } from './visitor';
import { evaluateVisitor, checkoutVersion, type Decision } from './evaluation';
import './style.css';

import { ProductArt } from './ProductArt';
import { Summary, CheckoutV1, CheckoutV2 } from './Checkout';
function CheckoutPage({ cart }: { cart: Cart }) {
  const [visitor, setVisitor] = useState(() => getVisitor(browserStorage));
  const [decision, setDecision] = useState<Decision | null>(null);
  const [iteration, setIteration] = useState(0);
  const [loading, setLoading] = useState(true);
  const [complete, setComplete] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setDecision(null); setComplete(false);
    void evaluateVisitor(visitor, controller.signal).then(result => {
      if (!controller.signal.aborted) { setDecision(result); setLoading(false); }
    });
    return () => controller.abort();
  }, [visitor, iteration]);
  const fallback = decision?.reason === 'RELEASEPILOT_UNAVAILABLE';
  return <><a className="back" href="/cart">← Back to your bag</a><div className="demo-panel"><div><span className="eyebrow">RELEASEPILOT · DEMONSTRATION TOOL</span><dl><div><dt>Visitor</dt><dd>{visitor}</dd></div><div><dt>Flag</dt><dd>{decision?.flagKey || 'new_checkout'}</dd></div><div><dt>Decision</dt><dd>{loading ? 'EVALUATING…' : decision?.enabled ? 'ENABLED' : 'DISABLED'}</dd></div><div><dt>Reason</dt><dd>{loading ? '—' : decision?.reason}</dd></div></dl></div><div className="demo-actions"><button className="button secondary" disabled={loading} onClick={() => setVisitor(newVisitor(browserStorage))}>Generate another visitor</button><button className="button secondary" disabled={loading} onClick={() => setIteration(n => n + 1)}>Re-evaluate</button></div>{fallback && <p role="status" className="fallback">ReleasePilot unavailable → safe fallback → Checkout V1. You can still complete this demo.</p>}</div>{loading ? <div className="loading" role="status">Checking your checkout experience…</div> : complete ? <section className="thank-you" role="status"><span>✓</span><h1>That felt good.</h1><p>Your demo is complete. No payment was taken and no order was saved.</p><a className="button" href="/">Back to the store</a></section> : decision && (checkoutVersion(decision) === 'V2' ? <CheckoutV2 cart={cart} complete={() => setComplete(true)} /> : <CheckoutV1 cart={cart} complete={() => setComplete(true)} />)}</>;
}
function App() {
  const [cart, setCart] = useState<Cart>(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('morrow.cart') || '{}');
      return Object.fromEntries(products.map(p => [p.id, Number.isInteger(saved?.[p.id]) && saved[p.id] >= 0 && saved[p.id] <= 20 ? saved[p.id] : 0]));
    } catch { return {}; }
  });
  const [notice, setNotice] = useState('');
  useEffect(() => { try { localStorage.setItem('morrow.cart', JSON.stringify(cart)); } catch { /* Keep cart in memory. */ } }, [cart]);
  useEffect(() => { if (notice) { const timer = setTimeout(() => setNotice(''), 2500); return () => clearTimeout(timer); } }, [notice]);
  const count = Object.values(cart).reduce((a, b) => a + b, 0);
  const path = window.location.pathname;
  const update = (id: string, value: number) => setCart(current => ({ ...current, [id]: Number.isFinite(value) ? Math.max(0, Math.min(20, Math.floor(value))) : 0 }));
  return <><div className="announcement">Considered essentials. Everyday possibilities. <span>Complimentary shipping on every demo order.</span></div><header><a className="brand" href="/">morrow<span>®</span></a><nav aria-label="Main navigation"><a href="/" aria-current={path === '/' ? 'page' : undefined}>The collection</a><a href="/cart" aria-current={path === '/cart' ? 'page' : undefined}>Your bag <span className="count">{count}</span></a></nav><span className="header-note">LESS, BUT BETTER.</span></header><main>{path === '/' ? <><section className="hero"><div><span className="eyebrow">THE EVERYDAY, RECONSIDERED</span><h1>Small things.<br /><em>Better days.</em></h1><p>Useful objects, thoughtful details. A collection made for the moments in between.</p><a className="button" href="#collection">Find your everyday →</a><small>Independent Demo Store · powered by ReleasePilot</small></div><div className="hero-art"><span className="art-label">OBJECTS TO LIVE WITH</span><ProductArt shape="bag" color="#b9aa88" /><ProductArt shape="bottle" color="#829583" /><span className="art-caption">01 / The everyday collection</span></div></section><section id="collection"><div className="section-heading"><div><span className="eyebrow">A FEW GOOD THINGS</span><h2>Meet your new essentials.</h2></div><span>Designed for a slower kind of living.</span></div><div className="products">{products.map(p => <article className="product" key={p.id}><ProductArt shape={p.shape} color={p.color} /><small>{p.category}</small><div className="product-title"><h3>{p.name}</h3><span>{money(p.price)}</span></div><p>{p.description}</p><button className="button secondary wide" disabled={(cart[p.id] || 0) >= 20} onClick={() => { update(p.id, (cart[p.id] || 0) + 1); setNotice(`${p.name} added to your bag`); }}>Add to bag +</button></article>)}</div></section><div className="store-note"><strong>Made for the demo. Built like the real thing.</strong><p>Add a few favourites, then explore a checkout that changes with ReleasePilot.</p></div></> : path === '/cart' ? <><span className="eyebrow">YOUR EVERYDAY FINDS</span><h1>A bag of good things.</h1>{count ? <div className="cart-layout"><div>{products.filter(p => cart[p.id]).map(p => <article className="cart-item" key={p.id}><ProductArt shape={p.shape} color={p.color} /><div><small>{p.category}</small><h2>{p.name}</h2><label>Quantity<input type="number" min="0" max="20" value={cart[p.id]} onChange={e => update(p.id, Number(e.target.value) || 0)} /></label><button className="remove" onClick={() => update(p.id, 0)}>Remove</button></div><strong>{money(p.price * cart[p.id])}</strong></article>)}</div><div><Summary cart={cart} /><a href="/checkout" className="button wide">Continue to checkout →</a><small className="form-footnote">A demo experience. No real payment.</small></div></div> : <div className="empty"><h2>Your bag has room for something good.</h2><a className="button" href="/">Explore the collection →</a></div>}</> : path === '/checkout' ? count ? <CheckoutPage cart={cart} /> : <div className="empty"><h1>Your bag is empty.</h1><p>Add an item before trying the checkout.</p><a className="button" href="/">Explore the collection →</a></div> : <div className="empty"><h1>Page not found.</h1><a className="button" href="/">Back to the collection</a></div>}</main><footer><a href="/" className="brand">morrow<span>®</span></a><span>A little more intention. A little less ordinary.</span><small>ReleasePilot Demo Store · No real purchases</small></footer>{notice && <div role="status" className="toast">✓ {notice}</div>}</>;
}
createRoot(document.getElementById('root')!).render(<App />);
