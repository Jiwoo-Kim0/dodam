type HomeTabReselectListener = () => void;

const listeners = new Set<HomeTabReselectListener>();

export function emitHomeTabReselect() {
  listeners.forEach((listener) => listener());
}

export function subscribeHomeTabReselect(listener: HomeTabReselectListener) {
  listeners.add(listener);

  return () => {
    listeners.delete(listener);
  };
}
