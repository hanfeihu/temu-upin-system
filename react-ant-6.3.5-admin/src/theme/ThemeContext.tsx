import { createContext, useContext } from 'react';
import type { ReactNode } from 'react';
import lightTheme from '@/theme/themes/light';

interface ThemeContextValue {
  themeConfig: typeof lightTheme;
}

const ThemeContext = createContext<ThemeContextValue>({
  themeConfig: lightTheme,
});

export const ThemeProvider = ({ children }: { children: ReactNode }) => {
  return <ThemeContext.Provider value={{ themeConfig: lightTheme }}>{children}</ThemeContext.Provider>;
};

export const useTheme = () => useContext(ThemeContext);
