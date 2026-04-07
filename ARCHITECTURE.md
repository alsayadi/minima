# Minima OS — Architecture Diagram

## System Overview

```
+===========================================================================+
|                           MINIMA OS v1+v2                                  |
|                     AI-First Android Launcher                              |
+===========================================================================+

                          USER INPUT
                              |
                    [Voice / Text / Tap]
                              |
                              v
+===========================================================================+
|                          UI LAYER (ui module)                              |
|---------------------------------------------------------------------------|
|                                                                           |
|  +------------------+  +----------------+  +-----------------+            |
|  | LauncherScreen   |  | ContextSurface |  | CommandBar      |            |
|  | - Main scaffold  |  | - Clock        |  | - Text input    |            |
|  | - Overlays       |  | - Greeting     |  | - Send button   |            |
|  | - Gestures       |  | - Insight cards|  | - Apps button   |            |
|  +------------------+  | - AI-generated |  | - Mic button    |            |
|                         +----------------+  +-----------------+            |
|                                                                           |
|  +------------------+  +----------------+  +-----------------+            |
|  | TaskCard         |  | ProactiveCards |  | AppDrawer       |            |
|  | - Status icon    |  | - Morning brief|  | - Grid layout   |            |
|  | - Input text     |  | - AI nudges    |  | - Fuzzy search  |            |
|  | - Result/answer  |  | - Dismissable  |  | - Letter icons  |            |
|  | - 8-line answers |  | - Tappable     |  +-----------------+            |
|  +------------------+  +----------------+                                  |
|                                                                           |
|  +------------------+  +----------------+  +-----------------+            |
|  | ApprovalSheet    |  | SettingsSheet  |  | MemoryScreen    |            |
|  | - Confirm/Deny   |  | - API key      |  | - All memories  |            |
|  | - Action details |  | - Sensitivity  |  | - People/Places |            |
|  +------------------+  | - Q/N/P modes  |  | - Patterns      |            |
|                         +----------------+  | - Tier badges   |            |
|  +------------------+                       | - Delete/Edit   |            |
|  | OnboardingFlow   |                       +-----------------+            |
|  | - 4 seed Q's     |                                                     |
|  | - Progressive    |                                                     |
|  +------------------+                                                     |
|                                                                           |
|  LauncherViewModel --- collects all flows, dispatches commands             |
+===========================================================================+
                              |
                              v
+===========================================================================+
|                        AGENT LAYER (agent module)                          |
|---------------------------------------------------------------------------|
|                                                                           |
|  +--------------------------------------------------------------------+  |
|  |                      TaskExecutor (orchestrator)                     |  |
|  |                                                                     |  |
|  |  Input ──> [1. Memory Context] ──> [2. Classify] ──> [3. Plan]     |  |
|  |            Load relevant         AI first (GPT-4o)   Map intent     |  |
|  |            memories, people,     Deterministic        to action     |  |
|  |            patterns for LLM      fallback if offline  steps         |  |
|  |                                  ANSWER fallback                    |  |
|  |                                  if still UNKNOWN                   |  |
|  |                                                                     |  |
|  |  ──> [4. Approve] ──> [5. Execute] ──> [6. Extract] ──> [7. Log]  |  |
|  |      AUTO/NOTIFY/     Run capability   Learn memories   Append-only |  |
|  |      CONFIRM/BLOCK    provider          from result     action log  |  |
|  +--------------------------------------------------------------------+  |
|                                                                           |
|  +-------------------+  +---------------------+                          |
|  | DeterministicClass|  | DeterministicPlanner |                          |
|  | - Keyword/regex   |  | - Intent -> Steps   |                          |
|  | - 16 intent types |  | - Approval levels   |                          |
|  | - Offline/instant |  | - Multi-step chains |                          |
|  +-------------------+  +---------------------+                          |
|                                                                           |
|  +-------------------+  +---------------------+                          |
|  | ApprovalEngine    |  | ActionLogger        |                          |
|  | - 4 levels        |  | - Append-only       |                          |
|  | - User decision   |  | - Immutable audit   |                          |
|  | - SharedFlow      |  | - JSON serialized   |                          |
|  +-------------------+  +---------------------+                          |
+===========================================================================+
                              |
                              v
+===========================================================================+
|                    CAPABILITY LAYER (capability module)                     |
|---------------------------------------------------------------------------|
|                                                                           |
|  CapabilityRegistry ──> routes to provider by capabilityId                |
|                                                                           |
|  +---------------+  +---------------+  +---------------+                 |
|  | calendar      |  | notification  |  | messaging     |                 |
|  | - create_event|  | - triage      |  | - draft_msg   |                 |
|  | - read_events |  | - dismiss     |  | - send_msg    |                 |
|  | - set_reminder|  | - send_reply  |  | [CONFIRM]     |                 |
|  | [ContentProv] |  | [Listener]    |  | [SMS Intent]  |                 |
|  +---------------+  +---------------+  +---------------+                 |
|                                                                           |
|  +---------------+  +---------------+  +---------------+                 |
|  | commerce      |  | system        |  | memory        |                 |
|  | - open_ride   |  | - open_app    |  | - remember    |                 |
|  | - open_food   |  | - list_apps   |  | - recall      |                 |
|  | [Deep links]  |  | - search      |  | [MemoryMgr]   |                 |
|  |               |  | - settings    |  |               |                 |
|  +---------------+  | - fuzzy match |  +---------------+                 |
|                      | [edit dist]   |                                    |
|                      +---------------+  +---------------+                 |
|                                         | chat          |                 |
|                                         | - answer      |                 |
|                                         | [GPT-4o +     |                 |
|                                         |  memory ctx]  |                 |
|                                         +---------------+                 |
+===========================================================================+
                              |
                              v
+===========================================================================+
|                      MODEL LAYER (model module)                            |
|---------------------------------------------------------------------------|
|                                                                           |
|  +--------------------------------------------------------------------+  |
|  |                        ModelRouter                                  |  |
|  |  Cache (30min TTL) ──> CloudProvider ──> Response                   |  |
|  +--------------------------------------------------------------------+  |
|                                                                           |
|  +----------------------------+  +---------------------------+           |
|  | CloudModelProvider (GPT-4o)|  | ResponseCache             |           |
|  | - classify (system prompt  |  | - LRU (100 classify,     |           |
|  |   + memory in system,      |  |   50 draft)              |           |
|  |   user request separate)   |  | - 30min TTL              |           |
|  | - draft (text answers)     |  | - Normalized keys        |           |
|  | - plan (task planning)     |  +---------------------------+           |
|  | - OpenAI Chat Completions  |                                          |
|  +----------------------------+                                          |
|                                                                           |
|  +----------------------------+                                          |
|  | ModelProvider interface    |  <-- Ready for Gemma/local model         |
|  | - classify / draft / plan  |                                          |
|  | - isAvailable / isLocal    |                                          |
|  +----------------------------+                                          |
+===========================================================================+
                              |
                              v
+===========================================================================+
|                       DATA LAYER (data module)                             |
|---------------------------------------------------------------------------|
|                                                                           |
|  MinimaDatabase (Room, v2, SQLite)                                        |
|  +------------------+  +--------------------+  +-------------------+     |
|  | tasks            |  | action_records     |  | memories          |     |
|  | - id, input      |  | - append-only      |  | - key/value       |     |
|  | - state, intent  |  | - no UPDATE/DELETE |  | - category        |     |
|  | - steps (JSON)   |  | - outcome, timing  |  | - tier (STM/MTM/  |     |
|  | - error, times   |  |                    |  |   LTM)            |     |
|  +------------------+  +--------------------+  | - confidence      |     |
|                                                  | - access count   |     |
|  +------------------+  +--------------------+  | - expiry          |     |
|  | people           |  | places             |  +-------------------+     |
|  | - name           |  | - name             |                            |
|  | - relationship   |  | - type             |  +-------------------+     |
|  | - phone/email    |  | - address          |  | patterns          |     |
|  | - interactions   |  | - visit count      |  | - type, desc      |     |
|  +------------------+  +--------------------+  | - frequency       |     |
|                                                  | - confidence      |     |
|                                                  +-------------------+     |
|                                                                           |
|  +--------------------------------------------------------------------+  |
|  |                     MemoryManager                                   |  |
|  |  remember() ──> STM (48h) ──> MTM (30d) ──> LTM (permanent)       |  |
|  |  Auto-promotion: 3+ accesses = MTM, 7+ = LTM                      |  |
|  |  Explicit "my name is" / "I like" = direct LTM                     |  |
|  |  recall() / getContextForAgent() / getContextString()              |  |
|  |  maintain() ── expire STM, promote based on access count           |  |
|  +--------------------------------------------------------------------+  |
|                                                                           |
|  +--------------------------------------------------------------------+  |
|  |                    MemoryExtractor                                  |  |
|  |  Runs after every completed task. Extracts:                         |  |
|  |  - People from messages/events                                      |  |
|  |  - Places from rides/navigation                                     |  |
|  |  - Preferences from food/apps/settings                              |  |
|  |  - Facts from explicit "remember X"                                 |  |
|  |  - Patterns from time-of-day usage                                  |  |
|  +--------------------------------------------------------------------+  |
|                                                                           |
|  +--------------------------------------------------------------------+  |
|  |                    ContextEngine (AI-driven)                        |  |
|  |  Generates home screen content:                                     |  |
|  |  - Personalized greeting from user.name                             |  |
|  |  - Insight cards (GPT-4o generates from memory + time)              |  |
|  |  - Smart suggestions (contextual commands)                          |  |
|  |  - Fallback to rule-based when AI unavailable                       |  |
|  +--------------------------------------------------------------------+  |
|                                                                           |
|  +--------------------------------------------------------------------+  |
|  |                   ProactiveEngine (AI-driven)                       |  |
|  |  OS talks first — generates unprompted cards:                       |  |
|  |  - Morning briefs, transition summaries                             |  |
|  |  - Pattern nudges, people reminders                                 |  |
|  |  - Memory milestones, time-aware alerts                             |  |
|  |  - Learns from dismissals (avoids dismissed types)                  |  |
|  |  - 3 sensitivity modes: Quiet / Normal / Proactive                  |  |
|  |  - Fallback to pattern-based when AI unavailable                    |  |
|  +--------------------------------------------------------------------+  |
+===========================================================================+
                              |
                              v
+===========================================================================+
|                       CORE LAYER (core module)                             |
|---------------------------------------------------------------------------|
|  Pure Kotlin domain models — no Android dependencies                      |
|                                                                           |
|  Task, TaskState, ClassifiedIntent, IntentType (16 types), Confidence     |
|  ActionStep, StepStatus, StepResult, ApprovalLevel, ActionRecord          |
|  CapabilityExecutor interface, NotificationInfo, Capability               |
+===========================================================================+


## Intent Types (16)

  CREATE_EVENT ──── Calendar
  READ_CALENDAR ── Calendar
  SET_REMINDER ─── Calendar
  SEND_MESSAGE ─── Messaging (CONFIRM approval)
  READ_MESSAGES ── Messaging
  TRIAGE_NOTIF ─── Notification
  DISMISS_NOTIF ── Notification
  REPLY_NOTIF ──── Notification (CONFIRM approval)
  ORDER_RIDE ───── Commerce (CONFIRM approval)
  ORDER_FOOD ───── Commerce (CONFIRM approval)
  SEARCH ────────── System (web search)
  OPEN_APP ──────── System (fuzzy match + list apps)
  DEVICE_SETTING ── System (wifi/bt/dnd/brightness/volume)
  REMEMBER ──────── Memory (explicit facts → LTM)
  RECALL ─────────── Memory (query what OS knows)
  ANSWER ─────────── Chat (GPT-4o text response with memory context)


## Classification Flow

  User Input
      │
      ▼
  ┌─────────────────────┐
  │ Load Memory Context  │  ← user.name, preferences, patterns, people
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────┐     ┌──────────────────────┐
  │   GPT-4o Classify   │────>│ Memory in system     │
  │   (AI-first)        │     │ prompt, user request  │
  └──────────┬──────────┘     │ as user message       │
             │                 └──────────────────────┘
             │ (if unavailable or fails)
             ▼
  ┌─────────────────────┐
  │ Deterministic       │  ← keyword/regex, instant, offline
  │ Classifier          │
  └──────────┬──────────┘
             │ (if still UNKNOWN)
             ▼
  ┌─────────────────────┐
  │ Fallback to ANSWER  │  ← GPT-4o answers directly, never "didn't understand"
  └─────────────────────┘


## Memory Tier System

  ┌──────────┐   3+ accesses   ┌──────────┐   7+ accesses   ┌──────────┐
  │   STM    │ ──────────────> │   MTM    │ ──────────────> │   LTM    │
  │  48 hrs  │                 │  30 days │                  │ Forever  │
  │  expires │                 │  expires │                  │ no expiry│
  └──────────┘                 └──────────┘                  └──────────┘

  Explicit statements ("my name is", "I like") ──────────> LTM directly


## Module Dependency Graph

  core (pure Kotlin)
    ▲
    │
  data (Room, DAOs, Memory, Context/Proactive engines)
    ▲
    │
  model (GPT-4o, ModelRouter, Cache)
    ▲
    │
  agent (Classifier, Planner, Approval, TaskExecutor)
    ▲
    │
  capability (Calendar, Messaging, System, Chat, Memory, Commerce, Notification)
    ▲
    │
  ui (Compose screens, ViewModel)
    ▲
    │
  app (Hilt, Activity, DI wiring, NotificationListener)


## What's Built vs Not Built

### DONE (v1 + v2 partial)
- [x] Custom Android launcher (HOME role)
- [x] Context surface with live clock, greeting, insight cards
- [x] Universal command bar with text input
- [x] Task feed with status-coded cards
- [x] App drawer with grid layout
- [x] AI-first intent classification (GPT-4o)
- [x] Deterministic classifier (16 intents, offline fallback)
- [x] Deterministic planner (intent → action steps)
- [x] 4-level approval engine (AUTO/NOTIFY/CONFIRM/BLOCK)
- [x] Append-only action log (immutable audit trail)
- [x] Task state machine (7 states)
- [x] Model routing with cache (30min TTL)
- [x] GPT-4o cloud provider (OpenAI API)
- [x] 7 capability providers (calendar, notification, messaging, commerce, system, memory, chat)
- [x] Fuzzy app matching (edit distance)
- [x] Chat capability (GPT-4o answers with memory context)
- [x] ANSWER fallback (never returns "didn't understand")
- [x] Personal knowledge graph (4 Room tables)
- [x] 3-tier memory (STM → MTM → LTM with auto-promotion)
- [x] MemoryExtractor (learns from every completed task)
- [x] MemoryManager (remember, recall, maintain, observe)
- [x] Memory audit UI (4 tabs, tier badges, delete)
- [x] AI-driven context surface (GPT-4o generates insight cards + suggestions)
- [x] AI-driven proactive engine (morning briefs, nudges, people reminders)
- [x] Cold start onboarding (4 seed questions)
- [x] 3 sensitivity modes (Quiet / Normal / Proactive)
- [x] Settings screen (API key, sensitivity)
- [x] Dismissal learning (proactive cards avoid dismissed types)
- [x] Debug time simulation (test any hour/day)

### NOT BUILT YET
- [ ] Local on-device model (Gemma Nano / LiteRT) — no cloud dependency
- [ ] Voice input (speech recognition)
- [ ] Multi-step task chains ("order uber then text Sarah")
- [ ] Conversation history (agent context across commands)
- [ ] Email triage + drafting loop
- [ ] 20+ app integrations (deep links for Uber, Spotify, etc.)
- [ ] Financial actions with Level 3 approval
- [ ] Third-party agent marketplace
- [ ] Enterprise MDM distribution
- [ ] Context/Proactive card caching (currently calls AI every refresh)
- [ ] Background refresh timer (currently per-command)
- [ ] Notification triage with real notification access
- [ ] Widget support
- [ ] Cross-device continuity

### NOT BUILT (v3 — Native OS)
- [ ] AOSP fork
- [ ] Custom SystemUI replacement
- [ ] Privileged agent scheduler service
- [ ] Capability-based security model
- [ ] Agent contract spec
- [ ] Generative UI runtime
- [ ] Zero permanent apps for top 20 use cases
- [ ] Android compat layer
- [ ] Developer SDK for skill publishing
- [ ] Reference hardware with OEM partner
- [ ] NPU-tuned on-device model stack
- [ ] Always-on ambient layer
- [ ] Cross-device continuity (phone + watch + glasses)
