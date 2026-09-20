package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.CustomerShopCreateDTO;
import com.dawnbread.attendance.dto.SaleItemRequest;
import com.dawnbread.attendance.dto.SalesRequest;
import com.dawnbread.attendance.dto.ShopProductPricesUpdateDTO;
import com.dawnbread.attendance.dto.ShopVisitItemRequest;
import com.dawnbread.attendance.dto.ShopVisitRequest;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.entity.MartType;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.SaleItem;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.ShopProductDiscount;
import com.dawnbread.attendance.entity.ShopProductPrice;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.ShopProductDiscountRepository;
import com.dawnbread.attendance.repository.ShopProductPriceRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import com.dawnbread.attendance.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Per-shop explicit prices (V24): base price = shop price if set, else the
 * global salesmanPrice; the shop discount is applied on top. Salesman
 * shop-visit flow only — the last test proves the Agent flow ignores shop
 * prices entirely and still computes from agentPrice.
 */
@SpringBootTest
class ShopPricingTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private ShopProductDiscountRepository shopProductDiscountRepository;

    @Autowired
    private MartRepository martRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private ShopProductPriceRepository shopProductPriceRepository;

    @Autowired
    private CustomerShopService customerShopService;

    @Autowired
    private SalesService salesService;

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(tenantId());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private Long tenantId() {
        return tenantRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setCompanyCode("DAWNBREAD");
                    t.setName("Dawn Bread");
                    t.setIsActive(true);
                    t.setCreatedAt(LocalDateTime.now());
                    t.setCreatedBy("TEST");
                    return tenantRepository.save(t);
                })
                .getId();
    }

    private Agent seedLmt(String agentId) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole("SALESMAN_LMT");
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    private Product seedProduct(double salesmanPrice) {
        Product product = new Product();
        product.setTenantId(tenantId());
        product.setName("Shop Pricing Test Bread " + System.nanoTime());
        product.setAgentPrice(55.0);
        product.setSalesmanPrice(salesmanPrice);
        product.setIsActive(true);
        product.setCreatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private CustomerShop seedShop(Double shopDiscountPercent) {
        Area area = new Area();
        area.setTenantId(tenantId());
        area.setName("Shop Pricing Test Area " + System.nanoTime());
        area.setCreatedAt(LocalDateTime.now());
        area = areaRepository.save(area);

        CustomerShop shop = new CustomerShop();
        shop.setTenantId(tenantId());
        shop.setShopCode("PRC-" + System.nanoTime());
        shop.setShopName("Shop Pricing Test Shop");
        shop.setArea(area);
        shop.setGeoFencingEnabled(false); // no geofence — not the concern of this test
        shop.setIsActive(true);
        shop.setDiscountPercent(shopDiscountPercent);
        shop.setCreatedAt(LocalDateTime.now());
        return customerShopRepository.save(shop);
    }

    private void seedSkuOverride(CustomerShop shop, Product product, double discountPercent) {
        ShopProductDiscount override = new ShopProductDiscount();
        override.setTenantId(tenantId());
        override.setCustomerShop(shop);
        override.setProduct(product);
        override.setDiscountPercent(discountPercent);
        shopProductDiscountRepository.save(override);
    }

    private void seedShopPrice(CustomerShop shop, Product product, double price) {
        ShopProductPrice row = new ShopProductPrice();
        row.setTenantId(tenantId());
        row.setCustomerShop(shop);
        row.setProduct(product);
        row.setPrice(price);
        shopProductPriceRepository.save(row);
    }

    private void seedCompanyCheckIn(Agent agent) {
        Mart companyMart = new Mart();
        companyMart.setTenantId(tenantId());
        companyMart.setName("Pricing Test Depot " + System.nanoTime());
        companyMart.setLatitude(0.0);
        companyMart.setLongitude(0.0);
        companyMart.setRadius(100.0);
        companyMart.setGeoFencingEnabled(true);
        companyMart.setIsActive(true);
        companyMart.setMartType(MartType.COMPANY);
        companyMart.setCreatedAt(LocalDateTime.now());
        companyMart = martRepository.save(companyMart);

        Attendance attendance = new Attendance();
        attendance.setTenantId(tenantId());
        attendance.setAgent(agent);
        attendance.setMart(companyMart);
        attendance.setCheckInTime(LocalDateTime.now());
        attendance.setStatus("IN");
        attendanceRepository.save(attendance);
    }

    private SalesRecord submitSale(Agent lmt, CustomerShop shop, Product product, int quantity, String transactionType) {
        ShopVisitRequest request = new ShopVisitRequest();
        request.setAgentId(lmt.getId());
        request.setShopCode(shop.getShopCode());
        request.setLatitude(0.0);
        request.setLongitude(0.0);
        ShopVisitItemRequest item = new ShopVisitItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(quantity);
        item.setTransactionType(transactionType);
        request.setItems(List.of(item));
        return salesService.submitShopVisit(request);
    }

    @Test
    void shopPriceOverridesGlobalSalesmanPrice() {
        Product product = seedProduct(100.0);
        CustomerShop shop = seedShop(null);
        seedShopPrice(shop, product, 120.0);
        Agent lmt = seedLmt("PRICE_SHOP_WINS");
        seedCompanyCheckIn(lmt);

        SalesRecord saved = submitSale(lmt, shop, product, 3, "SALE");

        assertEquals(360.0, saved.getTotalAmount(), 0.001, "3 x shop price 120 = 360, not 3 x salesmanPrice 100");
        assertEquals(120.0, saved.getItems().get(0).getUnitPrice(), 0.001, "unitPrice must record the price actually used");
    }

    @Test
    void fallsBackToSalesmanPriceWhenShopHasNoPriceForProduct() {
        Product product = seedProduct(100.0);
        Product otherProduct = seedProduct(90.0);
        CustomerShop shop = seedShop(null);
        seedShopPrice(shop, otherProduct, 500.0); // a price for a DIFFERENT product must not leak
        Agent lmt = seedLmt("PRICE_FALLBACK");
        seedCompanyCheckIn(lmt);

        SalesRecord saved = submitSale(lmt, shop, product, 2, "SALE");

        assertEquals(200.0, saved.getTotalAmount(), 0.001);
        assertEquals(100.0, saved.getItems().get(0).getUnitPrice(), 0.001);
    }

    @Test
    void shopDiscountIsAppliedOnTopOfTheShopPrice() {
        // The shop price can be HIGHER than the global one — discounts alone
        // could never express that. 200 x 2 x (1 - 0.10) = 360.
        Product product = seedProduct(100.0);
        CustomerShop shop = seedShop(10.0);
        seedShopPrice(shop, product, 200.0);
        Agent lmt = seedLmt("PRICE_THEN_DISCOUNT");
        seedCompanyCheckIn(lmt);

        SalesRecord saved = submitSale(lmt, shop, product, 2, "SALE");

        assertEquals(360.0, saved.getTotalAmount(), 0.001);
        assertEquals(200.0, saved.getItems().get(0).getUnitPrice(), 0.001);
        assertEquals(10.0, saved.getItems().get(0).getDiscountPercent(), 0.001);
    }

    @Test
    void skuDiscountOverrideStillWinsAndAppliesOnTopOfShopPrice() {
        Product product = seedProduct(100.0);
        CustomerShop shop = seedShop(10.0);
        seedShopPrice(shop, product, 80.0);
        seedSkuOverride(shop, product, 25.0);
        Agent lmt = seedLmt("PRICE_SKU_DISCOUNT");
        seedCompanyCheckIn(lmt);

        SalesRecord saved = submitSale(lmt, shop, product, 2, "SALE");

        // 80 x 2 x (1 - 0.25) = 120 — the existing SKU-override-wins rule is unchanged.
        assertEquals(120.0, saved.getTotalAmount(), 0.001);
    }

    @Test
    void returnUsesShopPriceButIsNeverDiscounted() {
        Product product = seedProduct(100.0);
        CustomerShop shop = seedShop(50.0);
        seedShopPrice(shop, product, 150.0);
        Agent lmt = seedLmt("PRICE_RETURN");
        seedCompanyCheckIn(lmt);

        SalesRecord saved = submitSale(lmt, shop, product, 4, "RETURN");

        SaleItem item = saved.getItems().get(0);
        assertEquals(150.0, item.getUnitPrice(), 0.001);
        assertEquals(600.0, item.getTotalPrice(), 0.001, "RETURN = shop price x qty, no discount");
        assertNull(item.getDiscountPercent());
        assertEquals(0.0, saved.getTotalAmount(), 0.001, "RETURN still never contributes to revenue");
    }

    @Test
    void agentSaleIgnoresShopPricesAndStillUsesAgentPrice() {
        // The safety gate for the live agents: a shop price row exists for
        // this product, and the Agent flow must not see it at all.
        Product product = seedProduct(100.0); // agentPrice is 55.0 in this test's seed
        CustomerShop shop = seedShop(20.0);
        seedShopPrice(shop, product, 999.0);
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId("PRICE_AGENT_UNCHANGED");
        agent.setName("Seed PRICE_AGENT_UNCHANGED");
        agent.setEmail("price_agent_unchanged@example.com");
        agent.setRole("AGENT");
        agent.setCreatedAt(LocalDateTime.now());
        agent = agentRepository.save(agent);

        SalesRecord saved = salesService.addSalesWithImages(new SalesRequest(
                agent.getId(), "Pricing Test Store", List.of(new SaleItemRequest(product.getId(), 4))));

        assertEquals(220.0, saved.getTotalAmount(), 0.001, "4 x agentPrice 55 = 220 — shop price 999 and discount 20% are invisible to agents");
        assertEquals(55.0, saved.getItems().get(0).getUnitPrice(), 0.001);
    }

    @Test
    void batchApplySetsUpdatesAndRemovesShopPrices() {
        Product a = seedProduct(100.0);
        Product b = seedProduct(100.0);
        CustomerShop shop = seedShop(null);

        customerShopService.applyProductPrices(shop.getId(), List.of(entry(a, 110.0), entry(b, 90.0)));
        assertEquals(2, customerShopService.getProductPrices(shop.getId()).size());

        // update a, remove b (null), an unlisted product stays untouched
        customerShopService.applyProductPrices(shop.getId(), List.of(entry(a, 130.0), entry(b, null)));
        var prices = customerShopService.getProductPrices(shop.getId());
        assertEquals(1, prices.size());
        assertEquals(130.0, prices.get(0).getPrice(), 0.001);
    }

    @Test
    void explicitZeroShopPriceMeansFreeWhileNullMeansFallback() {
        Product free = seedProduct(100.0);
        Product fallback = seedProduct(100.0);
        CustomerShop shop = seedShop(null);
        customerShopService.applyProductPrices(shop.getId(), List.of(entry(free, 0.0), entry(fallback, null)));
        Agent lmt = seedLmt("PRICE_ZERO_FREE");
        seedCompanyCheckIn(lmt);

        SalesRecord freeSale = submitSale(lmt, shop, free, 5, "SALE");
        SalesRecord fallbackSale = submitSale(lmt, shop, fallback, 5, "SALE");

        assertEquals(0.0, freeSale.getTotalAmount(), 0.001, "a typed 0 price is a real price: free at this shop");
        assertEquals(500.0, fallbackSale.getTotalAmount(), 0.001, "a blank (null) price falls back to salesmanPrice");
    }

    @Test
    void invalidPricesAndDiscountsAreRejectedBeforeAnythingIsSaved() {
        Product a = seedProduct(100.0);
        Product b = seedProduct(100.0);
        CustomerShop shop = seedShop(null);

        // a negative price anywhere in the batch rejects the WHOLE batch — no half-applied save
        assertThrows(RuntimeException.class,
                () -> customerShopService.applyProductPrices(shop.getId(), List.of(entry(a, 50.0), entry(b, -1.0))));
        assertEquals(0, customerShopService.getProductPrices(shop.getId()).size());

        assertThrows(RuntimeException.class, () -> customerShopService.upsertProductDiscount(shop.getId(), a.getId(), 101.0));
        assertThrows(RuntimeException.class, () -> customerShopService.upsertProductDiscount(shop.getId(), a.getId(), -5.0));

        CustomerShopCreateDTO overDiscount = new CustomerShopCreateDTO();
        overDiscount.setDiscountPercent(150.0);
        assertThrows(RuntimeException.class, () -> customerShopService.update(shop.getId(), overDiscount));
        CustomerShopCreateDTO negativeDiscount = new CustomerShopCreateDTO();
        negativeDiscount.setDiscountPercent(-1.0);
        assertThrows(RuntimeException.class, () -> customerShopService.update(shop.getId(), negativeDiscount));
    }

    private ShopProductPricesUpdateDTO.Entry entry(Product product, Double price) {
        ShopProductPricesUpdateDTO.Entry e = new ShopProductPricesUpdateDTO.Entry();
        e.setProductId(product.getId());
        e.setPrice(price);
        return e;
    }
}
