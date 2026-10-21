export type Theme = "light" | "dark";

const KEY = "symmetry.theme";

export function getTheme(): Theme {
  const stored = localStorage.getItem(KEY);
  return stored === "dark" ? "dark" : "light";
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  localStorage.setItem(KEY, theme);
}

export function initTheme(): void {
  applyTheme(getTheme());
}
