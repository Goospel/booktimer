// 서재(PR-G 사양 §5.1 · §5.2 — 피그마 R7 S-LIB · S-LIB-EMPTY). 독서 서재 책만 세 단(읽는 중 → 다 읽음 → 읽고 싶어요).
// 책이 있는 서재의 주 동작은 「책 열기」라 검정 알약이 없다 — 빈 서재에서만 「제목으로 찾기」가 검정(§8-4).
// 맨 아래 「책 없는 여백」(책생글 A1 — J75 · C9 · F1 §4.2-4) — 0개면 단째 숨김. 빈 서재에도 단다(새 계정이 책 없이 쓴 쪽지 — U-148).
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';

import { library, margins } from '../../api/client';
import type { MarginSummary, MyBookSummary } from '../../api/types';
import { Cover } from '../../components/Cover';
import { confirmRemoveNote, NoteRow } from '../../components/NoteRow';
import { Button, Notice, Screen, Txt, useStacked } from '../../components/ui';
import { booklessSection, noteHref, pendingLeave, savedNotice } from '../../lib/marginSave';
import { bookMeta, shelfSections } from '../../lib/shelf';
import { color, layout, line, space } from '../../theme/tokens';

export default function Library() {
  const [books, setBooks] = useState<MyBookSummary[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [bookless, setBookless] = useState<MarginSummary[]>([]);
  const stacked = useStacked();

  const load = useCallback(async () => {
    // 책 없는 쪽지는 따로 읽는다 — 실패해도 서재가 막히지 않는다(못 읽으면 단을 숨긴다). 방금 떠난 쪽지의 저장은 상한까지 기다린다
    void pendingLeave.wait().then(() => margins.bookless()).then(setBookless, () => setBookless([]));
    try {
      const r = await library.shelf();
      setBooks(r.books);
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  // 책 화면에서 상태를 바꾸고 돌아오면 단이 바뀌어야 한다
  useFocusEffect(
    useCallback(() => {
      savedNotice.take(); // 서재에서 연 쪽지의 「여백을 남겼어요」 — 소비만 한다(알림은 홈 · 책 — F1 §4.4-7). 안 하면 나중에 엉뚱한 화면에 뜬다
      void load();
    }, [load]),
  );

  const removeBookless = (id: number) => setBookless((l) => l.filter((x) => x.id !== id));
  const toAdd = () => router.push('/add');
  const toScan = () => router.push('/add?scan=1');

  if (!books) {
    return (
      <Screen>
        <Txt v="D2">서재</Txt>
        {loadError ? (
          <Notice tone="error" message={loadError} action={{ label: '다시 불러오기', onPress: () => void load() }} />
        ) : (
          <View style={{ flexDirection: 'row', gap: space[2], alignItems: 'center' }}>
            <ActivityIndicator color={color.ink} />
            <Txt v="L1" style={{ color: color.text }}>
              불러오는 중이에요
            </Txt>
          </View>
        )}
      </Screen>
    );
  }

  if (books.length === 0) {
    return (
      <Screen scroll>
        <Txt v="D2">서재</Txt>
        <View style={{ marginTop: space[12] - space[4] }}>
          <Txt v="Q1">아직 담은 책이 없어요</Txt>
          <Txt v="L1" style={{ color: color.text, marginTop: space[2] }}>
            제목으로 찾거나, 책 뒤표지의 바코드를 비춰 담을 수 있어요
          </Txt>
          <View style={{ marginTop: space[6], gap: space[3] }}>
            <Button kind="ink" label="제목으로 찾기" onPress={toAdd} />
            <Button kind="outline" label="바코드로 담기" onPress={toScan} />
          </View>
        </View>
        <Bookless list={bookless} onRemoved={removeBookless} />
      </Screen>
    );
  }

  return (
    <Screen scroll>
      <Txt v="D2">서재</Txt>
      <View style={{ flexDirection: stacked ? 'column' : 'row', gap: stacked ? space[3] : space[4] }}>
        <View style={stacked ? undefined : { flex: 1 }}>
          <Button kind="outline" label="제목으로 찾기" onPress={toAdd} />
        </View>
        <View style={stacked ? undefined : { flex: 1 }}>
          <Button kind="outline" label="바코드로 담기" onPress={toScan} />
        </View>
      </View>
      <View style={{ marginTop: space[4], gap: layout.sectionGap }}>
        {shelfSections(books).map((s) => (
          <View key={s.status} style={{ gap: space[3] }}>
            <View
              accessible
              accessibilityRole="header"
              accessibilityLabel={`${s.title}, ${s.books.length}권`}
              style={{ flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: space[2] }}
            >
              <Txt v="H1">{s.title}</Txt>
              <Txt v="L1" style={{ color: color.muted }}>
                {s.books.length}권
              </Txt>
            </View>
            {s.books.length === 0 ? (
              <Txt v="L1" style={{ color: color.text }}>
                {s.empty}
              </Txt>
            ) : (
              <View>
                {s.books.map((b) => (
                  <ShelfRow key={b.id} book={b} />
                ))}
              </View>
            )}
          </View>
        ))}
      </View>
      <Bookless list={bookless} onRemoved={removeBookless} />
    </Screen>
  );
}

/** 「책 없는 여백」 단 — 쪽지 줄은 책 화면과 같은 모양(길게 누르거나 VoiceOver 「지우기」로 지운다) · 옮긴 문장이 있으면 그 아래 출처 줄(J84). */
function Bookless({ list, onRemoved }: { list: MarginSummary[]; onRemoved: (id: number) => void }) {
  const [error, setError] = useState<string | null>(null);
  useFocusEffect(useCallback(() => setError(null), [])); // 지우기 실패 알림은 탭을 다시 열면 걷는다
  const section = booklessSection(list);
  if (!section) return null;
  return (
    <View style={{ marginTop: layout.sectionGap - space[4], gap: space[3] }}>{/* 화면 틀 사이 16 + 16 = 단 사이 32 */}
      <View
        accessible
        accessibilityRole="header"
        accessibilityLabel={`책 없는 여백, ${section.count}`}
        style={{ flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: space[2] }}
      >
        <Txt v="H1">책 없는 여백</Txt>
        <Txt v="L1" style={{ color: color.muted }}>
          {section.count}
        </Txt>
      </View>
      {error ? <Notice tone="error" message={error} /> : null}
      <View>
        {list.map((m) => (
          <NoteRow
            key={m.id}
            m={m}
            source
            onPress={() => router.push(noteHref(null, m.id))}
            onDelete={() => confirmRemoveNote(m.id, {
                  removed: () => {
                    setError(null);
                    onRemoved(m.id);
                  },
                  failed: setError,
                })}
          />
        ))}
      </View>
    </View>
  );
}

/** 서재 행 — 표지가 있어 Row가 아니다. 행 하나 = 접근성 요소 하나(REQ-122). */
function ShelfRow({ book }: { book: MyBookSummary }) {
  const meta = bookMeta(book);
  const label = [book.title, book.author, meta].filter(Boolean).join(', ');
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint="책 화면을 열어요"
      onPress={() => router.push(`/book/${book.id}`)}
      style={({ pressed }) => [
        {
          flexDirection: 'row',
          gap: space[3],
          paddingVertical: space[3],
          borderBottomWidth: line.thin,
          borderBottomColor: color.line,
        },
        pressed && { backgroundColor: color.pressed },
      ]}
    >
      <Cover title={book.title} coverUrl={book.coverUrl} size="row" />
      <View style={{ flex: 1, gap: 2 }}>
        <Txt v="H3" numberOfLines={2}>
          {book.title}
        </Txt>
        {book.author ? (
          <Txt v="L1" style={{ color: color.text }}>
            {book.author}
          </Txt>
        ) : null}
        {meta ? (
          <Txt v="T2" style={{ color: color.muted }}>
            {meta}
          </Txt>
        ) : null}
      </View>
    </Pressable>
  );
}
