/**
 * Labels for the SALESMAN_LOCAL screens, English and Urdu side by side.
 * Both are always shown together (large English, Urdu beneath) rather than
 * behind a language switch — the users are not highly literate, so nothing
 * should need to be found in a settings screen first. Keep every
 * user-facing word for this role here so a wording fix is one edit.
 */
export const STRINGS = {
  welcome: { en: 'Welcome', ur: 'خوش آمدید' },
  menu: { en: 'Menu', ur: 'مینو' },
  enterSales: { en: 'Enter Sales', ur: 'فروخت درج کریں' },
  enterReturn: { en: 'Enter Return', ur: 'واپسی درج کریں' },
  logout: { en: 'Log Out', ur: 'لاگ آؤٹ' },
  close: { en: 'Close', ur: 'بند کریں' },
  nearbyShops: { en: 'Nearby Shops', ur: 'قریبی دکانیں' },
  pickShop: { en: 'Tap the shop you are at', ur: 'جس دکان پر ہیں اس پر ٹیپ کریں' },
  findingShops: { en: 'Finding nearby shops...', ur: 'قریبی دکانیں تلاش ہو رہی ہیں...' },
  noShops: { en: 'No shop nearby', ur: 'قریب کوئی دکان نہیں' },
  noShopsHelp: {
    en: 'Move closer to the shop and try again.',
    ur: 'دکان کے قریب جائیں اور دوبارہ کوشش کریں۔',
  },
  refresh: { en: 'Refresh', ur: 'دوبارہ تلاش کریں' },
  locationNeeded: {
    en: 'Please allow location so we can find the shop you are at.',
    ur: 'براہ کرم لوکیشن کی اجازت دیں تاکہ آپ کی دکان مل سکے۔',
  },
};
