export const products = [
  { id: 'bag', name: 'Everyday carry', category: 'The canvas collection', price: 6800, shape: 'bag', color: '#b9aa88', description: 'A little room for everything. Heavyweight cotton canvas, made for the everyday.' },
  { id: 'lamp', name: 'Soft light', category: 'Objects for home', price: 9200, shape: 'lamp', color: '#cc7756', description: 'A warm corner, wherever you are. A sculptural lamp with a gentle glow.' },
  { id: 'bottle', name: 'Go slow bottle', category: 'Daily essentials', price: 3400, shape: 'bottle', color: '#829583', description: 'Keep your favourite drink close. Double-wall steel in a quiet sage finish.' },
  { id: 'cup', name: 'Morning ritual', category: 'Objects for home', price: 2800, shape: 'cup', color: '#dfb986', description: 'An unhurried start. A generously sized stoneware mug with a soft matte glaze.' },
] as const;
export type Cart = Record<string, number>;
export const money = (cents: number) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(cents / 100);
export function cartTotal(cart: Cart): number { return products.reduce((sum, product) => sum + product.price * (cart[product.id] || 0), 0); }
