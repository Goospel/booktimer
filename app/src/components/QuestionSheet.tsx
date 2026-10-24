// 질문 시트(PR-H 사양 §5.5 — 피그마 R7 S-QSHEET). 반쯤 열림 → 끌어 올리면 끝까지.
// 마지막 라운드만 펼치고 지난 라운드는 「지난 질문 M개」 하나로 접는다. 질문을 누르면 띠에 오른다 — 본문에는 들어가지 않는다(REQ-47).
// 넣기 · 복사 단추는 없고 질문 글자는 고를 수 없다(RN Text 기본 — 소스 가드).
import { useState } from 'react';
import { Pressable, View } from 'react-native';

import type { Question, QuestionLog, Round } from '../api/types';
import { ROUNDS_MAX } from '../lib/aiStep';
import { isPicked, roundTitle, sheetRounds } from '../lib/questions';
import { color, layout, line, space } from '../theme/tokens';
import { Sheet } from './Sheet';
import { AiBadge, Badge, Button, Txt } from './ui';

const LAST_ROUND = '이 글에서 받을 수 있는 질문을 모두 받았어요 — 이제 내 글로 이어 가 보세요';

export function QuestionSheet(p: {
  open: boolean;
  onClose: () => void;
  log: QuestionLog;
  focus: string | null;
  remainingToday: number;
  materials: { included: number; excluded: number } | null;
  busy: boolean;
  onPick: (q: Question) => void;
  onAgain: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const { latest, past } = sheetRounds(p.log);
  const rounds = p.log.rounds.length;
  const pastCount = past.reduce((n, r) => n + r.questions.length, 0);
  const round = (r: Round) => (
    <View key={r.at} style={{ gap: space[1] }}>
      <Txt v="L2" style={{ color: color.text }}>
        {roundTitle(r.step)}
      </Txt>
      <View>
        {r.questions.map((q, i) => (
          <Card key={i} q={q} picked={isPicked(q, p.focus)} onPress={() => p.onPick(q)} />
        ))}
      </View>
    </View>
  );
  return (
    <Sheet open={p.open} onClose={p.onClose} snap={['half', 'full']}>
      <View style={{ gap: space[6] }}>
        <View style={{ gap: space[1] }}>
          <Txt v="H2">{`이 글에서 받은 질문 ${rounds}/${ROUNDS_MAX}`}</Txt>
          <Txt v="T2" style={{ color: color.muted }}>
            {`오늘 남은 질문 ${p.remainingToday}번`}
          </Txt>
          {p.materials && p.materials.excluded > 0 ? (
            <Txt v="T2" style={{ color: color.muted }}>
              {`재료 ${p.materials.included + p.materials.excluded}개 중 ${p.materials.included}개를 보냈어요(오래된 여백부터 뺐어요)`}
            </Txt>
          ) : null}
          {rounds >= ROUNDS_MAX ? (
            <Txt v="L1" style={{ color: color.text }}>
              {LAST_ROUND}
            </Txt>
          ) : null}
        </View>
        {latest ? round(latest) : null}
        <View style={{ gap: space[4] }}>
          {latest && rounds < ROUNDS_MAX ? <Button kind="outline" label="질문 다시 받기" busy={p.busy} onPress={p.onAgain} /> : null}
          {past.length > 0 ? (
            <View>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`지난 질문 ${pastCount}개`}
                accessibilityState={{ expanded }}
                onPress={() => setExpanded((v) => !v)}
                style={({ pressed }) => [
                  {
                    minHeight: layout.hit,
                    flexDirection: 'row',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    borderTopWidth: line.thin,
                    borderBottomWidth: line.thin,
                    borderColor: color.line,
                  },
                  pressed && { backgroundColor: color.pressed },
                ]}
              >
                <Txt v="L2">{`지난 질문 ${pastCount}개`}</Txt>
                <Txt v="L2" style={{ color: color.muted }}>
                  {expanded ? '접기' : '펼치기'}
                </Txt>
              </Pressable>
              {expanded ? <View style={{ gap: space[6], marginTop: space[6] }}>{past.map(round)}</View> : null}
            </View>
          ) : null}
        </View>
      </View>
    </Sheet>
  );
}

/** 질문 카드 — 「AI 질문」 배지 + 질문(명조) + AI가 짚은 내 문장(인용 모양 — 서버가 원문 그대로인 것만 남긴다). */
function Card({ q, picked, onPress }: { q: Question; picked: boolean; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={q.anchor ? `AI 질문: ${q.text}, 짚은 곳: ${q.anchor}` : `AI 질문: ${q.text}`}
      accessibilityHint="이 질문을 띠에 올려요"
      accessibilityState={{ selected: picked }}
      onPress={onPress}
      style={({ pressed }) => [
        { paddingVertical: space[4], gap: space[2], borderBottomWidth: line.thin, borderBottomColor: color.line },
        pressed && { backgroundColor: color.pressed },
      ]}
    >
      <View style={{ flexDirection: 'row', gap: space[2] }}>
        <AiBadge />
        {picked ? <Badge kind="tint" label="지금 답하는 질문" /> : null}
      </View>
      <Txt v="Q1">{q.text}</Txt>
      {q.anchor ? (
        <View style={{ borderLeftWidth: line.bold, borderLeftColor: color.text, paddingLeft: space[3] }}>
          <Txt v="Q2" style={{ color: color.text }}>
            {q.anchor}
          </Txt>
        </View>
      ) : null}
    </Pressable>
  );
}
