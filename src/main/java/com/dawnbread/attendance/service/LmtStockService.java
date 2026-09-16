package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.LmtDailyStockDTO;
import com.dawnbread.attendance.dto.LmtDailyStockItemDTO;
import com.dawnbread.attendance.dto.LmtMorningStockRequest;
import com.dawnbread.attendance.dto.LmtReconcileItemRequest;
import com.dawnbread.attendance.dto.LmtReconcileRequest;
import com.dawnbread.attendance.dto.LmtShopReturnDTO;
import com.dawnbread.attendance.dto.LmtStockItemRequest;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.LmtDailyStock;
import com.dawnbread.attendance.entity.LmtDailyStockItem;
import com.dawnbread.attendance.entity.LmtStockStatus;
import com.dawnbread.attendance.entity.Product;
import com.dawnbread.attendance.repository.LmtDailyStockItemRepository;
import com.dawnbread.attendance.repository.LmtDailyStockRepository;
import com.dawnbread.attendance.repository.ProductRepository;
import com.dawnbread.attendance.repository.SaleItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LMT Phase C — stock reconciliation. Entirely new tables/flow; never
 * touches Agent, Attendance, SaleItem, or SalesRecord rows (SaleItem is
 * only ever read here, for the Sold aggregation, never written).
 */
@Service
@Transactional
public class LmtStockService {

    @Autowired
    private LmtDailyStockRepository lmtDailyStockRepository;

    @Autowired
    private LmtDailyStockItemRepository lmtDailyStockItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private AgentService agentService;

    public Optional<LmtDailyStockDTO> getToday(Long agentId) {
        return lmtDailyStockRepository.findByAgentIdAndStockDate(agentId, LocalDate.now())
                .map(this::convertToDTO);
    }

    /**
     * Phase D (C6): the Sales Department's reconciliation report — every
     * LmtDailyStock in a date range, across all LMTs or one, RECONCILED and
     * still-OPEN rows both included (an OPEN row past its day is itself a
     * finding — "not reconciled" — not something to hide). Read-only,
     * management-gated at the controller.
     */
    public List<LmtDailyStockDTO> getReconciliationReport(LocalDate startDate, LocalDate endDate, Long agentId) {
        List<LmtDailyStock> stocks = agentId != null
                ? lmtDailyStockRepository.findByAgentIdAndStockDateBetween(agentId, startDate, endDate)
                : lmtDailyStockRepository.findByStockDateBetween(startDate, endDate);
        return stocks.stream().map(this::convertToReportDTO).collect(Collectors.toList());
    }

    /**
     * Same as convertToDTO, plus the per-shop Returned breakdown — kept as
     * a separate method (rather than adding this to convertToDTO itself)
     * so the LMT's own self-service calls (getToday, enterMorningStock,
     * reconcile) never carry shop-wise return data, only this
     * management-only report path does.
     */
    private LmtDailyStockDTO convertToReportDTO(LmtDailyStock stock) {
        LmtDailyStockDTO dto = convertToDTO(stock);

        Map<Long, List<LmtShopReturnDTO>> shopReturnsByProduct = new HashMap<>();
        for (Object[] row : saleItemRepository.sumReturnedQuantityByShopForAgentAndDateRange(
                stock.getAgentId(), stock.getStockDate(), stock.getStockDate())) {
            LmtShopReturnDTO shopReturn = new LmtShopReturnDTO();
            shopReturn.setShopId((Long) row[0]);
            shopReturn.setShopCode((String) row[1]);
            shopReturn.setShopName((String) row[2]);
            Long qty = (Long) row[5];
            shopReturn.setReturnedQty(qty != null ? qty.intValue() : 0);

            Long productId = (Long) row[3];
            shopReturnsByProduct.computeIfAbsent(productId, k -> new java.util.ArrayList<>()).add(shopReturn);
        }

        for (LmtDailyStockItemDTO itemDto : dto.getItems()) {
            itemDto.setReturnsByShop(shopReturnsByProduct.getOrDefault(itemDto.getProductId(), new java.util.ArrayList<>()));
        }
        return dto;
    }

    /**
     * Morning stock entry — creates the day's LmtDailyStock (status OPEN)
     * plus one item per product. Rejects a second call for the same
     * agent+day rather than silently overwriting: an accidental double
     * submission shouldn't wipe out a day already in progress. The V20
     * unique index (agent_id, stock_date) is the race-proof backstop behind
     * this in-memory check, same dual-layer pattern as SalesService's
     * shop-visit dedupe.
     */
    public LmtDailyStockDTO enterMorningStock(LmtMorningStockRequest request) {
        Agent agent = agentService.getAgentById(request.getAgentId())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + request.getAgentId()));

        LocalDate today = LocalDate.now();
        if (lmtDailyStockRepository.findByAgentIdAndStockDate(agent.getId(), today).isPresent()) {
            throw new IllegalArgumentException("Stock has already been entered for today.");
        }

        LmtDailyStock stock = new LmtDailyStock();
        stock.setAgentId(agent.getId());
        stock.setStockDate(today);
        stock.setStatus(LmtStockStatus.OPEN);
        stock.setCreatedAt(LocalDateTime.now());
        stock = lmtDailyStockRepository.save(stock);

        for (LmtStockItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + itemReq.getProductId()));
            LmtDailyStockItem item = new LmtDailyStockItem();
            item.setLmtDailyStock(stock);
            item.setProduct(product);
            item.setOpeningStock(itemReq.getOpeningStock());
            lmtDailyStockItemRepository.save(item);
        }

        return convertToDTO(lmtDailyStockRepository.findById(stock.getId()).orElseThrow());
    }

    /**
     * Night reconciliation — computes Sold and Returned (both from that
     * day's SaleItem SALE/RETURN rows, the same aggregation pattern for
     * each — see SaleItemRepository) and Missing (opening - sold -
     * returned - unsold) per product, then flips status to RECONCILED.
     * Returned is no longer entered manually here: per the LMT flow
     * refinement, returns are captured per-shop on the shop-visit screen,
     * not as a day-level lump figure. Missing is allowed to go negative
     * (an LMT selling more than their declared opening stock) and is never
     * rejected — recorded as a real discrepancy for the Sales Department,
     * per the build plan's "recorded, not blocked" rule. Items not named
     * in the request default to unsoldQty=0 for the same reason — a
     * partially-filled reconciliation still completes.
     */
    public LmtDailyStockDTO reconcile(LmtReconcileRequest request) {
        LocalDate today = LocalDate.now();
        LmtDailyStock stock = lmtDailyStockRepository.findByAgentIdAndStockDate(request.getAgentId(), today)
                .orElseThrow(() -> new IllegalArgumentException("No stock has been entered for today — enter morning stock first."));

        Map<Long, LmtReconcileItemRequest> byProductId = new HashMap<>();
        if (request.getItems() != null) {
            for (LmtReconcileItemRequest itemReq : request.getItems()) {
                byProductId.put(itemReq.getProductId(), itemReq);
            }
        }

        Map<Long, Integer> soldByProduct = new HashMap<>();
        for (Object[] row : saleItemRepository.sumSoldQuantityByAgentAndDate(request.getAgentId(), today)) {
            Long productId = (Long) row[0];
            Long sold = (Long) row[1];
            soldByProduct.put(productId, sold != null ? sold.intValue() : 0);
        }

        Map<Long, Integer> returnedByProduct = new HashMap<>();
        for (Object[] row : saleItemRepository.sumReturnedQuantityByAgentAndDate(request.getAgentId(), today)) {
            Long productId = (Long) row[0];
            Long returned = (Long) row[1];
            returnedByProduct.put(productId, returned != null ? returned.intValue() : 0);
        }

        List<LmtDailyStockItem> items = lmtDailyStockItemRepository.findByLmtDailyStockId(stock.getId());
        for (LmtDailyStockItem item : items) {
            Long productId = item.getProduct().getId();
            LmtReconcileItemRequest itemReq = byProductId.get(productId);
            int unsoldQty = itemReq != null ? itemReq.getUnsoldQty() : 0;
            int soldQty = soldByProduct.getOrDefault(productId, 0);
            int returnedQty = returnedByProduct.getOrDefault(productId, 0);

            item.setReturnedQty(returnedQty);
            item.setUnsoldQty(unsoldQty);
            item.setSoldQty(soldQty);
            item.setMissingQty(item.getOpeningStock() - soldQty - returnedQty - unsoldQty);
            lmtDailyStockItemRepository.save(item);
        }

        stock.setStatus(LmtStockStatus.RECONCILED);
        stock.setReconciledAt(LocalDateTime.now());
        stock = lmtDailyStockRepository.save(stock);

        return convertToDTO(stock);
    }

    private LmtDailyStockDTO convertToDTO(LmtDailyStock stock) {
        LmtDailyStockDTO dto = new LmtDailyStockDTO();
        dto.setId(stock.getId());
        dto.setAgentId(stock.getAgentId());
        dto.setStockDate(stock.getStockDate());
        dto.setStatus(stock.getStatus().name());
        dto.setCreatedAt(stock.getCreatedAt());
        dto.setReconciledAt(stock.getReconciledAt());

        List<LmtDailyStockItemDTO> itemDtos = lmtDailyStockItemRepository.findByLmtDailyStockId(stock.getId())
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        dto.setItems(itemDtos);
        return dto;
    }

    private LmtDailyStockItemDTO convertToDTO(LmtDailyStockItem item) {
        LmtDailyStockItemDTO dto = new LmtDailyStockItemDTO();
        dto.setId(item.getId());
        dto.setProductId(item.getProduct().getId());
        dto.setProductName(item.getProduct().getName());
        dto.setOpeningStock(item.getOpeningStock());
        dto.setReturnedQty(item.getReturnedQty());
        dto.setUnsoldQty(item.getUnsoldQty());
        dto.setSoldQty(item.getSoldQty());
        dto.setMissingQty(item.getMissingQty());
        return dto;
    }
}
