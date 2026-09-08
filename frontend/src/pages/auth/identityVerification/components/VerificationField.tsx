import type { ReactNode } from 'react';
import { Text, View } from 'react-native';

import { styles } from './VerificationField.styles';

interface VerificationFieldProps {
  label: string;
  children: ReactNode;
}

export function VerificationField({
  label,
  children,
}: VerificationFieldProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.content}>{children}</View>
    </View>
  );
}