import { useEffect, useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
import type { AlertButton } from 'react-native';

import { closeAppDialog, subscribeAppDialog } from './appDialog';
import type { AppDialogRequest } from './appDialog';
import { styles } from './AppDialogHost.styles';

/** 취소 버튼을 왼쪽(맨 앞)에 둔다. 호출부 배열 순서가 달라도 같은 자리에 오게 한다. */
function ordered(buttons: readonly AlertButton[]) {
  return [...buttons].sort(
    (left, right) => Number(right.style === 'cancel') - Number(left.style === 'cancel'),
  );
}

/**
 * 뒤로 가기·바깥 터치로 닫을 때 누른 것으로 칠 버튼. 취소 버튼이 있으면 취소, 버튼이 하나뿐이면 그 버튼(안내창의 "확인")이다.
 * 선택지가 둘 이상인데 취소가 없으면 닫지 않는다 — 사용자가 고르지 않은 동작을 대신 실행하지 않는다.
 */
function dismissButtonOf(buttons: readonly AlertButton[]) {
  return buttons.find((button) => button.style === 'cancel') ?? (buttons.length === 1 ? buttons[0] : undefined);
}

/**
 * `appDialog`로 부른 확인창을 그린다. 루트 레이아웃에 하나만 둔다.
 *
 * 바텀시트(Modal) 위에서 불러도 나중에 열린 Modal이 위에 뜬다. 확인창이 떠 있는 동안 부른 것은 대기열에서 차례를 기다린다.
 */
export function AppDialogHost() {
  const [current, setCurrent] = useState<AppDialogRequest | null>(null);

  useEffect(() => subscribeAppDialog(setCurrent), []);

  if (current === null) return null;

  const buttons = ordered(current.buttons);
  const isRow = buttons.length === 2;
  const dismissButton = dismissButtonOf(buttons);

  const press = (button: AlertButton) => {
    closeAppDialog(current.id);
    button.onPress?.();
  };

  const dismiss = () => {
    if (dismissButton === undefined) return;
    closeAppDialog(current.id);
    dismissButton.onPress?.();
    current.options?.onDismiss?.();
  };

  return (
    <Modal
      animationType="fade"
      key={current.id}
      onRequestClose={dismiss}
      statusBarTranslucent
      transparent
      visible>
      <View style={styles.overlay}>
        <Pressable
          accessibilityLabel="확인창 닫기"
          accessible={dismissButton !== undefined}
          onPress={dismiss}
          style={styles.backdrop}
        />
        <View accessibilityRole="alert" accessibilityViewIsModal style={styles.card}>
          <Text style={styles.title}>{current.title}</Text>
          {current.message ? <Text style={styles.message}>{current.message}</Text> : null}
          <View style={isRow ? styles.buttonRow : styles.buttonColumn}>
            {buttons.map((button, index) => {
              const isCancel = button.style === 'cancel';
              const isDestructive = button.style === 'destructive';
              return (
                <Pressable
                  accessibilityRole="button"
                  key={`${button.text ?? '확인'}-${index}`}
                  onPress={() => press(button)}
                  style={({ pressed }) => [
                    styles.button,
                    isRow && styles.buttonInRow,
                    isCancel
                      ? styles.cancelButton
                      : isDestructive
                        ? styles.destructiveButton
                        : styles.primaryButton,
                    pressed && styles.pressed,
                  ]}>
                  <Text style={[styles.label, isCancel ? styles.cancelLabel : styles.filledLabel]}>
                    {button.text ?? '확인'}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>
      </View>
    </Modal>
  );
}
