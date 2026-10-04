import type React from 'react';
export function ProductArt({ shape, color }: { shape: string; color: string }) {
  return <div className={`product-art ${shape}`} style={{ '--object-color': color } as React.CSSProperties} aria-hidden="true"><div className="object"><span /></div><div className="shadow" /></div>;
}
