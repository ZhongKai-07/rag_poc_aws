# UI Design Upgrade Scheme: Cosmic Intelligence

## 1. Design Philosophy
- **Theme**: "Cosmic Intelligence" - A fusion of deep space reliability and AI brilliance.
- **Core Values**: Trust, Speed, Clarity.
- **Visual Style**: Glassmorphism, Gradient Borders, Soft Glows, Clean Typography.

## 2. Color System (CSS Variables)

### Primary Palette
- **Primary**: `hsl(250, 95%, 65%)` (Electric Violet)
- **Primary Gradient**: `linear-gradient(135deg, hsl(250, 95%, 65%), hsl(280, 85%, 60%))`
- **Secondary**: `hsl(190, 90%, 50%)` (Cyan)
- **Accent**: `hsl(320, 80%, 60%)` (Hot Pink - for micro-interactions)

### Neutral Palette
- **Background (Light)**: `hsl(220, 30%, 98%)` (Cool Gray)
- **Background (Dark)**: `hsl(230, 25%, 10%)` (Deep Space Blue)
- **Surface**: `hsl(230, 20%, 15%)` (Darker Blue)

## 3. Typography
- **Font**: Inter (Variable)
- **H1**: Tracking-tight, Gradient Text.
- **Body**: Relaxed line-height (1.6).

## 4. Component Library Changes

### Cards
- **Style**: Glassmorphism (`backdrop-filter: blur(12px)`).
- **Border**: Thin, translucent white (light) or gradient (dark).
- **Hover**: Scale up (1.02) + Shadow Glow.

### Buttons
- **Primary**: Gradient background, rounded-full, shadow-lg.
- **Secondary**: Glass background, border-primary.

### Inputs
- **Style**: Filled (low opacity), bottom border focus animation.

## 5. Micro-interactions
- **Page Transition**: Fade + Slide Up.
- **List Items**: Staggered Fade In.
- **Buttons**: Click ripple (if possible) or scale down.

## 6. Accessibility & Performance
- **Contrast**: Ensure text on gradients meets AA standards.
- **Dark Mode**: Fully supported via CSS variables.
- **Lighthouse**: Target > 90 (Use `content-visibility` and optimized assets).
