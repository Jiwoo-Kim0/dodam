import type { AlertButton, AlertOptions } from 'react-native';

/**
 * 앱 전역 확인창. `Alert.alert`와 같은 인자를 받아 도담 디자인의 다이얼로그로 띄운다.
 *
 * 네이티브 `Alert`는 OS 모양(안드로이드 회색 대화상자)으로 떠 앱 톤과 맞지 않는다. 호출부 로직을 건드리지 않고 디자인만
 * 바꾸려고 인자 모양을 그대로 따른다 — `Alert.alert(...)`를 `appDialog.alert(...)`로 바꾸면 된다.
 *
 * 화면을 그리는 쪽은 루트 레이아웃의 `AppDialogHost` 하나다. 여기는 대기열만 들고 있어 컴포넌트 밖(훅·콜백)에서도 부를 수
 * 있다. 확인창이 떠 있는 동안 또 부르면 앞의 것이 닫힌 뒤 차례로 뜬다(버튼 콜백 안에서 오류 확인창을 띄우는 흐름).
 */
export interface AppDialogRequest {
  id: number;
  title: string;
  message?: string;
  buttons: AlertButton[];
  options?: AlertOptions;
}

type Listener = (current: AppDialogRequest | null) => void;

const DEFAULT_BUTTONS: AlertButton[] = [{ text: '확인' }];

let queue: AppDialogRequest[] = [];
let nextId = 1;
const listeners = new Set<Listener>();

function emit() {
  const current = queue[0] ?? null;
  listeners.forEach((listener) => listener(current));
}

export const appDialog = {
  alert(title: string, message?: string, buttons?: AlertButton[], options?: AlertOptions) {
    queue = [
      ...queue,
      {
        id: nextId++,
        title,
        message,
        buttons: buttons !== undefined && buttons.length > 0 ? buttons : DEFAULT_BUTTONS,
        options,
      },
    ];
    emit();
  },
};

/** 떠 있는 확인창을 구독한다. 호스트 전용이다. */
export function subscribeAppDialog(listener: Listener) {
  listeners.add(listener);
  listener(queue[0] ?? null);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * 떠 있는 확인창을 닫는다. 콜백보다 먼저 대기열에서 빼야, 콜백이 새 확인창을 부를 때 그것이 닫힌 창 뒤가 아니라
 * 다음 차례로 뜬다.
 */
export function closeAppDialog(id: number) {
  if (queue[0]?.id !== id) return;
  queue = queue.slice(1);
  emit();
}
