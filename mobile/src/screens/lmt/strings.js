/**
 * "Salesman updates" feature: LMT had no bilingual strings file before this
 * (every existing LMT screen is English-only). This file holds ONLY the
 * new English+Urdu labels introduced by this feature (Return Before
 * Expiry, Shop Feedback) — it is deliberately NOT a full port of every
 * pre-existing LMT label (that would be a much larger, separate retrofit).
 * Mirrors screens/local/strings.js's {en, ur} shape so the same display
 * patterns (and the writingDirection:'rtl' fix for mixed Urdu+Latin text)
 * apply here too.
 */
export const STRINGS = {
  // Return Before Expiry.
  expiryField: { en: 'Expiry', ur: 'میعاد' },
  askExpiryReturn: { en: 'Is there any return before expiry?', ur: 'کیا میعاد سے پہلے کوئی واپسی ہے؟' },
  returnBeforeExpiry: { en: 'Return Before Expiry', ur: 'میعاد ختم ہونے سے پہلے واپسی' },
  replacedHint: { en: 'Replaced — does not change the total', ur: 'تبدیل شدہ — کل رقم پر اثر نہیں' },

  // Shop Feedback.
  addFeedback: { en: 'Add Feedback', ur: 'رائے درج کریں' },
  feedbackReasonShopClosed: { en: 'Shop closed', ur: 'دکان بند ہے' },
  feedbackReasonStockFull: { en: 'Stock is full', ur: 'اسٹاک پورا ہے' },
  feedbackReasonOwnerNotAvailable: { en: 'Owner not available', ur: 'مالک موجود نہیں' },
  feedbackReasonOther: { en: 'Other', ur: 'دیگر' },
  writeYourFeedback: { en: 'Write your feedback', ur: 'اپنی رائے لکھیں' },
  saveFeedback: { en: 'Save Feedback', ur: 'رائے محفوظ کریں' },
  feedbackNoteRequired: { en: 'A note is required for "Other"', ur: '"دیگر" کے لیے نوٹ ضروری ہے' },
  feedbackSavedTitle: { en: 'Feedback Saved', ur: 'رائے محفوظ ہو گئی' },
};
