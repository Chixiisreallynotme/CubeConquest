---
name: "awesome-design-md"
description: "Apply a DESIGN.md design system to generate consistent UI. Use when creating UI components, styling a frontend, applying a visual theme, or when the user asks to 'use the design of X', 'make it look like X', or 'apply X's design system'. Supports 73 brand design systems (Claude, Linear, Vercel, Notion, Spotify, Apple, Shopify, etc.) from https://github.com/voltagent/awesome-design-md"
---

# awesome-design-md

## What This Skill Does

Fetches a DESIGN.md from the awesome-design-md collection and applies it to generate UI that matches a well-known brand's visual identity. Each DESIGN.md contains colors, typography, components, spacing, and AI prompts for consistent UI generation.

## Quick Start

1. User asks to apply a brand's design system (e.g. "use Linear's design", "make it look like Notion")
2. Fetch the relevant DESIGN.md from GitHub
3. Apply the design tokens and component styles to the UI being built

```
Base URL: https://raw.githubusercontent.com/voltagent/awesome-design-md/main/designs/[brand].md
```

## Available Brands

### AI / LLM Platforms
`claude`, `openai`, `mistral`, `perplexity`, `cursor`, `github-copilot`

### Developer Tools
`vercel`, `raycast`, `supabase`, `mongodb`, `railway`, `fly-io`

### Productivity / SaaS
`linear`, `notion`, `figma`, `loom`, `retool`, `cal-com`

### E-commerce / Consumer
`shopify`, `airbnb`, `spotify`, `apple`, `tesla`

### Many more — browse the full list at:
https://github.com/voltagent/awesome-design-md

## Step-by-Step Guide

### Step 1: Identify the brand
Match the user's request to a brand slug (e.g. "Linear style" → `linear`).

### Step 2: Fetch the DESIGN.md
```
WebFetch: https://raw.githubusercontent.com/voltagent/awesome-design-md/main/designs/[brand].md
Prompt: Extract the full design system — colors, typography, components, spacing, shadows.
```

If the exact URL fails, check the repo index:
```
WebFetch: https://raw.githubusercontent.com/voltagent/awesome-design-md/main/README.md
```

### Step 3: Apply the design system
Use the extracted tokens to:
- Set CSS variables or Tailwind config values
- Style components with the brand's color palette and typography
- Follow spacing and grid principles
- Use the ready-made AI prompts if provided in the DESIGN.md

### Step 4: Copy to project (optional)
If the user wants to keep the DESIGN.md in their project:
```bash
curl -o DESIGN.md https://raw.githubusercontent.com/voltagent/awesome-design-md/main/designs/[brand].md
```

## What Each DESIGN.md Contains
- **Visual theme** — tone, atmosphere, brand personality
- **Color palette** — primary, secondary, semantic roles (success/error/warning)
- **Typography** — font families, sizes, weights, hierarchy
- **Components** — buttons, cards, inputs, modals styled for the brand
- **Spacing & grid** — consistent spacing scale and layout system
- **Shadows** — elevation system
- **Responsive** — breakpoints and mobile-first guidelines
- **AI prompts** — ready-to-use prompts for generating more UI in this style

## Tips
- If a brand slug is unknown, fetch the README to browse the full list
- DESIGN.md files are plain markdown — paste directly into context or save to project root
- Combine multiple DESIGN.md files for hybrid styles (e.g. Linear layout + Claude colors)
