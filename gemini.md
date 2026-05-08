# Technische Spezifikation: Team Alpha Planning Poker

## 1. Branding & Identität
- **Projektname:** Team Alpha Planning Poker
- **Kern-Farbschema:** 
    - **Primary:** Sparkassen-Rot (`#E30613`)
    - **Secondary/Background:** Tiefschwarz (`#000000`) und Graphit (`#1A1A1A`)
    - **Text/Icons:** Reinweiß (`#FFFFFF`) für maximale Lesbarkeit auf dunklem Grund.

## 2. UI/UX Design "Alpha High-Contrast"
- **Header:** Schwarzer Balken mit rotem "TEAM ALPHA" Logo-Schriftzug (links) und der Room-ID in Monospace (rechts).
- **Buttons:** 
    - Primäre Actions (z.B. "Reveal"): Roter Hintergrund, weißer Text, scharfe Kanten (0-2px Radius).
    - Hover-State: Ein dunkleres Rot oder ein weißer Glow-Effekt.
- **Karten-Design:** 
    - Rückseite: Schwarz mit rotem Alpha-Logo oder einem schlichten roten Rand.
    - Vorderseite (Revealed): Weißer Hintergrund, rote, fette Zahlen (Fibonacci).
- **Zustands-Feedback:**
    - Spieler hat gewählt: Karte leuchtet am Rand dezent rot auf.
    - Konsens-Check: Wenn alle gleich schätzen, pulsiert der Bildschirmrand einmal kurz rot.

## 3. Funktionale Spezifikationen
- **Ktor-Integration:** Nutzung des `DefaultWebSocketServerSession` zur Verwaltung der Client-Pools.
- **Security:** Da das Tool intern genutzt wird, reicht eine einfache In-Memory-Validierung der Room-IDs.
- **Performance:** Optimierung auf minimalen DOM-Tree im Frontend für sofortige Reaktion auf Klicks.

## 4. Key Shortcuts (Alpha-Efficiency)
- `1-9`: Direkte Wahl der Fibonacci-Werte.
- `Space`: Karten aufdecken (nur für den Host).
- `Esc`: Reset / Nächste Runde.
