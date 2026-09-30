/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        surface: '#f7f8fa',
        border:  '#e5e7eb',
        muted:   '#57606a',
        accent:  '#3b82d4',
      },
    },
  },
  plugins: [],
}
