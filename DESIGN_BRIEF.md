# Minima OS — UI/UX Design Brief

## Product Overview

Minima OS is an AI-first Android launcher that replaces the traditional home screen with an intelligent, conversational interface. No app grid. No widgets. The OS understands you, remembers you, and talks to you first.

**Core philosophy:** The best interface is no interface. The OS should feel like talking to a smart assistant that lives on your phone — not navigating menus.

**Target user:** Professionals who want their phone to work for them, not the other way around.

---

## Design Principles

1. **Calm over busy** — The home screen should feel like a blank canvas that shows only what matters right now. No clutter.
2. **Dark-first** — Deep dark backgrounds. The content floats. Like looking at stars.
3. **Glass & blur** — Frosted glass cards, subtle blur behind overlays. Depth through transparency.
4. **Typography-led** — Big, thin clock. Clean type hierarchy. Let text breathe.
5. **No chrome** — No navigation bars, no headers, no tab bars on the home screen. Everything is content.
6. **Motion with purpose** — Cards slide in from bottom. Proactive cards fade in gently. Nothing bounces or jiggles.
7. **One-thumb reachable** — Command bar at the bottom. Everything important within thumb reach.

---

## Color System

| Token | Purpose | Suggestion |
|-------|---------|------------|
| Surface | Home background | Deep gradient: #0D0D1A → #1A1A2E (or live wallpaper with dark scrim) |
| Card | Task cards, insight chips | White @ 8-12% opacity (glass effect) |
| Card Active | Processing card | White @ 15% opacity |
| Accent | Primary actions, highlights | Purple #7C6FED or similar |
| Success | Completed tasks | Green #34C759 |
| Error | Failed tasks | Red #FF6B6B |
| Warning | Pending approval | Amber #FFB800 |
| Text Primary | Main text on dark | White @ 90% |
| Text Secondary | Subtitles, hints | White @ 50-60% |
| Text Tertiary | Timestamps, meta | White @ 30% |

---

## Typography

| Element | Size | Weight | Notes |
|---------|------|--------|-------|
| Clock | 62sp | Thin (100) | Biggest element on screen. Negative letter-spacing. |
| AM/PM | 18sp | Regular | Beside clock, bottom-aligned, 50% opacity |
| Date | 15sp | Regular | Below clock. "Monday, Apr 6" |
| Greeting chip | 12sp | Medium | Inside pill shape. "Good morning, Ahmed" |
| Insight card title | 12sp | Medium | Inside horizontal scroll chips |
| Insight card subtitle | 10sp | Regular | Below title, 50% opacity |
| Proactive card title | 13sp | SemiBold | Left-aligned in card |
| Proactive card body | 12sp | Regular | 60% opacity, max 3 lines |
| Task input | 14sp | Medium | User's command text |
| Task result | 12-13sp | Regular | AI answer or status. Up to 8 lines for answers. |
| Command bar hint | 14sp | Regular | "What do you need?" at 40% opacity |
| Section labels | 12sp | Regular | "Try saying..." at 35% opacity |

**Font:** System default (Roboto on Android). Consider SF Pro-inspired thin weights for the clock.

---

## Screens & Components

### 1. Home Screen (LauncherScreen)

The main and only screen. Everything is an overlay on top of this.

**Layout (top to bottom):**

```
┌─────────────────────────────┐
│  Status bar (system)         │
│                              │
│  10:25 AM                    │  ← Big clock, thin weight
│  Monday, Apr 6               │  ← Date
│  [☀ Good morning, Ahmed]     │  ← Greeting chip (personalized)
│                              │
│  [☕ Coffee time?] [📅 Mtg]  │  ← Insight cards (horizontal scroll)
│                              │
│  ┌──────────────────────┐    │
│  │ ☀ Good morning, Ahmed│ ✕  │  ← Proactive card (dismissable)
│  │ You have a meeting   │    │
│  │ at 3pm. 14 memories. │    │
│  │ Tap to act           │    │
│  └──────────────────────┘    │
│                              │
│        (empty space)         │  ← Swipe up for app drawer
│                              │
│  ┌──────────────────────┐    │
│  │ ✓ remind me to call  │    │  ← Task card (completed)
│  │   Reminder set       │    │
│  └──────────────────────┘    │
│  ┌──────────────────────┐    │
│  │ ✓ tell me a joke     │    │  ← Task card with long answer
│  │   Why do programmers │    │
│  │   prefer dark mode?  │    │
│  └──────────────────────┘    │
│                              │
│  [⊞ What do you need?   🎤] │  ← Command bar (frosted glass pill)
│           ︿                  │  ← Swipe-up chevron
└─────────────────────────────┘
```

**States:**
- **Empty state (new user):** Show onboarding flow instead of task feed
- **Empty state (returning user):** Show smart suggestions ("Try saying...")
- **Active state:** Task feed shows last 5 tasks, proactive cards show above
- **Processing:** Command bar shows spinner, current task shows purple glow

**Interactions:**
- Tap command bar → keyboard opens, type command
- Tap send (arrow) → submit command
- Tap mic → voice input (future)
- Tap apps grid icon → open app drawer
- Swipe up on empty area → open app drawer
- Tap insight card → run its action command
- Tap proactive card → run its action command
- Tap ✕ on proactive card → dismiss it
- Long-press clock area → open settings
- Double-tap clock area → open memory audit
- Tap suggestion chip → run that command

---

### 2. Command Bar

**Design:** Frosted glass pill, full width with horizontal padding. Sits at the bottom of the screen.

```
┌──────────────────────────────────┐
│ ⊞ │ What do you need?        │🎤│
└──────────────────────────────────┘

When typing:
┌──────────────────────────────────┐
│ ⊞ │ remind me to call mom    │ ↑│
└──────────────────────────────────┘

When processing:
┌──────────────────────────────────┐
│ ⊞ │                          │⟳ │
└──────────────────────────────────┘
```

- Left: Apps grid icon (opens app drawer)
- Center: Text input field
- Right: Mic icon (idle) → Send arrow (when text present) → Spinner (when processing)
- Height: ~56dp
- Corner radius: 24dp
- Background: White @ 10% with backdrop blur
- Border: White @ 5%, 0.5px

---

### 3. Task Card

Shows in the task feed. Each card represents one command and its result.

```
┌──────────────────────────────────┐
│ ● │ tell me a joke               │
│   │ Why do programmers prefer    │
│   │ dark mode? Because light     │
│   │ attracts bugs!               │
└──────────────────────────────────┘
```

**Status indicator (left dot/icon):**
- ✓ Green circle — completed
- ✕ Red circle — failed
- ✦ Purple circle — processing (with subtle pulse animation)
- ◷ Amber circle — awaiting approval

**Content:**
- Line 1: User's input (white, medium weight)
- Line 2+: Result text (white @ 50-80% opacity)
  - For ANSWER/RECALL intents: up to 8 lines, slightly brighter (80% opacity), 13sp
  - For action results: 1-2 lines, 50% opacity, 12sp
  - For errors: red-tinted text

**Card style:**
- Background: White @ 10%
- Corner radius: 16dp
- Padding: 14dp
- No border
- Spacing between cards: 8dp

---

### 4. Insight Cards (Horizontal scroll chips)

Small contextual chips that scroll horizontally below the greeting.

```
[☕ Coffee time?     ] [📅 Meeting at 3pm  ] [🧠 14 memories    ]
 Perfect time for...    Your meeting is...     You've learned...
```

**Design:**
- Corner radius: 14dp
- Background: White @ 8%
- Padding: 14h × 10v dp
- Min width: 140dp, max: 200dp
- Icon: 18dp, colored per type
- Title: 12sp medium, white @ 90%
- Subtitle: 10sp, white @ 50%
- Tappable cards have subtle right-arrow or "Tap" hint

**Icon colors by type:**
- morning: Yellow #FBBF24
- focus: Blue #3B82F6
- food: Amber #F59E0B
- evening: Orange #F97316
- night: Indigo #818CF8
- heart: Pink #EC4899
- pattern: Cyan #06B6D4
- brain: Purple #7C6FED
- calendar: Green #10B981
- person: Violet #8B5CF6

---

### 5. Proactive Cards

Larger cards that appear between the insight chips and the task feed. The OS talking first.

```
┌──────────────────────────────────┐
│ ☀ │ Good morning, Ahmed        ✕ │
│   │ It's Monday in Dubai. You    │
│   │ have a meeting at 3pm.       │
│   │ Tap to act                   │
└──────────────────────────────────┘
```

**Design:**
- Same glass card style as task cards
- Left: 32dp circle icon with colored background @ 15% opacity
- Title: 13sp semibold
- Body: 12sp, white @ 60%, max 3 lines
- "Tap to act": 10sp, colored per type, shown only if actionable
- Dismiss ✕: top-right, 12dp, white @ 30%
- Animate in: slide up + fade in
- Animate out on dismiss: slide down + fade out

---

### 6. App Drawer

Full-screen overlay triggered by swipe-up or apps button.

```
┌─────────────────────────────────┐
│ (dark scrim, tap to dismiss)     │
│                                  │
│  ┌────────────────────────────┐  │
│  │         ━━━                │  │  ← Handle bar
│  │    Apps                 ✕  │  │
│  │                            │  │
│  │  [P]  [S]  [T]  [C]       │  │  ← 4-column grid
│  │ Play  Set  TMo  Cam        │  │
│  │ Store tings bile era        │  │
│  │                            │  │
│  │  [G]  [M]  [Y]  [W]       │  │
│  │ Gmail Maps  YT  WhApp      │  │
│  │                            │  │
│  └────────────────────────────┘  │
└─────────────────────────────────┘
```

**Design:**
- Scrim: Black @ 40%, tap to dismiss
- Panel: Rounded top corners (28dp), surface color
- Height: 60% of screen
- Handle bar: 36w × 4h dp, centered, rounded
- Grid: 4 columns
- App icon: Colored letter in rounded square (hash-based color per app)
  - **DESIGNER NOTE:** Replace letter icons with actual app icons using PackageManager
- App label: 11sp, below icon
- Tap app → runs "open [appname]" command, drawer closes

---

### 7. Approval Sheet

Bottom sheet that appears when a task requires user confirmation (CONFIRM level).

```
┌─────────────────────────────────┐
│ (scrim)                          │
│                                  │
│  ┌────────────────────────────┐  │
│  │         ━━━                │  │
│  │    🛡 Confirm action       │  │
│  │                            │  │
│  │    Send message to Sarah   │  │
│  │    "happy birthday"        │  │
│  │                            │  │
│  │    Capability: messaging   │  │
│  │    Action: send_message    │  │
│  │                            │  │
│  │  [ Deny ]     [ Approve ]  │  │
│  │  outlined     filled       │  │
│  │  red          purple       │  │
│  └────────────────────────────┘  │
└─────────────────────────────────┘
```

**Design:**
- Same bottom sheet pattern as app drawer
- Shield icon: centered, purple
- Title: "Confirm action", 18sp semibold
- Description: what will happen, 14sp
- Capability + action: smaller text, 12sp, tertiary color
- Deny button: outlined, red text, rounded
- Approve button: filled purple, white text, rounded
- Both buttons: same width, 48dp height, 14dp corner radius

---

### 8. Settings Sheet

Bottom sheet opened by long-pressing the clock area.

```
┌────────────────────────────────┐
│         ━━━                     │
│    Settings                  ✕  │
│                                 │
│    AI Model                     │
│    OpenAI GPT-4o                │
│                                 │
│    OpenAI API Key               │
│    ┌──────────────────────┐     │
│    │ sk-...               │     │
│    └──────────────────────┘     │
│    Required for AI features.    │
│                                 │
│    Proactive Mode               │
│    [Quiet] [Normal] [Proactive] │
│    Balanced. Smart suggestions. │
│                                 │
│    [ Save ]                     │
└────────────────────────────────┘
```

**Design:**
- Same bottom sheet pattern
- Section labels: 14sp medium
- Sublabels: 12sp, 60% opacity
- Input field: rounded rect, surface variant bg, 14sp
- Sensitivity selector: 3 equal-width pills, selected = accent bg @ 20%
- Description below pills changes per selection
- Save button: full width, filled accent, 48dp height

---

### 9. Memory Audit Screen

Full-height bottom sheet opened by double-tapping the clock.

```
┌────────────────────────────────┐
│         ━━━                     │
│    Memory                    ✕  │
│    14 memories | 2 people       │
│                                 │
│    [STM: 5] [MTM: 3] [LTM: 6]  │  ← Colored tier badges
│                                 │
│    [All] [People] [Places] [Pat]│  ← Tab row
│                                 │
│    ┌────────────────────────┐   │
│    │ 🟢 user.name           │   │
│    │ LTM  ahmed         🗑  │   │
│    └────────────────────────┘   │
│    ┌────────────────────────┐   │
│    │ 💜 user.location       │   │
│    │ LTM  dubai         🗑  │   │
│    └────────────────────────┘   │
│    ┌────────────────────────┐   │
│    │ 🟡 search.recent       │   │
│    │ STM  best restaurants 🗑│   │
│    └────────────────────────┘   │
└────────────────────────────────┘
```

**Design:**
- Height: 85% of screen
- Tier badges: colored pills (STM=amber, MTM=blue, LTM=green)
- Tab row: 4 tabs, underline indicator in accent color
- Memory cards: icon (colored by tier) + key + value + tier badge + delete icon
- Delete icon: trash, red @ 50%, 14dp
- Empty state: large icon + "No memories yet" + subtitle
- People tab: name, relationship, interaction count
- Places tab: name, type, visit count
- Patterns tab: description, frequency, confidence %

---

### 10. Onboarding Flow

Shown to new users (< 3 memories) instead of the suggestion list.

```
┌────────────────────────────────┐
│           ✦                     │
│    Let me get to know you       │
│    Answer a few questions...    │
│                                 │
│         ● ○ ○ ○                 │  ← Progress dots
│                                 │
│    What's your name?            │
│    ┌──────────────────────┐     │
│    │ Type your answer...  │     │
│    └──────────────────────┘     │
│                                 │
│    [ Skip ]      [ Next ]       │
└────────────────────────────────┘
```

**Design:**
- Glass card style, rounded 20dp
- Star/sparkle icon at top, accent color
- 4 progress dots, filled = accent, unfilled = white @ 20%
- Question: 18sp medium, white
- Input: glass bg, 14sp
- Skip: text button, 50% opacity
- Next/Done: filled accent button
- 4 questions: Name → Location → Role → Likes

---

## Animations

| Element | Animation | Duration |
|---------|-----------|----------|
| App drawer | Slide up from bottom + fade in | 300ms |
| Approval sheet | Slide up from bottom + fade in | 300ms |
| Settings sheet | Slide up from bottom + fade in | 300ms |
| Memory screen | Slide up from bottom + fade in | 300ms |
| Proactive card appear | Slide down + fade in | 400ms, staggered 100ms |
| Proactive card dismiss | Slide up + fade out | 250ms |
| Task card appear | Fade in | 200ms |
| Processing spinner | Continuous rotation | Infinite |
| Status dot (processing) | Subtle pulse (scale 1.0 → 1.1) | 1000ms, infinite |
| Command bar focus | Slight elevation change | 150ms |
| Insight cards scroll | Horizontal fling with snap | Physics-based |

---

## Responsive Notes

- Design for standard Android phone: 1080 × 2400 (412 × 892 dp)
- Command bar and task feed must be reachable with one thumb
- Clock area is top 30% of screen — display only, not interactive (except long/double tap)
- Middle area is elastic — fills remaining space between content and command bar
- When keyboard is open, task feed and proactive cards should scroll above keyboard

---

## Deliverables Needed

1. **Home screen** — all states (empty/new user, with tasks, with proactive cards, processing)
2. **Command bar** — idle, typing, processing states
3. **Task cards** — success, failure, processing, approval pending, long answer (8 lines)
4. **Insight cards** — various types with icons
5. **Proactive cards** — morning brief, nudge, people reminder, time-aware
6. **App drawer** — with real app icons
7. **Approval sheet** — confirm/deny
8. **Settings sheet** — with sensitivity toggle
9. **Memory audit** — all 4 tabs with data
10. **Onboarding flow** — 4-step progressive
11. **Icon set** — for all insight/proactive card types
12. **App icon** — Minima OS launcher icon
13. **Color palette & typography spec** — tokens for implementation

**Format:** Figma file with components, auto-layout, and design tokens.
