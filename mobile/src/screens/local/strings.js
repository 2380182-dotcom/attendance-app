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
  slowGps: {
    en: 'Still searching... check your GPS and internet connection.',
    ur: 'تلاش جاری ہے... اپنا GPS اور انٹرنیٹ کنکشن چیک کریں۔',
  },
  gpsTimeout: {
    en: 'Could not get your location in time. Move to an open area and try again.',
    ur: 'بروقت لوکیشن حاصل نہیں ہو سکی۔ کھلی جگہ پر جائیں اور دوبارہ کوشش کریں۔',
  },
  /** Shown mid-retry while apiService.lmt.getNearbyShops waits out a cold-starting backend. */
  serverWaking: (attempt, total) => ({
    en: `Server is waking up (try ${attempt} of ${total})... this can take up to a minute.`,
    ur: `سرور شروع ہو رہا ہے (کوشش ${attempt} از ${total})... اس میں ایک منٹ تک لگ سکتا ہے۔`,
  }),
  searchBread: { en: 'Search bread...', ur: 'روٹی تلاش کریں' },
  loadingProducts: { en: 'Loading breads...', ur: 'روٹیاں لوڈ ہو رہی ہیں...' },
  totalBreads: { en: 'Total breads', ur: 'کل روٹیاں' },
  totalRs: { en: 'Total Rs', ur: 'کل رقم' },
  returnValue: { en: 'Return value Rs', ur: 'واپسی کی مالیت روپے' },
  save: { en: 'Save', ur: 'محفوظ کریں' },
  nothingYet: { en: 'Tap + on a bread to begin', ur: 'شروع کرنے کے لیے روٹی پر + دبائیں' },
  checkThenSave: { en: 'Please check, then save', ur: 'براہ کرم دیکھ لیں، پھر محفوظ کریں' },
  sold: { en: 'Sold', ur: 'فروخت' },
  returned: { en: 'Return', ur: 'واپسی' },
  breads: { en: 'breads', ur: 'روٹیاں' },
  yesSave: { en: 'Yes, Save', ur: 'ہاں، محفوظ کریں' },
  goBack: { en: 'No, Go Back', ur: 'نہیں، واپس جائیں' },
  savedTitle: { en: 'Saved!', ur: 'محفوظ ہو گیا!' },
  savedBody: { en: 'Your entry has been saved.', ur: 'آپ کی انٹری محفوظ ہو گئی ہے۔' },
  saveFailed: { en: 'Could not save', ur: 'محفوظ نہیں ہو سکا' },
  saving: { en: 'Saving...', ur: 'محفوظ ہو رہا ہے...' },

  // QR shop-visit flow (Q3)
  scanQr: { en: 'Scan Shop QR', ur: 'دکان کا QR اسکین کریں' },
  pointCamera: { en: "Point the camera at the shop's QR code", ur: 'کیمرہ دکان کے QR کوڈ پر رکھیں' },
  verifyingShop: { en: 'Verifying shop...', ur: 'دکان کی تصدیق ہو رہی ہے...' },
  shopVerified: { en: 'Shop Verified', ur: 'دکان کی تصدیق ہو گئی' },
  couldNotVerifyShop: { en: 'Could Not Verify Shop', ur: 'دکان کی تصدیق نہیں ہو سکی' },
  distanceFromShop: { en: 'from shop', ur: 'دکان سے' },
  continueLabel: { en: 'Continue', ur: 'جاری رکھیں' },
  scanAgain: { en: 'Scan Again', ur: 'دوبارہ اسکین کریں' },
  cancel: { en: 'Cancel', ur: 'منسوخ کریں' },
  cameraPermissionNeeded: {
    en: "Camera access is needed to scan a shop's QR code.",
    ur: 'دکان کا QR کوڈ اسکین کرنے کے لیے کیمرہ کی اجازت درکار ہے۔',
  },
  allowCamera: { en: 'Allow Camera', ur: 'کیمرہ کی اجازت دیں' },

  // One-voucher-per-visit (Task 2): sale + optional return in one flow.
  recordVisit: { en: 'Record Shop Visit', ur: 'دکان کا وزٹ درج کریں' },
  askReturn: { en: 'Is there any return?', ur: 'کیا کوئی واپسی ہے؟' },
  yes: { en: 'Yes', ur: 'ہاں' },
  no: { en: 'No', ur: 'نہیں' },
  next: { en: 'Next', ur: 'اگلا' },
  saleTotal: { en: 'Sale Total Rs', ur: 'فروخت کی رقم' },
  returnTotal: { en: 'Return Total Rs', ur: 'واپسی کی رقم' },
  netTotal: { en: 'Net Total Rs', ur: 'خالص رقم' },
  netNegativeHint: {
    en: 'Returns are more than sales on this visit.',
    ur: 'اس وزٹ میں واپسی فروخت سے زیادہ ہے۔',
  },
  noSaleHint: {
    en: 'No sale? Tap Next — you can log a return-only visit.',
    ur: 'کوئی فروخت نہیں؟ اگلا دبائیں — آپ صرف واپسی درج کر سکتے ہیں۔',
  },
  nothingEnteredTitle: { en: 'Nothing entered', ur: 'کچھ درج نہیں ہوا' },
  nothingEnteredBody: {
    en: 'Add at least one sale or return item before saving.',
    ur: 'محفوظ کرنے سے پہلے کم از کم ایک فروخت یا واپسی درج کریں۔',
  },
  soldItems: { en: 'Sold Items', ur: 'فروخت شدہ اشیاء' },
  returnedItems: { en: 'Returned Items', ur: 'واپس شدہ اشیاء' },
};
