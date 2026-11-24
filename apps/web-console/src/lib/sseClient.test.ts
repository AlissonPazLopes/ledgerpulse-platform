import { describe, expect, it } from 'vitest';
import { parseSseFrame } from './sseClient';

describe('sseClient parseSseFrame', () => {
  it('parses event name, id, and JSON data payload ignoring SSE comment lines', () => {
    const rawBlock = [
      ':heartbeat-comment',
      'id: evt-001',
      'event: payment.settled',
      'data: {"eventId":"evt-001","eventType":"payment.settled","payload":{"amountCents":15000}}',
    ].join('\n');

    const parsed = parseSseFrame(rawBlock);
    expect(parsed).toEqual({
      eventName: 'payment.settled',
      id: 'evt-001',
      data: '{"eventId":"evt-001","eventType":"payment.settled","payload":{"amountCents":15000}}',
    });
  });

  it('returns null for comment-only keepalive blocks without data', () => {
    expect(parseSseFrame(': keepalive')).toBeNull();
    expect(parseSseFrame('')).toBeNull();
  });
});
