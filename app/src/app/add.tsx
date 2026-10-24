// 책 담기(PR-G 사양 §5.3 — 피그마 R7 S-ADD). 제목 검색과 바코드를 한 화면이 맡는다.
// 담은 책은 「읽는 중」이고 화면에 남는다 — 맨 위 알림 + [책 보기](결정 B ①). 바코드 결과는 바로 담지 않는다(§8-5).
// ?shelf=study = 공부 책 담기(PR-S1 사양 §5.6) — 다른 것은 셋: 머리 · 「담음」(공부 서재 isbn 기준) · 담기(공부 6필드 · 알림에 동작 없음).
import { Camera, CameraView } from 'expo-camera';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Linking, View } from 'react-native';

import { library, NetworkError, study } from '../api/client';
import type { SearchRow } from '../api/types';
import { Cover } from '../components/Cover';
import { Button, Field, Notice, Screen, Txt, useStacked } from '../components/ui';
import { addBody } from '../lib/bookAdd';
import { objectParticle } from '../lib/format';
import { toIsbn13 } from '../lib/isbn';
import { studyAddBody, studyOwned } from '../lib/studyShelf';
import { color, line, space } from '../theme/tokens';

type NoticeState = { message: string; tone?: 'info' | 'error'; action?: { label: string; onPress: () => void } } | null;

const errText = (e: unknown) => (e instanceof Error ? e.message : String(e));

export default function AddBook() {
  const { scan, shelf } = useLocalSearchParams<{ scan?: string; shelf?: string }>();
  const forStudy = shelf === 'study';
  const query = useRef('');
  const [inputKey, setInputKey] = useState(0); // 바코드 결과 때 검색어 칸을 비운다(비제어 입력 — 다시 그려 비운다)
  const [rows, setRows] = useState<SearchRow[] | null>(null);
  const [fromBarcode, setFromBarcode] = useState(false);
  const [searching, setSearching] = useState(false);
  const [notice, setNotice] = useState<NoticeState>(null);
  const [addingKey, setAddingKey] = useState<string | null>(null);
  const sub = useRef<ReturnType<typeof CameraView.onModernBarcodeScanned> | null>(null);
  const stacked = useStacked();

  // 화면을 떠날 때도 구독을 지운다 — 안 지우면 다음 스캔에 두 번 불린다(§11-8)
  useEffect(() => () => sub.current?.remove(), []);

  // 공부 책 담기 — 검색 응답의 owned는 독서 서재 기준이라 공부 서재 isbn으로 다시 쓴다(REQ-150). 못 읽으면 빈 집합(전부 [담기] — 서버가 같은 isbn은 기존 행을 돌려준다).
  // 결과가 늘 own()을 거쳐 들어오므로, 집합이 늦게 왔을 때 남은 owned: true는 방금 담은 행뿐이다 — 그 행은 지킨다.
  const studyIsbns = useRef<ReadonlySet<string> | null>(null);
  const own = (list: SearchRow[]) => (forStudy ? studyOwned(list, studyIsbns.current) : list);
  useEffect(() => {
    if (!forStudy) return;
    void study
      .shelf()
      .then((r) => new Set(r.books.flatMap((b) => (b.isbn13 ? [b.isbn13] : []))), () => new Set<string>())
      .then((set) => {
        studyIsbns.current = set;
        setRows((list) => list && studyOwned(list, set).map((r, i) => (list[i].owned ? { ...r, owned: true } : r)));
      });
  }, [forStudy]);

  // ?scan=1 — 사용자가 「바코드로 담기」를 눌러 온 것이라 「진입 직후 덮기」가 아니다. 마운트 때 한 번만 연다.
  const scanOnce = useRef(scan === '1');
  useEffect(() => {
    if (scanOnce.current) {
      scanOnce.current = false;
      void openScanner();
    }
  }, []);

  async function search() {
    const q = query.current.trim();
    if (!q) return;
    setNotice(null);
    setSearching(true);
    try {
      const r = await library.search(q);
      setRows(own(r.results));
      setFromBarcode(false);
    } catch (e) {
      setNotice({ message: errText(e), tone: 'error' });
    }
    setSearching(false);
  }

  async function openScanner() {
    setNotice(null);
    const perm = await Camera.requestCameraPermissionsAsync().catch(() => null);
    if (!perm?.granted) {
      setNotice({
        message: '카메라를 쓸 수 없어요 — 설정에서 책생글의 카메라를 켜 주세요',
        tone: 'error',
        action: { label: '설정 열기', onPress: () => void Linking.openSettings() },
      });
      return;
    }
    sub.current?.remove();
    sub.current = CameraView.onModernBarcodeScanned((ev) => {
      // 첫 값 하나만 — 받으면 구독을 지우고 스캐너를 닫는다(스파이크 순서)
      sub.current?.remove();
      sub.current = null;
      void CameraView.dismissScanner();
      void lookup(ev.data);
    });
    try {
      await CameraView.launchScanner({ barcodeTypes: ['ean13'] });
    } catch {
      sub.current?.remove();
      sub.current = null;
      setNotice({ message: '바코드 스캐너를 열지 못했어요 — 제목으로 찾아 주세요', tone: 'error' });
    }
  }

  async function lookup(data: string) {
    const isbn = toIsbn13(data);
    if (!isbn) {
      setNotice({
        message: '책 바코드가 아니에요 — 뒤표지에서 978·979로 시작하는 바코드를 비춰 주세요',
        tone: 'error',
        action: { label: '다시 비추기', onPress: () => void openScanner() },
      });
      return;
    }
    setSearching(true);
    try {
      const r = await library.byIsbn(isbn);
      if (r.results.length === 0) {
        setNotice({ message: '이 바코드로 찾은 책이 없어요 — 제목으로 찾아 주세요', tone: 'error' });
      } else {
        query.current = '';
        setInputKey((k) => k + 1);
        setRows(own(r.results.slice(0, 1)));
        setFromBarcode(true);
      }
    } catch (e) {
      setNotice({ message: errText(e), tone: 'error' });
    }
    setSearching(false);
  }

  async function add(row: SearchRow, key: string) {
    if (addingKey) return;
    setAddingKey(key);
    try {
      const saved = forStudy ? await study.add(studyAddBody(row)) : await library.add(addBody(row));
      setRows((list) => list?.map((r, i) => (rowKey(r, i) === key ? { ...r, owned: true } : r)) ?? null);
      const named = `『${row.title}』${objectParticle(row.title)}`;
      // 공부 책은 동작 없음 — 뒤로 = 공부 탭(공부 책 화면이 없다)
      setNotice(
        forStudy
          ? { message: `${named} 공부 서재에 담았어요` }
          : { message: `${named} 읽는 중에 담았어요`, action: { label: '책 보기', onPress: () => router.push(`/book/${saved.id}`) } },
      );
    } catch (e) {
      setNotice({
        message: e instanceof NetworkError ? e.message : '책을 담지 못했어요 — 잠시 뒤 다시 눌러 주세요',
        tone: 'error',
      });
    }
    setAddingKey(null);
  }

  return (
    <Screen scroll stacked>
      <Txt v="D2">{forStudy ? '공부 책 담기' : '책 담기'}</Txt>
      <Field
        key={inputKey}
        label="책 찾기"
        placeholder="제목이나 저자"
        returnKeyType="search"
        clearButtonMode="while-editing"
        autoCorrect={false}
        onChangeText={(t) => {
          query.current = t;
        }}
        onSubmitEditing={() => void search()}
      />
      <Button kind="outline" label="바코드로 담기" onPress={() => void openScanner()} />
      {notice ? <Notice message={notice.message} tone={notice.tone} action={notice.action} /> : null}
      {searching ? (
        <View style={{ flexDirection: 'row', gap: space[2], alignItems: 'center' }}>
          <ActivityIndicator color={color.ink} />
          <Txt v="L1" style={{ color: color.text }}>
            찾는 중이에요
          </Txt>
        </View>
      ) : rows && rows.length === 0 ? (
        <Txt v="L1" style={{ color: color.text }}>
          찾은 책이 없어요 — 다른 낱말로 찾아 보세요
        </Txt>
      ) : rows ? (
        <View>
          {fromBarcode ? (
            <Txt v="L1" style={{ color: color.muted }}>
              바코드로 찾은 책
            </Txt>
          ) : null}
          {rows.map((r, i) => {
            const key = rowKey(r, i);
            return <ResultRow key={key} row={r} stacked={stacked} busy={addingKey === key} onAdd={() => void add(r, key)} />;
          })}
        </View>
      ) : null}
    </Screen>
  );
}

/** 「담음」 갱신 키 — isbn13, 없으면 행 순번(§5.3) */
const rowKey = (r: SearchRow, i: number) => r.isbn13 ?? `#${i}`;

function ResultRow(p: { row: SearchRow; stacked: boolean; busy: boolean; onAdd: () => void }) {
  const { row } = p;
  const sub = [row.authorShort, row.publisher].filter(Boolean).join(' · ');
  const action = row.owned ? (
    <Txt v="T1" style={{ color: color.muted }}>
      담음
    </Txt>
  ) : (
    <Button kind="outline" label="담기" busy={p.busy} accessibilityHint={`『${row.title}』${objectParticle(row.title)} 서재에 담아요`} onPress={p.onAdd} />
  );
  return (
    <View
      style={{
        flexDirection: 'row',
        gap: space[3],
        paddingVertical: space[3],
        borderBottomWidth: line.thin,
        borderBottomColor: color.line,
        alignItems: p.stacked ? 'flex-start' : 'center',
      }}
    >
      <Cover title={row.title} coverUrl={row.coverUrl} size="row" />
      <View style={{ flex: 1, gap: space[2] }}>
        <View accessible accessibilityLabel={sub ? `${row.title}, ${sub}` : row.title} style={{ gap: 2 }}>
          <Txt v="H3" numberOfLines={2}>
            {row.title}
          </Txt>
          {sub ? (
            <Txt v="L1" style={{ color: color.text }}>
              {sub}
            </Txt>
          ) : null}
        </View>
        {p.stacked ? <View style={{ alignSelf: 'flex-start' }}>{action}</View> : null}
      </View>
      {p.stacked ? null : action}
    </View>
  );
}
