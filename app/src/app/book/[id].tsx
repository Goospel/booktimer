// 책 화면(PR-G 사양 §5.4 · §5.5 — 책생글 「여백 + 블로그」 A1 · F1 사양 §4.3 · 그림 F-MARGIN-BOOK).
// 머리 → 상태 세 칸 → 검정 알약 「여백에 글쓰기」 하나 → 「여백」 단(쪽지 줄 — 최신 먼저). 옛 [이 책으로 글쓰기] · 「이 책의 글」은 없다(D9).
// 쪽지 줄은 길게 누르거나 VoiceOver 동작 「지우기」로 지운다.
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, Image, Pressable, View } from 'react-native';

import { ApiError, failText, library, margins } from '../../api/client';
import type { BookLine, MarginSummary, ShelfStatus } from '../../api/types';
import { Cover } from '../../components/Cover';
import { NoteRow } from '../../components/NoteRow';
import { Badge, base, Button, Notice, Screen, Txt, useStacked } from '../../components/ui';
import { durationLabel } from '../../lib/format';
import { noteHref, pendingLeave, savedNotice } from '../../lib/marginSave';
import { SECTIONS } from '../../lib/shelf';
import { color, line, space } from '../../theme/tokens';

type NoticeState = { message: string; tone?: 'info' | 'error' } | null;

export default function Book() {
  const id = Number(useLocalSearchParams<{ id: string }>().id);
  const [book, setBook] = useState<BookLine | null>(null);
  const [notes, setNotes] = useState<MarginSummary[]>([]);
  const [loadError, setLoadError] = useState<{ message: string; notFound: boolean } | null>(null);
  const [notice, setNotice] = useState<NoticeState>(null);
  const [statusBusy, setStatusBusy] = useState(false);
  const stacked = useStacked();

  const load = useCallback(async () => {
    try {
      // 방금 떠난 쪽지의 저장(첫 POST · 남은 PUT · 빈 쪽지 지우기)을 상한까지 기다린 뒤 목록을 읽는다 — 「여백을 남겼어요」 아래가 비지 않게
      const [m, list] = await Promise.all([library.materials(id), pendingLeave.wait().then(() => margins.ofBook(id))]);
      setBook(m.book);
      setNotes(list);
      setLoadError(null);
    } catch (e) {
      setLoadError({ message: e instanceof Error ? e.message : String(e), notFound: e instanceof ApiError && e.status === 404 });
    }
  }, [id]);

  // 포커스마다 다시 읽는다 — 쪽지를 쓰고 돌아오면 목록과 알림이 바뀐다
  useFocusEffect(
    useCallback(() => {
      if (savedNotice.take()) setNotice({ message: '여백을 남겼어요' });
      void load();
    }, [load]),
  );

  async function changeStatus(next: ShelfStatus) {
    if (!book || statusBusy || next === book.status) return;
    setStatusBusy(true);
    try {
      const r = await library.setStatus(id, next);
      setBook((b) => (b ? { ...b, status: r.status } : b));
    } catch (e) {
      setNotice({ message: failText(e, '상태를 바꾸지 못했어요 — 잠시 뒤 다시 해 주세요'), tone: 'error' });
    }
    setStatusBusy(false);
  }

  function confirmRemove(m: MarginSummary) {
    Alert.alert('이 여백을 지울까요?', '지우면 되돌릴 수 없어요', [
      { text: '취소', style: 'cancel' },
      { text: '지우기', style: 'destructive', onPress: () => void remove(m.id) },
    ]);
  }

  async function remove(noteId: number) {
    try {
      await margins.delete(noteId);
      setNotes((list) => list.filter((x) => x.id !== noteId));
    } catch (e) {
      setNotice({ message: failText(e, '여백을 지우지 못했어요 — 잠시 뒤 다시 해 주세요'), tone: 'error' });
    }
  }

  if (!book) {
    return (
      <Screen stacked>
        {loadError ? (
          <Notice
            tone="error"
            message={loadError.message}
            action={
              loadError.notFound
                ? { label: '서재로 돌아가기', onPress: () => router.back() }
                : { label: '다시 불러오기', onPress: () => void load() }
            }
          />
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

  return (
    <Screen scroll stacked>
      {notice ? <Notice message={notice.message} tone={notice.tone} /> : null}
      <View style={{ flexDirection: 'row', gap: space[4], alignItems: 'flex-start' }}>
        <Cover title={book.title} coverUrl={book.coverUrl} size="head" />
        <View style={{ flex: 1 }}>
          <Txt v="H2" numberOfLines={3}>
            {book.title}
          </Txt>
          {book.author ? (
            <Txt v="L1" style={{ color: color.text, marginTop: space[1] }}>
              {book.author}
            </Txt>
          ) : null}
          {book.readSeconds >= 60 ? (
            <Txt v="Q2" style={{ color: color.text, marginTop: space[2] }}>
              {durationLabel(book.readSeconds)} 읽었어요
            </Txt>
          ) : null}
          {book.isPublic ? (
            <View style={{ marginTop: space[2] }}>
              <Badge kind="tint" label="공개 책" />
            </View>
          ) : null}
        </View>
      </View>

      <View style={stacked ? undefined : { flexDirection: 'row', borderBottomWidth: line.thin, borderBottomColor: color.line }}>
        {SECTIONS.map((s) => {
          const selected = book.status === s.status;
          return (
            <Pressable
              key={s.status}
              accessibilityRole="button"
              accessibilityLabel={s.title}
              accessibilityHint={selected ? undefined : '책 상태를 바꿔요'}
              accessibilityState={{ selected, disabled: statusBusy }}
              disabled={statusBusy}
              onPress={() => void changeStatus(s.status)}
              style={({ pressed }) => [
                base.segment,
                stacked ? { alignItems: 'flex-start', borderBottomWidth: line.thin, borderBottomColor: color.line } : { flex: 1, marginBottom: -line.thin },
                selected && { borderBottomWidth: line.bold, borderBottomColor: color.ink },
                pressed && { backgroundColor: color.pressed },
              ]}
            >
              <Txt v="L2" style={{ color: selected ? color.ink : color.muted }}>
                {s.title}
              </Txt>
            </Pressable>
          );
        })}
      </View>

      {/* 같은 일(새 쪽지)을 홈과 같은 말로 — 사용자 확정 2026-10-31(확인 질문 1) */}
      <Button kind="ink" label="여백에 글쓰기" onPress={() => router.push(noteHref(book.id))} />

      <View style={{ marginTop: space[4], gap: space[3] }}>
        <View
          accessible
          accessibilityRole="header"
          accessibilityLabel={notes.length > 0 ? `여백, ${notes.length}개` : '여백'}
          style={{ flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: space[2] }}
        >
          <Txt v="H1">여백</Txt>
          {notes.length > 0 ? (
            <Txt v="L1" style={{ color: color.muted }}>
              {notes.length}개
            </Txt>
          ) : null}
        </View>
        {notes.length > 0 ? (
          <View>
            {notes.map((m) => (
              <NoteRow key={m.id} m={m} onPress={() => router.push(noteHref(book.id, m.id))} onDelete={() => confirmRemove(m)} />
            ))}
          </View>
        ) : (
          // 검정 알약은 위 「여백에 글쓰기」 하나 — 빈 묶음 안에 두지 않는다
          <View style={{ marginTop: space[3] }}>
            <Image
              source={require('../../../assets/woodcut-owl.png')}
              style={{ width: 96, height: 96, tintColor: color.ink }}
              resizeMode="contain"
              accessible={false}
            />
            <Txt v="Q1" style={{ marginTop: space[4] }}>
              아직 남긴 여백이 없어요
            </Txt>
            <Txt v="L1" style={{ color: color.text, marginTop: space[2] }}>
              읽다가 마음에 남은 문장이나 생각을 적어 두세요. 쪽지를 쓰다가 AI에게 질문을 받을 수 있어요
            </Txt>
          </View>
        )}
      </View>
    </Screen>
  );
}
