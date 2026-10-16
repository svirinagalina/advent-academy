---
name: "Advent Academy"
description: "Public learning site and incumbent educational workspaces, with explicitly scoped themes"
colors:
  public-dark-page: "#08090b"
  public-dark-surface: "#121519"
  public-dark-alt: "#191e24"
  public-dark-field: "#191e24"
  public-dark-text: "#f4f7fa"
  public-dark-muted: "#a9b4bf"
  public-dark-accent: "#22d3d0"
  public-dark-on-accent: "#05232b"
  public-dark-border: "#303942"
  public-dark-control: "#718096"
  public-dark-hover: "#67e8e5"
  public-dark-active: "#15b8b5"
  public-dark-secondary-hover: "#242b33"
  public-dark-disabled: "#252c34"
  public-light-page: "#f5f6f8"
  public-light-surface: "#fff"
  public-light-alt: "#edf1f4"
  public-light-field: "#f8fafc"
  public-light-text: "#17212b"
  public-light-muted: "#526170"
  public-light-accent: "#0f766e"
  public-light-on-accent: "#fff"
  public-light-border: "#d3dae1"
  public-light-control: "#64748b"
  public-light-hover: "#115e59"
  public-light-active: "#134e4a"
  public-light-secondary-hover: "#e7edf1"
  public-light-disabled: "#e2e8f0"
  cabinet-dark-background: "#071827"
  cabinet-dark-surface: "#17283d"
  cabinet-dark-raised: "#20334a"
  cabinet-dark-field: "#0d2032"
  cabinet-dark-foreground: "#f1f5f9"
  cabinet-dark-muted: "#a8b8ca"
  cabinet-dark-accent: "#22d3d0"
  cabinet-dark-on-accent: "#05232b"
  cabinet-dark-border: "#2c4056"
  cabinet-dark-control-border: "#647b93"
  cabinet-dark-success: "#6ee7b7"
  cabinet-dark-warning: "#fcd34d"
  cabinet-dark-error: "#fda4af"
  cabinet-light-background: "#f4f7fa"
  cabinet-light-surface: "#ffffff"
  cabinet-light-raised: "#eef3f7"
  cabinet-light-field: "#f8fafc"
  cabinet-light-foreground: "#172b3a"
  cabinet-light-muted: "#475569"
  cabinet-light-accent: "#0f766e"
  cabinet-light-on-accent: "#ffffff"
  cabinet-light-border: "#b8c7d5"
  cabinet-light-control-border: "#64748b"
  cabinet-light-success: "#166534"
  cabinet-light-warning: "#92400e"
  cabinet-light-error: "#b91c1c"
typography:
  public-display:
    fontFamily: "Golos Text, Segoe UI, Arial, sans-serif"
    fontSize: "clamp(2.25rem, 4.2vw, 3.75rem)"
    fontWeight: 700
    lineHeight: 1.1
    letterSpacing: "-0.035em"
  public-headline:
    fontFamily: "Golos Text, Segoe UI, Arial, sans-serif"
    fontSize: "2.25rem"
    fontWeight: 600
    lineHeight: 1.22
    letterSpacing: "-0.03em"
  public-title:
    fontFamily: "Golos Text, Segoe UI, Arial, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.33
    letterSpacing: "-0.02em"
  public-body:
    fontFamily: "Golos Text, Segoe UI, Arial, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.625
  public-label:
    fontFamily: "Golos Text, Segoe UI, Arial, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 600
    lineHeight: 1.45
  public-code:
    fontFamily: "JetBrains Mono, monospace"
    fontSize: "0.8125rem"
    lineHeight: 1.8
  cabinet-heading:
    fontFamily: "Golos Text, Segoe UI, Arial, sans-serif"
    fontSize: "2rem"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "-0.035em"
  cabinet-body:
    fontFamily: "Golos Text, Segoe UI, Arial, sans-serif"
    fontSize: "16px"
    lineHeight: 1.5
rounded:
  small: "8px"
  control: "12px"
  panel: "16px"
  public-card: "20px"
  public-dialog: "24px"
  badge: "999px"
spacing:
  4: "4px"
  8: "8px"
  12: "12px"
  16: "16px"
  20: "20px"
  24: "24px"
  32: "32px"
  40: "40px"
  48: "48px"
  56: "56px"
  64: "64px"
  72: "72px"
  96: "96px"
components:
  public-button-primary:
    backgroundColor: "{colors.public-dark-accent}"
    textColor: "{colors.public-dark-on-accent}"
    rounded: "{rounded.control}"
    padding: "13px 20px"
    typography: "{typography.public-label}"
  public-button-primary-light:
    backgroundColor: "{colors.public-light-accent}"
    textColor: "{colors.public-light-on-accent}"
    rounded: "{rounded.control}"
    padding: "13px 20px"
  public-button-secondary:
    backgroundColor: "{colors.public-dark-surface}"
    textColor: "{colors.public-dark-text}"
    rounded: "{rounded.control}"
    padding: "13px 20px"
  public-field:
    backgroundColor: "{colors.public-dark-field}"
    textColor: "{colors.public-dark-text}"
    rounded: "{rounded.control}"
    padding: "12px"
  public-course-card:
    backgroundColor: "{colors.public-dark-surface}"
    textColor: "{colors.public-dark-text}"
    rounded: "{rounded.public-card}"
    padding: "32px"
  public-badge:
    backgroundColor: "{colors.public-dark-alt}"
    textColor: "{colors.public-dark-accent}"
    rounded: "{rounded.badge}"
    padding: "4px 12px"
---

# Design System: Advent Academy

## Overview

**Creative North Star: "Practical learning, clearly explained"**

The public site uses a quiet graphite or cool-paper canvas, teal actions, large Russian headings and diagrams of practical work. It presents learning with readable text, outlined Lucide icons and restrained bordered surfaces. The supplied public concept is the visual authority; the implemented CSS is the token authority.

Two visual scopes coexist. Public pages use frontend/src/app/public-site.css inside .public-site. Existing login, learner, teaching and administration screens keep frontend/src/app/globals.css; user administration additionally uses frontend/src/app/user-admin.css. Public graphite tokens do not replace the cabinet navy palette. Golos Text and JetBrains Mono are provided by the root layout.

**Key Characteristics:**
- Two explicit theme pairs, with separate public and cabinet scopes.
- Readable Russian typography, restrained teal emphasis and outlined diagrams.
- Keyboard focus, large controls and reduced-motion behavior.

## Colors

### Primary

Public teal supplies primary actions, focus and diagram emphasis; its light-theme counterpart is deeper for legibility. Cabinet teal has its own hover and active tokens. Frontmatter keys encode scope and theme explicitly; use the CSS variables at runtime rather than fixing a theme in a component.

### Neutral

Public graphite / cool-paper backgrounds, surfaces and alternate panels provide tonal grouping. Text and muted text have separate roles; control borders are stronger than decorative separators. Cabinets retain navy / cool-paper backgrounds, raised layers and their own text and border values. Cabinet success, warning and error colors communicate state and are not public promotional accents.

**The Scope Rule.** Choose the public or cabinet token family before styling a surface; never replace one with the other globally.

**The Semantic Accent Rule.** Use teal for actions, focus, selected states and diagram emphasis; keep explanatory copy muted.

## Typography

**Display and Body Font:** Golos Text, with Segoe UI, Arial and sans-serif fallbacks.
**Code Font:** JetBrains Mono, with monospace fallback.

Public headings use the frontmatter display, headline and title ramp; body is relaxed and paragraph widths generally stop at 65–70ch. Code windows use the mono role, horizontal overflow and disabled font ligatures. Public mobile headings become 2.25rem / 1.167, 1.75rem / 1.22 and 1.375rem below 768px; below 360px the main heading becomes 2rem / 1.1875. Lead paragraphs are 1.25rem / 1.5, becoming 1.125rem / 1.55 on mobile. Cabinets keep their denser body and heading ramp; user administration sets an explicit 1rem base. Do not carry public hero sizing into cabinet tables or dialogs.

## Layout

Public content is centered at a maximum 1248px with 32px side gutters, reducing to 16px below 768px. Header height is at least 80px, becoming 64px below 1200px with the mobile menu. Hero columns collapse at 900px; repeated grids collapse to one column below 768px. Section spacing steps from 96px to 72px and then 56px; cards use 24px gaps, reducing to 16px. Main buttons and fields have a 48px minimum height; icon controls and header links use 44px. Public content must remain readable at 320/390/768/1024/1440px, with keyboard access and enlarged text.

Cabinets retain their fixed 232px desktop sidebar and roughly 1200px content width. Management navigation collapses below 1024px. User administration uses a 1280px maximum and converts tables into stacked records below 1024px; filters collapse again below 768px. These are existing workspace layouts, not public landing-page grids.

## Elevation & Depth

Public surfaces use borders and tonal layers at rest. Dropdowns and dialogs have the restrained overlay shadow defined by the public overlay CSS tokens; the dialog backdrop dims the underlying page. Course cards move upward 3px with a stronger border on fine-pointer hover, without a new resting shadow. Button state transitions take 140ms and card transitions 180ms, using ease-out. Reduced motion removes transitions, animations and card displacement. Cabinet menus and dialogs retain their separate theme-dependent shadow.

## Shapes

Public fields and buttons use the control radius, secondary containers the panel radius, course cards the public-card radius and dialogs the public-dialog radius. Badges are pills. Internal diagram nodes use the small radius and thin strokes. Incumbent cabinet and user-admin controls commonly use the small radius, while workspace dialogs use the control radius. Keep these distinctions rather than imposing a single radius everywhere.

## Components

### Buttons

Primary public actions use accent with on-accent text; secondary actions use a surface fill and control border; text actions use accent with an underline on hover. Standard padding and radius are in frontmatter. Primary hover and pressed colors are the theme's public hover and active tokens. Focus uses a 2px accent outline offset 3px. Disabled actions retain full opacity, muted text and the disabled fill. Header buttons use a 44px minimum height and 10px 14px padding.

### Chips

Public badges use an alternate surface with accent text; neutral badges use muted text. Their role is informational, not an unlabelled interactive control. Cabinet user-role tags retain their raised fill and small-radius form.

### Cards / Containers

Course cards have a decorative border, surface fill and generous padding; mobile padding is 24px 20px. Feature and direction containers are smaller panel-radius surfaces with 24px padding. Avoid making static card bodies behave like controls when the actual action is a named link.

### Inputs / Fields

Public fields use the field background, stronger control border and control radius, with 12px padding and 1rem text. Labels remain visible. Keyboard focus follows the shared outline. Cabinet forms retain their existing field tokens and small radius. The unavailable public intake form remains disabled and explicitly explains the missing backend/privacy prerequisites.

### Navigation

Public navigation uses 0.875rem text, 44px minimum link targets, a sticky header and native disclosure for courses. At narrower widths it becomes a dialog menu with 48px rows. Preserve focus return and Escape behavior. The existing /login form leads to /cabinet; legacy /?view= routes remain supported.

### Practice diagrams and reading

Practice diagrams are semantic nodes with outlined Lucide icons and explicit explanatory text. They illustrate a process rather than simulate a working editor. Code uses JetBrains Mono; long reading uses 70ch maximum width, 1.25rem text and 1.7 line height. Program accordions use native details/summary, generous rows and visible state.

## Do's and Don'ts

### Do:
- **Do** reuse the tokens belonging to the surface being changed.
- **Do** retain readable server-rendered public content and the existing login boundary.
- **Do** preserve keyboard focus, labeled controls and reduced motion.
- **Do** show unavailable states until contact, partner, intake and privacy information is approved.

### Don't:
- **Don't** apply the public graphite palette to learner or administration screens.
- **Don't** invent submission success, partner destinations, legal text or product claims.
- **Don't** treat conceptual screenshots as pixel-exact goldens.

The supplied public composition uses accent uppercase introductory labels and arrow notation for sequence and relationships in diagrams. These are specific authored uses, not a requirement to add introductory labels or arrow decoration to other screens.
