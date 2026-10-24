jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));

import { ApiError, NetworkError } from '../api/client';
import { ALREADY_STOPPED, BUSY_ELSEWHERE, startMeasure, STOP_FAILED, stopMeasure } from './measureStart';

// 범위 보충 REQ-114 — 서버 409는 HTML 오류 화면이라 「요청에 실패했어요 (409)」가 된다. 앱 문구로 바꾸고 다시 읽는다.

test('REQ-114 · 측정 시작이 409면 정해진 문구를 보이고 대시보드를 다시 읽는다', async () => {
  const reload = jest.fn(async () => {});
  const start = jest.fn(async () => {
    throw new ApiError(409, '요청에 실패했어요 (409)');
  });
  expect(await startMeasure({ start, reload }, 7)).toEqual({ error: '다른 곳에서 재는 측정이 있어요 — 그 측정을 멈추면 시작할 수 있어요' });
  expect(BUSY_ELSEWHERE).toBe('다른 곳에서 재는 측정이 있어요 — 그 측정을 멈추면 시작할 수 있어요');
  expect(start).toHaveBeenCalledWith(7);
  expect(reload).toHaveBeenCalledTimes(1);
});

test('REQ-114 · 409가 아닌 실패는 그 문구를 그대로 보이고 다시 읽지 않는다', async () => {
  const reload = jest.fn(async () => {});
  const start = jest.fn(async () => {
    throw new NetworkError();
  });
  expect(await startMeasure({ start, reload }, null)).toEqual({ error: '인터넷에 연결되지 않았어요 — 연결한 뒤 다시 해 주세요' });
  expect(reload).not.toHaveBeenCalled();
});

test('REQ-114 · 시작하면 대시보드를 다시 읽어 측정 화면이 된다', async () => {
  const reload = jest.fn(async () => {});
  expect(await startMeasure({ start: async () => {}, reload }, null)).toEqual({});
  expect(reload).toHaveBeenCalledTimes(1);
});

test('REQ-114 · 공부 시작도 409면 같은 문구를 보이고 대시보드를 다시 읽는다', async () => {
  const reload = jest.fn(async () => {});
  const studyStart = jest.fn(async () => {
    throw new ApiError(409, '요청에 실패했어요 (409)');
  });
  expect(await startMeasure({ start: studyStart, reload }, 3)).toEqual({ error: BUSY_ELSEWHERE });
  expect(studyStart).toHaveBeenCalledWith(3);
  expect(reload).toHaveBeenCalledTimes(1);
});

// PR-S1 사양 REQ-151 — 공부는 여러 기기에서 잰다. 다른 곳에서 끝낸 뒤 켜 둔 화면의 [측정 종료]는 오류가 아니라 안내다.
test('REQ-151 · 정지가 409면 오류가 아니라 「이미 끝난 측정이에요」 안내를 보이고 다시 읽는다', async () => {
  const reload = jest.fn(async () => {});
  const stop = async () => {
    throw new ApiError(409, '요청에 실패했어요 (409)');
  };
  expect(await stopMeasure({ stop, reload })).toEqual({ info: '이미 끝난 측정이에요' });
  expect(ALREADY_STOPPED).toBe('이미 끝난 측정이에요');
  expect(reload).toHaveBeenCalledTimes(1);
});

test('REQ-151 · 그 밖의 정지 실패는 서버·연결 문구(없으면 정해 둔 문구)를 보이고 다시 읽는다', async () => {
  const reload = jest.fn(async () => {});
  const offline = async () => {
    throw new NetworkError();
  };
  expect(await stopMeasure({ stop: offline, reload })).toEqual({ error: new NetworkError().message });
  const html500 = async () => {
    throw new ApiError(500, '요청에 실패했어요 (500)');
  };
  expect(await stopMeasure({ stop: html500, reload })).toEqual({ error: STOP_FAILED });
  expect(STOP_FAILED).toBe('측정을 끝내지 못했어요 — 잠시 뒤 다시 눌러 주세요');
  expect(reload).toHaveBeenCalledTimes(2);
  // 성공도 다시 읽고 알림이 없다
  expect(await stopMeasure({ stop: async () => {}, reload })).toEqual({});
  expect(reload).toHaveBeenCalledTimes(3);
});
