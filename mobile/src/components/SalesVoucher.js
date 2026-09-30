import React, { useMemo } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import AppButton from './AppButton';
import { useTheme } from '../theme';

/**
 * The per-shop voucher shown to a salesman (LMT or Local) right after
 * saving one shop's Sales + Return entry. Built entirely from the SalesDTO
 * the server already returns from POST /sales/shop-visit — nothing here is
 * guessed or re-derived client-side; every number is what the server saved.
 *
 * Deliberately does NOT include Dispatch/van stock, Unsold, Vehicle,
 * Driver, Institution Sale, cash denomination counting, or the Market
 * B/F credit balance — none of those exist as data in this system today.
 */

function formatRs(amount) {
  const n = Number(amount) || 0;
  const rounded = Math.round((n + Number.EPSILON) * 100) / 100;
  return Number.isInteger(rounded)
    ? rounded.toLocaleString('en-US')
    : rounded.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** "14:32:00" -> "2:32 PM". Falls back to the raw string if it doesn't parse. */
function formatTime(saleTime) {
  if (!saleTime) return '';
  const match = /^(\d{1,2}):(\d{2})/.exec(saleTime);
  if (!match) return saleTime;
  let hour = parseInt(match[1], 10);
  const minute = match[2];
  const suffix = hour >= 12 ? 'PM' : 'AM';
  hour = hour % 12 || 12;
  return `${hour}:${minute} ${suffix}`;
}

/**
 * One row per PRODUCT, merging its SALE and RETURN lines (a shop visit can
 * have both for the same product). unitPrice is the same base price on
 * both line types (see SalesService.submitShopVisit — only SALE lines are
 * discounted), so there is exactly one price to show per product.
 */
function buildRows(items) {
  const byProduct = new Map();
  (items || []).forEach((item) => {
    const key = item.productId ?? item.productName;
    let row = byProduct.get(key);
    if (!row) {
      row = {
        key,
        productName: item.productName,
        sold: 0,
        returned: 0,
        unitPrice: item.unitPrice ?? 0,
        discountPercent: 0,
        netAmount: 0,
        returnAmount: 0,
      };
      byProduct.set(key, row);
    }
    if (item.transactionType === 'RETURN') {
      row.returned += item.quantity ?? 0;
      row.returnAmount += item.totalPrice ?? 0;
    } else if (item.transactionType === 'UNSOLD') {
      // Unsold lines don't belong to a shop visit — ignored defensively.
    } else {
      row.sold += item.quantity ?? 0;
      row.netAmount += item.totalPrice ?? 0;
      if (item.discountPercent) row.discountPercent = item.discountPercent;
    }
  });
  return Array.from(byProduct.values());
}

/**
 * record: the SalesDTO returned by POST /sales/shop-visit.
 * salesmanName / employeeId: from AuthContext, not on the DTO.
 * onDone: called when the salesman dismisses the voucher.
 */
export default function SalesVoucher({ record, salesmanName, employeeId, onDone }) {
  const { colors } = useTheme();
  const styles = createStyles(colors);

  const rows = useMemo(() => buildRows(record?.items), [record]);
  const totalSold = rows.reduce((sum, r) => sum + r.sold, 0);
  const totalReturned = rows.reduce((sum, r) => sum + r.returned, 0);
  const saleAmount = Number(record?.totalAmount) || 0;
  const returnAmount = rows.reduce((sum, r) => sum + r.returnAmount, 0);
  const netAmount = saleAmount - returnAmount;
  const flagged = record?.distanceFromShopMeters != null && record.distanceFromShopMeters > 100;

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.headerRow}>
          <MaterialIcons name="receipt-long" size={26} color={colors.primary} />
          <Text style={styles.title}>Sales Voucher</Text>
        </View>

        <View style={styles.card}>
          <InfoRow label="Voucher #" value={record?.id != null ? String(record.id) : '—'} styles={styles} />
          <InfoRow label="Shop" value={record?.customerShopName ? `${record.customerShopName} (${record.customerShopCode || ''})` : '—'} styles={styles} />
          <InfoRow label="Date" value={record?.saleDate || '—'} styles={styles} />
          <InfoRow label="Time" value={formatTime(record?.saleTime) || '—'} styles={styles} />
          <InfoRow label="Salesman" value={salesmanName ? `${salesmanName}${employeeId ? ` (${employeeId})` : ''}` : '—'} styles={styles} />
          {record?.distanceFromShopMeters != null && (
            <InfoRow
              label="GPS"
              value={`${Math.round(record.distanceFromShopMeters)} m from shop${flagged ? ' — flagged' : ''}`}
              valueColor={flagged ? colors.warningDark : undefined}
              styles={styles}
            />
          )}
        </View>

        <View style={styles.tableHeaderRow}>
          <Text style={[styles.th, styles.colName]}>Item</Text>
          <Text style={[styles.th, styles.colNum]}>Sold</Text>
          <Text style={[styles.th, styles.colNum]}>Return</Text>
          <Text style={[styles.th, styles.colAmount]}>Price</Text>
          <Text style={[styles.th, styles.colAmount]}>Net</Text>
        </View>
        {rows.length === 0 ? (
          <Text style={styles.emptyText}>No product lines on this visit.</Text>
        ) : (
          rows.map((row) => (
            <View key={row.key} style={styles.tableRow}>
              <Text style={[styles.td, styles.colName]} numberOfLines={2}>{row.productName}</Text>
              <Text style={[styles.td, styles.colNum]}>{row.sold || '—'}</Text>
              <Text style={[styles.td, styles.colNum]}>{row.returned || '—'}</Text>
              <Text style={[styles.td, styles.colAmount]}>{formatRs(row.unitPrice)}</Text>
              <Text style={[styles.td, styles.colAmount]}>{row.sold ? formatRs(row.netAmount) : '—'}</Text>
            </View>
          ))
        )}

        <View style={styles.summaryBox}>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryLabel}>Total Sold</Text>
            <Text style={styles.summaryValue}>{totalSold} breads</Text>
          </View>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryLabel}>Total Returned</Text>
            <Text style={styles.summaryValue}>{totalReturned} breads</Text>
          </View>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryLabel}>Sale Total (Rs)</Text>
            <Text style={styles.summaryValue}>{formatRs(saleAmount)}</Text>
          </View>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryLabel}>Return Total (Rs)</Text>
            <Text style={styles.summaryValue}>{formatRs(returnAmount)}</Text>
          </View>
          <View style={[styles.summaryRow, styles.summaryRowFinal]}>
            <Text style={styles.summaryLabelFinal}>Net Total (Rs)</Text>
            <Text style={[styles.summaryValueFinal, netAmount < 0 && styles.summaryValueFinalNegative]}>
              {formatRs(netAmount)}
            </Text>
          </View>
          {netAmount < 0 && (
            <Text style={styles.netNegativeNote}>Returns exceeded sales on this visit.</Text>
          )}
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <AppButton title="Done" variant="success" size="lg" icon="check-circle" onPress={onDone} />
      </View>
    </View>
  );
}

function InfoRow({ label, value, valueColor, styles }) {
  return (
    <View style={styles.infoRow}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={[styles.infoValue, valueColor && { color: valueColor }]}>{value}</Text>
    </View>
  );
}

const createStyles = (colors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    content: { padding: 16, paddingBottom: 8 },
    headerRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
    title: { fontSize: 22, fontWeight: 'bold', color: colors.textPrimary, marginLeft: 8 },
    card: {
      backgroundColor: colors.surface,
      borderRadius: 14,
      borderWidth: 1,
      borderColor: colors.border,
      padding: 14,
      marginBottom: 16,
    },
    infoRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 },
    infoLabel: { fontSize: 14, color: colors.textSecondary },
    infoValue: { fontSize: 14, fontWeight: '600', color: colors.textPrimary, flexShrink: 1, textAlign: 'right', marginLeft: 12 },
    tableHeaderRow: {
      flexDirection: 'row',
      backgroundColor: colors.surfaceMuted,
      borderTopLeftRadius: 10,
      borderTopRightRadius: 10,
      paddingVertical: 8,
      paddingHorizontal: 10,
    },
    th: { fontSize: 12, fontWeight: 'bold', color: colors.textSecondary, textTransform: 'uppercase' },
    tableRow: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.surface,
      paddingVertical: 8,
      paddingHorizontal: 10,
      borderBottomWidth: 1,
      borderBottomColor: colors.divider,
    },
    td: { fontSize: 14, color: colors.textPrimary },
    colName: { flex: 1, minWidth: 0, paddingRight: 6 },
    colNum: { width: 48, textAlign: 'center' },
    colAmount: { width: 70, textAlign: 'right' },
    emptyText: { padding: 14, color: colors.textSecondary, textAlign: 'center' },
    summaryBox: {
      marginTop: 16,
      backgroundColor: colors.surface,
      borderRadius: 14,
      borderWidth: 1,
      borderColor: colors.border,
      padding: 14,
    },
    summaryRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 },
    summaryLabel: { fontSize: 14, color: colors.textSecondary },
    summaryValue: { fontSize: 14, fontWeight: '600', color: colors.textPrimary },
    summaryRowFinal: { marginTop: 6, paddingTop: 10, borderTopWidth: 1, borderTopColor: colors.divider },
    summaryLabelFinal: { fontSize: 17, fontWeight: 'bold', color: colors.textPrimary },
    summaryValueFinal: { fontSize: 20, fontWeight: 'bold', color: colors.successDark },
    summaryValueFinalNegative: { color: colors.error },
    netNegativeNote: { fontSize: 12, color: colors.error, textAlign: 'right', marginTop: 2 },
    footer: { padding: 16, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.divider },
  });
