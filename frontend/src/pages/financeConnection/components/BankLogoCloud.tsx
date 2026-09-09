import { Image, View } from 'react-native';

import { FINANCIAL_INSTITUTIONS } from '@/mocks/financeConnection';

import { styles } from './BankLogoCloud.styles';

const TOP_ROW = FINANCIAL_INSTITUTIONS.slice(0, 4);
const MIDDLE_LEFT = FINANCIAL_INSTITUTIONS.slice(4, 6);
const MIDDLE_RIGHT = FINANCIAL_INSTITUTIONS.slice(6, 8);
const BOTTOM_ROW = FINANCIAL_INSTITUTIONS.slice(8, 12);

function BankLogo({ bankIndex }: { bankIndex: number }) {
  const bank = FINANCIAL_INSTITUTIONS[bankIndex];

  return (
    <View style={styles.logoContainer}>
      <Image source={bank.logo} accessibilityLabel={bank.name} style={styles.logo} />
    </View>
  );
}

export function BankLogoCloud() {
  return (
    <View style={styles.container}>
      <View style={styles.row}>
        {TOP_ROW.map((bank) => (
          <BankLogo
            key={bank.id}
            bankIndex={FINANCIAL_INSTITUTIONS.indexOf(bank)}
          />
        ))}
      </View>

      <View style={styles.middleRow}>
        <View style={styles.sideGroup}>
          {MIDDLE_LEFT.map((bank) => (
            <BankLogo
              key={bank.id}
              bankIndex={FINANCIAL_INSTITUTIONS.indexOf(bank)}
            />
          ))}
        </View>

        <View importantForAccessibility="no-hide-descendants" style={styles.characterSpace} />

        <View style={styles.sideGroup}>
          {MIDDLE_RIGHT.map((bank) => (
            <BankLogo
              key={bank.id}
              bankIndex={FINANCIAL_INSTITUTIONS.indexOf(bank)}
            />
          ))}
        </View>
      </View>

      <View style={styles.row}>
        {BOTTOM_ROW.map((bank) => (
          <BankLogo
            key={bank.id}
            bankIndex={FINANCIAL_INSTITUTIONS.indexOf(bank)}
          />
        ))}
      </View>
    </View>
  );
}
