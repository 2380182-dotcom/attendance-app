import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { debugLog } from '../utils/debugLog';

/**
 * Resolve the STOMP/SockJS endpoint from an API base URL.
 * Example: https://host.onrender.com/api -> https://host.onrender.com/ws
 */
export function resolveWebSocketBaseUrl(apiBaseUrl) {
  let base = apiBaseUrl.trim();
  if (base.endsWith('/')) {
    base = base.substring(0, base.length - 1);
  }
  if (base.endsWith('/api')) {
    base = base.substring(0, base.length - 4);
  }
  return `${base}/ws`;
}

/**
 * Connect to the Spring STOMP broker and subscribe to a topic.
 * Returns a disconnect function.
 */
export function connectStompTopic(apiBaseUrl, topic, onMessage) {
  const wsBase = resolveWebSocketBaseUrl(apiBaseUrl);
  let client;

  client = new Client({
    webSocketFactory: () => new SockJS(wsBase),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      debugLog('STOMP', `Connected to ${wsBase}, subscribing to ${topic}`);
      client.subscribe(topic, () => {
        if (onMessage) {
          onMessage();
        }
      });
    },
    onStompError: (frame) => {
      console.error('[STOMP] Broker error:', frame.headers['message'], frame.body);
    },
    onWebSocketError: (event) => {
      console.error('[STOMP] WebSocket error:', event?.message || event);
    },
    onDisconnect: () => {
      debugLog('STOMP', 'Disconnected');
    },
  });

  client.activate();

  return () => {
    if (client && client.active) {
      client.deactivate();
    }
  };
}

/**
 * Subscribe to real-time sales updates on /topic/sales.
 */
export function connectSalesWebSocket(apiBaseUrl, onMessage) {
  return connectStompTopic(apiBaseUrl, '/topic/sales', onMessage);
}

export default {
  resolveWebSocketBaseUrl,
  connectStompTopic,
  connectSalesWebSocket,
};
