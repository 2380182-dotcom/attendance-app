package com.dawnbread.attendance.util;

import com.dawnbread.attendance.entity.Product;

import java.util.Comparator;
import java.util.function.Function;

/**
 * The ONE product order used by every product list (apps, dashboard,
 * reports): the official price list's SR No. (Product.sortOrder, V39).
 * Products not placed yet (sortOrder null) come after every placed one.
 * Ties and unplaced products go by id, i.e. the order they were added;
 * never by name, category or price (Dawn Bread's rule).
 *
 * Rankings (top products, return %, slow products) are NOT product lists
 * and keep their own value order.
 */
public final class ProductOrder {

    private ProductOrder() {
    }

    public static final Comparator<Product> PRODUCTS = Comparator
            .comparing(Product::getSortOrder, Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
            .thenComparing(Product::getId, Comparator.nullsLast(Comparator.<Long>naturalOrder()));

    /** Orders anything that carries a product (sale lines, stock lines ...) by that product. Null products last. */
    public static <T> Comparator<T> by(Function<T, Product> product) {
        return Comparator.comparing(product, Comparator.nullsLast(PRODUCTS));
    }
}
