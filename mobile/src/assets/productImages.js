/**
 * Bundled product photos, keyed by PRODUCTION product id (products.id).
 *
 * Bundled rather than fetched: the catalog photos rarely change and must
 * show offline and instantly on the salesman screens. Keyed by id, not by
 * name, so an admin renaming a product doesn't make its photo vanish —
 * ids are stable in production (the only database this maps to; local dev
 * ids differ and will show the wrong/no photos there).
 *
 * Source photos: pics/products/products/, resized to 256×256 thumbnails in
 * mobile/assets/products/ (filename prefix = the photo's catalog number).
 * Adding a product: add its line here — some catalog photos (Paratha,
 * biscuits, 1x12 muffins, ...) are already bundled but unused because
 * production has no matching product yet.
 *
 * A product id missing from this map falls back to the placeholder icon in
 * ProductThumbnail.
 */
const PRODUCT_IMAGES = {
  1: require('../../assets/products/p01_large_bread.jpg'),
  2: require('../../assets/products/p03_small_bread.jpg'),
  3: require('../../assets/products/p04_milky_small.jpg'),
  4: require('../../assets/products/p06_bran_bread.jpg'),
  5: require('../../assets/products/p07_multi_grain_bread.jpg'),
  6: require('../../assets/products/p08_sandwich_bread.jpg'), // S/W Bread
  7: require('../../assets/products/p09_pitta_4_pec.jpg'),
  8: require('../../assets/products/p11_tortilla_1x8_10_inch.jpg'),
  9: require('../../assets/products/p12_tortilla_8_inch.jpg'),
  10: require('../../assets/products/p14_qp_bun_1x2.jpg'),
  11: require('../../assets/products/p15_qp_bun_1x4.jpg'),
  12: require('../../assets/products/p16_mac_royal_burger_bun_white_seeded.jpg'),
  13: require('../../assets/products/p18_hot_dog_burger_bun.jpg'),
  14: require('../../assets/products/p19_sheermall.jpg'),
  15: require('../../assets/products/p20_fruit_bun.jpg'), // not p21 "Fruity Bun"
  // One photo covers both sizes of each cake (catalog "L&S" shots).
  16: require('../../assets/products/p22_24_fruit_cake_l_and_s.jpg'),
  17: require('../../assets/products/p23_25_plain_cake_l_and_s.jpg'),
  18: require('../../assets/products/p22_24_fruit_cake_l_and_s.jpg'), // Mini Fruit Cake
  19: require('../../assets/products/p23_25_plain_cake_l_and_s.jpg'), // Mini Plain Cake
  20: require('../../assets/products/p44_cake_rusk.jpg'),
  21: require('../../assets/products/p45_mini_cake_rusk.jpg'),
  22: require('../../assets/products/p26_gol_cake_1x2.jpg'),
  23: require('../../assets/products/p27_lemon_cake.jpg'),
  24: require('../../assets/products/p28_marble_cake.jpg'),
  25: require('../../assets/products/p29_old_fashion_cake.jpg'),
  26: require('../../assets/products/p34_muffin_pine_apple_1x6.jpg'),
  27: require('../../assets/products/p35_muffin_strawbery_1x6.jpg'),
  28: require('../../assets/products/p36_muffin_mango_1x6.jpg'),
  29: require('../../assets/products/p37_muffin_choclate_1x6.jpg'),
  30: require('../../assets/products/p39_choclate_chip_muffin_1x4.jpg'),
  31: require('../../assets/products/p40_double_choclate_muffin_1x4.jpg'),
  32: require('../../assets/products/p46_dawn_rusk.jpg'),
  33: require('../../assets/products/p47_bran_rusk.jpg'),
  34: require('../../assets/products/p48_crispy_rusk.jpg'),
  35: require('../../assets/products/p49_otr_large.jpg'),
  36: require('../../assets/products/p50_otr_small.jpg'),
  37: require('../../assets/products/p52_crunbs.jpg'),
  38: require('../../assets/products/p13_bakar_khani_1x2.jpg'),
  39: require('../../assets/products/p51_round_rusk.jpg'),
  40: require('../../assets/products/p56_roll_patti_1x28.jpg'), // pack reads "Spring Roll Pastry, 24 sheets"
};

export function getProductImage(productId) {
  return productId != null ? PRODUCT_IMAGES[productId] ?? null : null;
}
