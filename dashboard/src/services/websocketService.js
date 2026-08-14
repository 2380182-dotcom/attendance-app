import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

/** https://host.onrender.com/api -> https://host.onrender.com/ws — same derivation as the mobile app's WebSocketService.js. */
function resolveWebSocketBaseUrl() {
  let base = API_URL.trim();
  if (base.endsWith('/')) base = base.slice(0, -1);
  if (base.endsWith('/api')) base = base.slice(0, -4);
  return `${base}/ws`;
}

/**
 * Connect to the Spring STOMP broker and subscribe to a topic. Unlike the
 * mobile version (which just triggers a refetch), this hands the parsed
 * message body to the caller — the live feed needs the actual notification,
 * not just a "something changed" signal.
 * Returns a disconnect function.
 */
export function connectStompTopic(topic, onMessage, onStatusChange) {
  const client = new Client({
    webSocketFactory: () => new SockJS(resolveWebSocketBaseUrl()),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      onStatusChange?.('connected');
      client.subscribe(topic, (message) => {
        try {
          onMessage(JSON.parse(message.body));
        } catch (e) {
          console.error('Failed to parse STOMP message body', e);
        }
      });
    },
    onStompError: (frame) => {
      console.error('[STOMP] Broker error:', frame.headers['message'], frame.body);
      onStatusChange?.('error');
    },
    onWebSocketError: (event) => {
      // The most likely cause of this specific failure is CORS rejecting the
      // SockJS handshake — the browser's console will show the real reason,
      // this callback only knows that the connection attempt failed.
      console.error('[STOMP] WebSocket error:', event?.message || event);
      onStatusChange?.('error');
    },
    onDisconnect: () => onStatusChange?.('disconnected'),
  });

  client.activate();

  return () => {
    if (client.active) client.deactivate();
  };
}
