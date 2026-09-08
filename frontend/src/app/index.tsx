import { Redirect, type Href } from 'expo-router';

import { routes } from '@/constants/routes';

export default function IndexRoute() {
  return <Redirect href={routes.home as Href} />;
}
