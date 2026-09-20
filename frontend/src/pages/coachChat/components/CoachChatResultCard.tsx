import { Fragment, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { colors } from '@/styles';
import type { CoachChatResponse } from '@/types/coachApi.types';

import { useSimulationDetail } from '../hooks/useSimulationDetail';
import { styles } from './CoachChatResultCard.styles';

/**
 * 화면에 그리지 않는 섹션.
 *
 * <p>`적용 가정(서버)`의 값은 `SimulationAssumptions`의 코드(`REDUCTION_DIRECTED_TO_SAVING_GOALS` 등)라
 * 사용자 말이 아니다(backend/docs/simulation.md §가정). 이 섹션은 모델이 숫자를 지어내지 못하게
 * 붙잡아 두는 근거로 내려오는 것이고, 다른 화면도 `assumptions`를 값으로 표시하지 않는다.
 * 문구를 새로 만들지는 않고 그리지만 않는다 — 한글 문구가 필요하면 서버가 내려 줘야 한다.
 */
const HIDDEN_SECTION_TITLES = new Set(['적용 가정(서버)']);

type CoachChatResultCardProps = {
  response: CoachChatResponse;
};

/**
 * 서버가 계산한 사실을 라벨·값 그대로 그린다. `source`가 `TEMPLATE`이어도 `facts`와
 * `toolResults`는 똑같이 오므로 카드는 항상 그린다(계약 §13-3).
 */
export function CoachChatResultCard({ response }: CoachChatResultCardProps) {
  const [isExpanded, setIsExpanded] = useState(false);
  const { simulation, isLoading, error, load } = useSimulationDetail();
  const { facts, toolResults } = response;
  const sections = (facts?.sections ?? []).filter(
    (section) => !HIDDEN_SECTION_TITLES.has(section.title),
  );
  const failures = toolResults.filter((result) => result.error !== null);
  const simulationId = toolResults.find((result) => result.simulationId !== null)
    ?.simulationId;

  if (sections.length === 0 && failures.length === 0) return null;

  return (
    <View style={styles.card}>
      {sections.map((section, sectionIndex) => (
        <Fragment key={section.title}>
          {sectionIndex > 0 ? <View style={styles.sectionDivider} /> : null}

          <Text style={styles.sectionTitle}>{section.title}</Text>
          <View style={styles.factList}>
            {section.facts.map((fact) => (
              <View key={`${section.title}-${fact.label}`} style={styles.factRow}>
                <Text style={styles.factLabel}>{fact.label}</Text>
                <Text style={styles.factValue}>{fact.value}</Text>
              </View>
            ))}
          </View>
        </Fragment>
      ))}

      {/* 되묻기·검증 실패다. 서버 문장을 그대로 보여 준다. */}
      {failures.length > 0 ? (
        <View style={styles.failureBlock}>
          <Text style={styles.failureTitle}>실행하지 못한 요청</Text>
          {failures.map((failure) => (
            <Text key={failure.tool} style={styles.failureText}>
              {failure.error}
            </Text>
          ))}
        </View>
      ) : null}

      {simulationId ? (
        <>
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ expanded: isExpanded }}
            onPress={() => {
              if (!isExpanded) void load(simulationId);
              setIsExpanded((expanded) => !expanded);
            }}
            style={({ pressed }) => [styles.detailButton, pressed && styles.detailPressed]}>
            <Text style={styles.detailText}>{isExpanded ? '접기' : '자세히 보기'}</Text>
          </Pressable>

          {isExpanded ? (
            <View style={styles.detailBlock}>
              {isLoading ? (
                <ActivityIndicator color={colors.primary.green} />
              ) : error ? (
                <Text style={styles.detailNotice}>{error}</Text>
              ) : simulation ? (
                /*
                 * 목표별 단축·지연은 위 사실 섹션이 목표 이름과 함께 이미 보여 준다.
                 * 여기서는 `goalId`밖에 없어 "목표 4"처럼 쓸 수 없으니 제목과 요약만 둔다.
                 */
                <>
                  <Text style={styles.detailTitle}>{simulation.title}</Text>
                  <Text style={styles.detailSummary}>{simulation.summary}</Text>
                </>
              ) : null}
            </View>
          ) : null}
        </>
      ) : null}
    </View>
  );
}
