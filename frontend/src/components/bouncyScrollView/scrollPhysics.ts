export const RUBBER_BAND_RESISTANCE = 0.4;

export const SPRING_BACK_CONFIG = {
  damping: 22,
  stiffness: 100,
  overshootClamping: true,
};

export const DECAY_DECELERATION = 0.997;

export function withRubberBand(
  value: number,
  min: number,
  max: number,
  resistance = RUBBER_BAND_RESISTANCE,
) {
  'worklet';

  if (value > max) {
    return max + (value - max) * resistance;
  }

  if (value < min) {
    return min + (value - min) * resistance;
  }

  return value;
}
