package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ProductPricingDTO;
import com.dawnbread.attendance.dto.ProductPricingUpdateDTO;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Role-based pricing (P3): the Sales Dashboard's product-pricing
 * management. Read-only elsewhere in the app — SalesService independently
 * reads Product.agentPrice/salesmanPrice fresh at submission time, never
 * anything cached or client-supplied, so nothing here can be used to
 * smuggle a price into a sale.
 */
@Service
@Transactional
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public List<ProductPricingDTO> getAllForPricing() {
        return productRepository.findAllByOrderByNameAsc().stream()
                .map(p -> new ProductPricingDTO(p.getId(), p.getName(), p.getCategory(), p.getAgentPrice(), p.getSalesmanPrice()))
                .collect(Collectors.toList());
    }

    public ProductPricingDTO updatePricing(Long productId, ProductPricingUpdateDTO dto) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));
        product.setAgentPrice(dto.getAgentPrice());
        product.setSalesmanPrice(dto.getSalesmanPrice());
        Product saved = productRepository.save(product);
        return new ProductPricingDTO(saved.getId(), saved.getName(), saved.getCategory(), saved.getAgentPrice(), saved.getSalesmanPrice());
    }
}
