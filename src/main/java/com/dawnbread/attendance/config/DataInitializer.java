package com.dawnbread.attendance.config;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("!prod")
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // This runs at app startup, outside any HTTP request, so there is no
        // TenantContext yet — resolve/create the default tenant explicitly
        // and stamp it on everything seeded below, same as tests do.
        Tenant defaultTenant = tenantRepository.findByCompanyCodeIgnoreCase("DAWNBREAD")
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setCompanyCode("DAWNBREAD");
                    t.setName("Dawn Bread");
                    t.setIsActive(true);
                    t.setCreatedAt(LocalDateTime.now());
                    t.setCreatedBy("SYSTEM");
                    return tenantRepository.save(t);
                });

        // Seed Admin user if absent
        if (!agentRepository.existsByAgentId("ADMIN001")) {
            Agent admin = new Agent();
            admin.setTenantId(defaultTenant.getId());
            admin.setAgentId("ADMIN001");
            admin.setName("System Admin");
            admin.setEmail("admin@attendance.com");
            admin.setPhone("1234567890");
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setRole("ADMIN");
            admin.setDepartment("MANAGEMENT");
            admin.setCreatedAt(LocalDateTime.now());
            admin.setCreatedBy("SYSTEM");
            admin.setIsActive(true);
            agentRepository.save(admin);
            System.out.println("Seeded admin user: ADMIN001 / admin");
        }

        // Seed Demo Agent user if absent
        if (!agentRepository.existsByAgentId("DEMO001")) {
            Agent agent = new Agent();
            agent.setTenantId(defaultTenant.getId());
            agent.setAgentId("DEMO001");
            agent.setName("Demo Agent");
            agent.setEmail("demo@attendance.com");
            agent.setPhone("0987654321");
            agent.setPassword(passwordEncoder.encode("password"));
            agent.setRole("AGENT");
            agent.setDepartment("SALES");
            agent.setCreatedAt(LocalDateTime.now());
            agent.setCreatedBy("SYSTEM");
            agent.setIsActive(true);
            agentRepository.save(agent);
            System.out.println("Seeded demo agent user: DEMO001 / password");
        }

        // Seed 40 Dawn Bread products if absent
        if (productRepository.count() == 0) {
            String[] names = {
                "LARGE BREAD", "SMALL BREAD", "MILKY BREAD", "BRAN BREAD", "FRUIT CAKE",
                "PLAIN CAKE", "MUFFIN CHOCOLATE", "MUFFIN VANILLA", "MUFFIN BLUEBERRY", "BURGER BUN LARGE",
                "BURGER BUN SMALL", "SHAWARMA BREAD", "SANDWICH BREAD", "MULTI GRAIN BREAD", "GARLIC BREAD",
                "BUTTER RUSK", "CAKE RUSK LARGE", "CAKE RUSK SMALL", "ZEERA BISCUIT", "CHOCOLATE CHIP COOKIE",
                "ALMOND BISCUIT", "COCONUT COOKIE", "CREAM ROLL CHOCOLATE", "CREAM ROLL VANILLA", "DONUT GLAZED",
                "DONUT CHOCOLATE", "CROISSANT BUTTER", "CROISSANT CHOCOLATE", "CHICKEN PATTIES", "VEGGIE PATTIES",
                "PIZZA SLICE", "SHEERMAL", "TAFTAN", "WHOLE WHEAT ROTI", "PARATHA READY-TO-COOK",
                "PLAIN RUSK", "DIET RUSK", "ATTA BREAD", "BROWNIE FUDGE", "SLICED POUND CAKE"
            };
            Double[] prices = {
                150.0, 80.0, 120.0, 160.0, 450.0,
                350.0, 60.0, 60.0, 70.0, 90.0,
                50.0, 80.0, 140.0, 180.0, 130.0,
                110.0, 240.0, 130.0, 70.0, 90.0,
                120.0, 80.0, 40.0, 40.0, 85.0,
                95.0, 120.0, 140.0, 80.0, 70.0,
                150.0, 100.0, 100.0, 120.0, 160.0,
                90.0, 100.0, 160.0, 150.0, 220.0
            };
            String[] descriptions = {
                "Standard large size white sandwich bread.", "Standard small size white sandwich bread.", "Sweet and soft milk-infused bread.", "High fiber whole wheat bran bread.", "Rich cake packed with dried fruits.",
                "Classic plain sponge cake.", "Delicious chocolate muffin with chocolate chips.", "Soft vanilla muffin.", "Sweet muffin with blueberries.", "Large size soft burger buns (pack of 4).",
                "Regular size soft burger buns (pack of 4).", "Fresh flat shawarma pocket breads.", "Premium quality sandwich loaf.", "Healthy multi-grain sliced bread.", "Toasted garlic flavored bread slice.",
                "Crispy butter baked toast rusks.", "Double baked sweet cake rusks (large box).", "Double baked sweet cake rusks (small box).", "Salty cumin-flavored crunchy tea biscuits.", "Crunchy cookies loaded with chocolate chips.",
                "Premium biscuits flavored with real almonds.", "Crispy biscuits with grated coconut.", "Crispy puff pastry rolls filled with chocolate cream.", "Crispy puff pastry rolls filled with vanilla cream.", "Classic glazed ring donut.",
                "Chocolate topped ring donut.", "Flaky butter croissant.", "Flaky croissant filled with chocolate.", "Savory chicken puff pastry.", "Savory vegetable puff pastry.",
                "Mini cheese and chicken pizza slice.", "Traditional sweet flatbread.", "Traditional soft flatbread.", "Whole wheat soft rotis (pack of 5).", "Layered frozen parathas (pack of 5).",
                "Crispy plain baked rusks.", "Sugar-free crispy diet rusks.", "100% natural wheat flour bread.", "Rich fudge chocolate brownie.", "Classic sliced pound cake."
            };

            for (int i = 0; i < names.length; i++) {
                Product p = new Product();
                p.setTenantId(defaultTenant.getId());
                p.setName(names[i]);
                p.setPrice(prices[i]);
                p.setDescription(descriptions[i]);
                // Set simple UI compatible image and thumbnail links
                p.setImageUrl("https://placehold.co/100x100/1976D2/FFFFFF?text=" + names[i].replace(" ", "+"));
                p.setThumbnailUrl("https://placehold.co/50x50/1976D2/FFFFFF?text=" + names[i].replace(" ", "+"));
                p.setIsActive(true);
                productRepository.save(p);
            }
            System.out.println("Seeded " + names.length + " products into the catalog.");
        }
    }
}
