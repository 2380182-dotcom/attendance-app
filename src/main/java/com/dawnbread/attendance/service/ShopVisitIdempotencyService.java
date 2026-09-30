package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ShopVisitItemRequest;
import com.dawnbread.attendance.entity.SaleItem;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.exception.ShopVisitConflictException;
import com.dawnbread.attendance.repository.SalesRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A separate bean, not a method on SalesService, specifically so its
 * REQUIRES_NEW actually takes effect: Spring's @Transactional only works
 * through the proxy, and a call from one method of SalesService to another
 * on `this` never goes through it (the classic self-invocation gap) — and
 * calling it from SalesController instead sidesteps that entirely, since
 * controller and service are already different beans.
 *
 * Needed because after submitShopVisit's save() throws
 * DataIntegrityViolationException on a requestId race, Hibernate poisons
 * that session — it refuses any further operation, including a plain
 * SELECT, until rollback (confirmed by a real AssertionFailure hitting
 * exactly this). submitShopVisit instead lets its own transaction roll back
 * cleanly (throwing ShopVisitRequestIdRaceException), and this class
 * resolves the outcome in a genuinely new transaction/session afterward.
 */
@Service
public class ShopVisitIdempotencyService {

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    /** Order-independent — price/discount are server-computed, never part of the identity check. */
    public boolean itemsMatch(List<SaleItem> existing, List<ShopVisitItemRequest> incoming) {
        if (existing.size() != incoming.size()) {
            return false;
        }
        Map<String, Long> existingCounts = existing.stream()
                .map(i -> i.getProduct().getId() + ":" + i.getQuantity() + ":" + i.getTransactionType())
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
        Map<String, Long> incomingCounts = incoming.stream()
                .map(i -> i.getProductId() + ":" + i.getQuantity() + ":" + i.getTransactionType().trim().toUpperCase())
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
        return existingCounts.equals(incomingCounts);
    }

    /**
     * Used both for the early (pre-attempt) idempotent-replay check inside
     * submitShopVisit's own healthy transaction, and — via
     * resolveAfterConflict below — for the post-race recovery path.
     */
    public SalesRecord findByRequestIdOrNull(String requestId) {
        return salesRecordRepository.findByRequestId(requestId).orElse(null);
    }

    /**
     * Called only after a requestId race (see class doc) — always in a
     * brand-new transaction. Returns the winning record on a genuine
     * retry (matching items), throws ShopVisitConflictException on a
     * mismatch, and throws IllegalArgumentException in the (should not
     * normally happen) case where the unique-index violation wasn't
     * actually about this requestId at all.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SalesRecord resolveAfterConflict(String requestId, List<ShopVisitItemRequest> incomingItems) {
        SalesRecord winner = salesRecordRepository.findByRequestId(requestId).orElse(null);
        if (winner == null) {
            throw new IllegalArgumentException(
                    "Duplicate entry: a product/transaction in this submission has already been recorded today for this shop.");
        }
        if (itemsMatch(winner.getItems(), incomingItems)) {
            return winner;
        }
        throw new ShopVisitConflictException(
                "This requestId was already used for a different set of items — not a retry of the same visit.");
    }
}
