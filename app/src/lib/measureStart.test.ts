jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));

import { ApiError, NetworkError } from '../api/client';
import { BUSY_ELSEWHERE, startReading } from './measureStart';

// 범위 보충 REQ-114 — 서버 409는 HTML 오류 화면이라 「요청에 실패했어요 (409)」가 된다. 앱 문구로 바꾸고 다시 읽는다.

test('REQ-114 · 측정 시작이 409면 정해진 문구를 보이고 대시보드를 다시 읽는다', async () => {
  const reload = jest.fn(async () => {});
  const start = jest.fn(async () => {
    throw new ApiError(409, '요청에 실패했어요 (409)');
  });
  expect(await startReading({ start, reload }, 7)).toEqual({ error: '다른 곳에서 재는 측정이 있어요 — 그 측정을 멈추면 시작할 수 있어요' });
  expect(BUSY_ELSEWHERE).toBe('다른 곳에서 재는 측정이 있어요 — 그 측정을 멈추면 시작할 수 있어요');
  expect(start).toHaveBeenCalledWith(7);
  expect(reload).toHaveBeenCalledTimes(1);
});

test('REQ-114 · 409가 아닌 실패는 그 문구를 그대로 보이고 다시 읽지 않는다', async () => {
  const reload = jest.fn(async () => {});
  const start = jest.fn(async () => {
    throw new NetworkError();
  });
  expect(await startReading({ start, reload }, null)).toEqual({ error: '인터넷에 연결되지 않았어요 — 연결한 뒤 다시 해 주세요' });
  expect(reload).not.toHaveBeenCalled();
});

test('REQ-114 · 시작하면 대시보드를 다시 읽어 측정 화면이 된다', async () => {
  const reload = jest.fn(async () => {});
  expect(await startReading({ start: async () => {}, reload }, null)).toEqual({});
  expect(reload).toHaveBeenCalledTimes(1);
});
