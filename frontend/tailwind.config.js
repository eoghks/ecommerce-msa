/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{js,jsx,ts,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#eef2ff',
          100: '#e0e7ff',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
        },
      },
      borderRadius: {
        '2xl': '16px',
        '3xl': '20px',
      },
      boxShadow: {
        card: '0 2px 16px rgba(0,0,0,0.07)',
        'card-brand': '0 4px 32px rgba(79,70,229,0.10), 0 1px 4px rgba(0,0,0,0.06)',
      },
    },
  },
  plugins: [],
}
