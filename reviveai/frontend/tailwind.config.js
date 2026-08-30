/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        paper: '#F6F7F9',
        ink: {
          DEFAULT: '#0B1220',
          muted: '#5B6472',
        },
        line: '#E3E6EB',
        revive: {
          50: '#E7F3EF',
          100: '#CFE8DF',
          600: '#0B6E4F',
          700: '#095A41',
          DEFAULT: '#0B6E4F',
        },
      },
      fontFamily: {
        display: ['"Space Grotesk"', 'sans-serif'],
        sans: ['Inter', 'sans-serif'],
        mono: ['"IBM Plex Mono"', 'monospace'],
      },
      borderRadius: {
        card: '10px',
      },
      boxShadow: {
        card: '0 1px 2px rgba(11,18,32,0.04), 0 1px 0 rgba(11,18,32,0.03)',
      },
    },
  },
  plugins: [],
}
