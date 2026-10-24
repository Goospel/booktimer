// 책 화면(PR-G 사양 §5.4 · §5.5 · PR-H 사양 §5.7 — 피그마 R7 S-BOOK · S-BOOK-EMPTY · S-BOOK-H).
// 머리 → 상태 세 칸 → [여백 남기기] · [이 책으로 글쓰기] → 이 책의 글 → 여백(결정 D ①). 글 · 여백 행은 길게 눌러 지운다.
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, Image, Pressable, View } from 'react-native';

import { ApiError, failText, library, margins as marginApi, writing } from '../../api/client';
import type { DraftSummary, MarginItem, MaterialsView, ShelfStatus } from '../../api/types';
import { Cover } from '../../components/Cover';
import { Badge, base, Button, Notice, Row, Screen, Txt, useStacked } from '../../components/ui';
import { dateLabel, durationLabel } from '../../lib/format';
import { savedNotice, showsThought } from '../../lib/marginSave';
import { draftTitle } from '../../lib/recentDrafts';
import { SECTIONS } from '../../lib/shelf';
import { color, line, space } from '../../theme/tokens';

type NoticeState = { message: string; tone?: 'info' | 'error' } | null;

export default function Book() {
  const id = Number(useLocalSearchParams<{ id: string }>().id);
  const [data, setData] = useState<MaterialsView | null>(null);
  const [loadError, setLoadError] = useState<{ message: string; notFound: boolean } | null>(null);
  const [notice, setNotice] = useState<NoticeState>(null);
  const [statusBusy, setStatusBusy] = useState(false);
  const [creating, setCreating] = useState(false);
  const stacked = useStacked();

  const load = useCallback(async () => {
    try {
      setData(await library.materials(id));
      setLoadError(null);
    } catch (e) {
      setLoadError({ message: e instanceof Error ? e.message : String(e), notFound: e instanceof ApiError && e.status === 404 });
    }
  }, [id]);

  // 포커스마다 다시 읽는다 — 여백을 남기고 돌아오면 목록과 알림이 바뀐다
  useFocusEffect(
    useCallback(() => {
      if (savedNotice.take()) setNotice({ message: '여백을 남겼어요' });
      void load();
    }, [load]),
  );

  async function changeStatus(next: ShelfStatus) {
    if (!data || statusBusy || next === data.book.status) return;
    setStatusBusy(true);
    try {
      const r = await library.setStatus(id, next);
      setData((d) => (d ? { ...d, book: { ...d.book, status: r.status } } : d));
    } catch (e) {
      setNotice({ message: failText(e, '상태를 바꾸지 못했어요 — 잠시 뒤 다시 해 주세요'), tone: 'error' });
    }
    setStatusBusy(false);
  }

  function confirmRemove(m: MarginItem) {
    Alert.alert('이 여백을 지울까요?', '지우면 되돌릴 수 없어요', [
      { text: '취소', style: 'cancel' },
      { text: '지우기', style: 'destructive', onPress: () => void remove(m.id) },
    ]);
  }

  async function remove(marginId: number) {
    try {
      await marginApi.remove(marginId);
      setData((d) => (d ? { ...d, margins: d.margins.filter((x) => x.id !== marginId) } : d));
    } catch (e) {
      setNotice({ message: failText(e, '여백을 지우지 못했어요 — 잠시 뒤 다시 해 주세요'), tone: 'error' });
    }
  }

  // 누르는 동안 busy — 연타로 글 둘이 생기지 않게(§11-18). 글쓰기 화면이 다시 읽는다(넘겨주지 않는다 · §8-21)
  async function createDraft() {
    if (creating) return;
    setCreating(true);
    try {
      const d = await writing.create(id);
      router.push(`/draft/${d.id}`);
    } catch (e) {
      setNotice({ message: failText(e, '글을 만들지 못했어요 — 잠시 뒤 다시 눌러 주세요'), tone: 'error' });
    }
    setCreating(false);
  }

  function confirmRemoveDraft(d: DraftSummary) {
    Alert.alert('이 글을 지울까요?', '지우면 되돌릴 수 없어요 — 받은 질문도 함께 지워져요', [
      { text: '취소', style: 'cancel' },
      { text: '지우기', style: 'destructive', onPress: () => void removeDraft(d.id) },
    ]);
  }

  async function removeDraft(draftId: number) {
    try {
      await writing.remove(draftId);
      setData((x) => (x ? { ...x, drafts: x.drafts.filter((d) => d.id !== draftId) } : x));
    } catch (e) {
      setNotice({ message: failText(e, '글을 지우지 못했어요 — 잠시 뒤 다시 해 주세요'), tone: 'error' });
    }
  }

  if (!data) {
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

  const { book } = data;
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

      <View style={{ gap: space[3] }}>
        <Button kind="ink" label="여백 남기기" onPress={() => router.push(`/margin/${book.id}`)} />
        <Button kind="outline" label="이 책으로 글쓰기" busy={creating} onPress={() => void createDraft()} />
      </View>

      <View style={{ marginTop: space[4], gap: space[3] }}>
        <View
          accessible
          accessibilityRole="header"
          accessibilityLabel={data.drafts.length > 0 ? `이 책의 글, ${data.drafts.length}편` : '이 책의 글'}
          style={{ flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: space[2] }}
        >
          <Txt v="H1">이 책의 글</Txt>
          {data.drafts.length > 0 ? (
            <Txt v="L1" style={{ color: color.muted }}>
              {data.drafts.length}편
            </Txt>
          ) : null}
        </View>
        {data.drafts.length > 0 ? (
          <View>
            {data.drafts.map((d) => (
              <Row
                key={d.id}
                title={draftTitle(d)}
                meta={`${dateLabel(new Date(d.updatedAt))}${d.exported ? ' · 내보냄' : ''}`}
                accessibilityHint="글쓰기 화면을 열어요"
                onPress={() => router.push(`/draft/${d.id}`)}
                onDelete={() => confirmRemoveDraft(d)}
              />
            ))}
          </View>
        ) : (
          <Txt v="L1" style={{ color: color.text }}>
            이 책으로 쓴 글이 아직 없어요
          </Txt>
        )}
      </View>

      <View style={{ marginTop: space[4], gap: space[3] }}>
        <View
          accessible
          accessibilityRole="header"
          accessibilityLabel={data.margins.length > 0 ? `여백, ${data.margins.length}개` : '여백'}
          style={{ flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: space[2] }}
        >
          <Txt v="H1">여백</Txt>
          {data.margins.length > 0 ? (
            <Txt v="L1" style={{ color: color.muted }}>
              {data.margins.length}개
            </Txt>
          ) : null}
        </View>
        {data.margins.length > 0 ? (
          <View>
            {data.margins.map((m) => (
              <Margin key={m.id} item={m} onRemove={() => confirmRemove(m)} />
            ))}
          </View>
        ) : (
          // 검정 알약은 위 「여백 남기기」 하나 — 빈 묶음 안에 두지 않는다(§8-13)
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
              읽다가 마음에 남은 문장이나 생각을 옮겨 두세요. 여백이 하나 생기면 이 책으로 질문을 받을 수 있어요
            </Txt>
          </View>
        )}
      </View>
    </Screen>
  );
}

/** 여백 하나 = 접근성 요소 하나. 길게 누르거나 VoiceOver 동작 「지우기」로 지운다(§6.5). */
function Margin({ item, onRemove }: { item: MarginItem; onRemove: () => void }) {
  const quote = item.quote?.trim() ? item.quote : null;
  const thought = showsThought(item.text) ? item.text : null; // 문장부호뿐인 생각은 그리지 않는다(v1 REQ-96)
  const date = dateLabel(new Date(item.createdAt));
  const label = [quote && `옮긴 문장: ${quote}`, thought && `내 생각: ${thought}`, date].filter(Boolean).join(', ');
  return (
    <Pressable
      accessibilityLabel={label}
      accessibilityActions={[{ name: 'delete', label: '지우기' }]}
      onAccessibilityAction={(e) => e.nativeEvent.actionName === 'delete' && onRemove()}
      onLongPress={onRemove}
      style={({ pressed }) => [
        { paddingVertical: space[4], gap: space[2], borderBottomWidth: line.thin, borderBottomColor: color.line },
        pressed && { backgroundColor: color.pressed },
      ]}
    >
      {quote ? (
        <View style={{ borderLeftWidth: line.bold, borderLeftColor: color.text, paddingLeft: space[3] }}>
          <Txt v="Q2" style={{ color: color.text }}>
            {quote}
          </Txt>
        </View>
      ) : null}
      {thought ? <Txt v="Q1">{thought}</Txt> : null}
      <Txt v="T2" style={{ color: color.muted }}>
        {date}
      </Txt>
    </Pressable>
  );
}
