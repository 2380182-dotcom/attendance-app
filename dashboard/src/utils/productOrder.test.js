import { describe, expect, it } from 'vitest';
import { compareProducts, withFamilyHeadings } from './productOrder';

const p = (id, name, sortOrder, familyId, onPriceList = true) => ({ id, name, sortOrder, familyId, onPriceList });

describe('official price list order', () => {
  it('sorts by SR No., unplaced products last by id, never by name', () => {
    const rows = [p(9, 'Aaa unplaced', null), p(4, 'Zeta', 3), p(2, 'Alpha', 52), p(1, 'Large Bread', 1), p(3, 'Bbb unplaced', null)];
    expect([...rows].sort(compareProducts).map((r) => r.id)).toEqual([1, 4, 2, 3, 9]);
  });

  it('puts family headings where the family changes and the unlisted products last', () => {
    const families = [{ id: 1, name: 'Bread' }, { id: 7, name: 'Rusk' }];
    const rows = withFamilyHeadings([p(60, 'S/W Bran', 60, 1, false), p(52, 'Crumbs', 52, 7), p(1, 'Large', 1, 1), p(3, 'Small', 3, 1)], families);
    expect(rows.map((r) => (r.type === 'heading' ? `#${r.name}` : r.item.name)))
      .toEqual(['#Bread', 'Large', 'Small', '#Rusk', 'Crumbs', '#Not on the price list', 'S/W Bran']);
  });

  it('keeps the order without headings when no families are loaded', () => {
    expect(withFamilyHeadings([p(2, 'B', 5, 1), p(1, 'A', 9, 1)], []).map((r) => r.item.id)).toEqual([2, 1]);
  });
});
