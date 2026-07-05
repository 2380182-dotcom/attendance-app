import React from 'react';
import StatusChip from './StatusChip';

/**
 * Kept for backward compatibility with existing call sites (NotificationCard.js).
 * Color logic now delegates to StatusChip's canonical mapping instead of its own
 * (previously disagreeing) hardcoded colors — but keeps showing the raw status
 * text, matching this component's original behavior.
 */
export default function StatusBadge({ status }) {
  return <StatusChip status={status} label={status} />;
}
