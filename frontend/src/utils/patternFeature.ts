/**
 * 군집·또래 비교가 쓰는 패턴 특징 값의 공통 표기 규칙(BE 670 회신).
 *
 * <p>발견 상세와 리포트 또래 비교가 같은 특징을 서로 다르게 적으면 한 사용자가 화면을 옮길 때마다
 * 숫자가 달라 보인다. 규칙을 한곳에 두고 두 화면이 같이 쓴다.
 */

/**
 * 퍼센트로 바꾸지 않고 소수 그대로 적는 특징.
 *
 * <p>소비 변동성은 변동계수(표준편차/평균)라 1을 넘을 수 있고 상한이 2.0이다. 100을 곱하면
 * `200%`처럼 읽는 기준이 없는 값이 된다. 서버 설명문도 변동성만 소수로 쓴다.
 */
const RAW_VALUE_FEATURES = new Set(['spendingVolatility']);

/** 변동성처럼 소수로 적는 특징에 붙이는 소수 자리. */
const RAW_VALUE_DIGITS = 2;
/** 범위 양 끝이 같은 값으로 뭉개질 때만 늘려 쓰는 자리(`0.01~0.01` 방지). */
const RAW_VALUE_FALLBACK_DIGITS = 3;

function roundForDisplay(value: number, digits: number) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

export function isRawValueFeature(feature: string) {
  return RAW_VALUE_FEATURES.has(feature);
}

/**
 * 특징 값 한 개를 화면 표기로 바꾼다.
 *
 * <p>비율 특징은 100을 곱해 퍼센트로 적고, 10% 미만이면 소수 한 자리를 남긴다(0.08 → 8%).
 * 소수로 적는 특징은 단위를 붙이지 않는다.
 */
export function formatFeatureValue(feature: string, value: number) {
  if (isRawValueFeature(feature)) return `${roundForDisplay(value, RAW_VALUE_DIGITS)}`;

  const percent = value * 100;
  return `${roundForDisplay(percent, Math.abs(percent) >= 10 ? 0 : 1)}%`;
}

/** `12~31%`처럼 단위를 뒤에 한 번만 붙인다. */
export function formatFeatureRange(feature: string, low: number, high: number) {
  if (isRawValueFeature(feature)) {
    const lowText = roundForDisplay(low, RAW_VALUE_DIGITS);
    const highText = roundForDisplay(high, RAW_VALUE_DIGITS);
    // 변동성은 0.011~0.014처럼 좁을 때가 있어 두 자리로 끊으면 양 끝이 같아진다.
    if (lowText === highText && low !== high) {
      return `${roundForDisplay(low, RAW_VALUE_FALLBACK_DIGITS)}~${roundForDisplay(high, RAW_VALUE_FALLBACK_DIGITS)}`;
    }

    return `${lowText}~${highText}`;
  }

  return `${formatFeatureValue(feature, low).replace('%', '')}~${formatFeatureValue(feature, high)}`;
}
