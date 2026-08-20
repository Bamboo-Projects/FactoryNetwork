# -*- coding: utf-8 -*-
"""Erzeugt die Texturübersicht als eigenständige HTML-Seite."""
import base64
import os

TEX = r"D:\Projekte\FactoryNetwork\src\main\resources\assets\factorynetwork\textures"
OUT = r"C:\Users\admin\AppData\Local\Temp\claude\D--Projekte-AE2SFM\aa2e8e97-2324-4b86-aec2-b62821162d2b\scratchpad\texturen.html"


def data_uri(folder, name):
    path = os.path.join(TEX, folder, name + ".png")
    with open(path, "rb") as handle:
        encoded = base64.b64encode(handle.read()).decode("ascii")
    return "data:image/png;base64," + encoded, os.path.getsize(path)


# Block, Textur, Fläche, Verwendung
GROUPS = [
    ("Controller", "Wurzel des Netzwerks. Hält Programm, Speicher und Laufzeit.", [
        ("controller_top", "Oben und unten", "leuchtender Kern mit vier Zuleitungen"),
        ("controller_side", "Vier Seiten", "Kühlrippen"),
    ]),
    ("Kabel", "Verbindet alles zu einem Netzwerk. Wird als 6×6-Röhre gerendert.", [
        ("cable", "Alle Flächen", "längs geriffeltes Metall"),
    ]),
    ("Connector", "Gibt der Maschine dahinter einen Namen.", [
        ("connector_front", "Zur Maschine", "Anschlussring mit grünem Kern"),
        ("connector_side", "Vier Seiten", "Rahmen"),
        ("machine_top", "Oben und unten", "geteilt mit dem Terminal"),
        ("connector_back", "— unbenutzt —", "gemalt, aber in keinem Modell eingebunden"),
    ]),
    ("Terminal", "Zugang zum Code-Editor.", [
        ("terminal_front", "Vorderseite", "Bildschirm mit angedeuteten Codezeilen"),
        ("terminal_side", "Seiten und Rückseite", "Streben"),
    ]),
]

ITEM = ("label_gun", "Label-Gun", "Vergibt Connectoren ihre Namen.")

PALETTE = [
    ("#22252 9".replace(" ", ""), "Kante", "dunkelster Ton, Umrisse"),
    ("#3C4147", "Grundfläche", "das Metall selbst"),
    ("#565C63", "Licht", "obere und linke Kanten"),
    ("#8AB4F8", "Netzwerk", "Controller-Kern, Schlüsselwörter im Editor"),
    ("#A3D9A5", "Aktiv", "Connector-Kern, Auswahlausdrücke im Editor"),
    ("#BC9854", "Werkzeug", "nur die Mündung der Label-Gun"),
]

STYLE = """
<title>Factory Network Texturen</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Chivo:wght@700;900&family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@400;500&display=swap">
<style>
:root {
  --ground: #EDEEF0;
  --surface: #FFFFFF;
  --surface-sunken: #E2E4E8;
  --line: #C7CBD1;
  --text: #1A1D21;
  --muted: #626A73;
  --blue: #2F6BC4;
  --green: #3F8C4A;
  --red: #C0474C;
  --checker-a: #D8DBE0;
  --checker-b: #C9CDD3;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --ground: #141619;
    --surface: #1E2126;
    --surface-sunken: #0E1013;
    --line: #31363C;
    --text: #D8DEE4;
    --muted: #868F99;
    --blue: #8AB4F8;
    --green: #A3D9A5;
    --red: #E88388;
    --checker-a: #26292F;
    --checker-b: #1B1E22;
  }
}
:root[data-theme="dark"] {
  --ground: #141619;
  --surface: #1E2126;
  --surface-sunken: #0E1013;
  --line: #31363C;
  --text: #D8DEE4;
  --muted: #868F99;
  --blue: #8AB4F8;
  --green: #A3D9A5;
  --red: #E88388;
  --checker-a: #26292F;
  --checker-b: #1B1E22;
}

* { box-sizing: border-box; }

body {
  margin: 0;
  background: var(--ground);
  color: var(--text);
  font-family: "IBM Plex Sans", system-ui, sans-serif;
  font-size: 15px;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

.wrap {
  max-width: 1000px;
  margin: 0 auto;
  padding: 56px 24px 96px;
  display: flex;
  flex-direction: column;
  gap: 56px;
}

h1, h2, h3 { font-family: Chivo, "IBM Plex Sans", sans-serif; margin: 0; text-wrap: balance; }
h1 { font-size: 40px; font-weight: 900; letter-spacing: -0.02em; line-height: 1.1; }
h2 { font-size: 13px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; color: var(--muted); }
h3 { font-size: 21px; font-weight: 700; letter-spacing: -0.01em; }

.lede { color: var(--muted); max-width: 62ch; margin: 0; }

code, .mono { font-family: "IBM Plex Mono", ui-monospace, monospace; font-size: 13px; }

/* ---- Kopf ---- */
header { display: flex; flex-direction: column; gap: 16px; }
.meta { display: flex; flex-wrap: wrap; gap: 8px 20px; color: var(--muted); font-size: 13px; }
.meta b { color: var(--text); font-weight: 500; }

/* ---- Werkzeugleiste ---- */
.tools {
  display: flex; flex-wrap: wrap; gap: 10px; align-items: center;
  padding: 12px 14px; background: var(--surface); border: 1px solid var(--line); border-radius: 4px;
}
.tools span { color: var(--muted); font-size: 13px; margin-right: 4px; }
button {
  font-family: "IBM Plex Mono", monospace; font-size: 13px;
  background: transparent; color: var(--text);
  border: 1px solid var(--line); border-radius: 3px;
  padding: 5px 11px; cursor: pointer;
}
button:hover { border-color: var(--muted); }
button[aria-pressed="true"] { background: var(--text); color: var(--ground); border-color: var(--text); }
button:focus-visible { outline: 2px solid var(--blue); outline-offset: 2px; }
</style>
"""

STYLE2 = """
<style>
/* ---- Gruppen ---- */
section { display: flex; flex-direction: column; gap: 20px; }
.group-head { display: flex; flex-direction: column; gap: 4px; }
.group-head p { margin: 0; color: var(--muted); font-size: 14px; }

.tiles { display: flex; flex-wrap: wrap; gap: 18px; }

.tile {
  display: flex; flex-direction: column; gap: 10px;
  padding: 14px; background: var(--surface);
  border: 1px solid var(--line); border-radius: 4px;
}
.tile.unused { border-style: dashed; }

/* Schachbrett zeigt, wo die Textur durchsichtig ist. */
.frame {
  background-image:
    linear-gradient(45deg, var(--checker-b) 25%, transparent 25%, transparent 75%, var(--checker-b) 75%),
    linear-gradient(45deg, var(--checker-b) 25%, transparent 25%, transparent 75%, var(--checker-b) 75%);
  background-size: 16px 16px;
  background-position: 0 0, 8px 8px;
  background-color: var(--checker-a);
  border: 1px solid var(--line);
  align-self: flex-start;
  line-height: 0;
}
.frame img {
  display: block;
  image-rendering: pixelated;
  width: calc(16px * var(--zoom, 7));
  height: calc(16px * var(--zoom, 7));
}

.tile-name { font-weight: 500; font-size: 14px; }
.tile-face { color: var(--muted); font-size: 13px; }
.tile-note { color: var(--muted); font-size: 13px; font-style: italic; }
.tile-file { color: var(--muted); }
.tile.unused .tile-face { color: var(--red); }

/* ---- Erkennbarkeitstest ---- */
.strip {
  display: flex; flex-wrap: wrap; gap: 0;
  padding: 20px; background: var(--surface-sunken);
  border: 1px solid var(--line); border-radius: 4px;
  overflow-x: auto;
}
.strip img {
  image-rendering: pixelated;
  width: 48px; height: 48px;
  border-right: 1px solid var(--line);
}
.strip img:last-child { border-right: 0; }

/* ---- Palette ---- */
.swatches { display: flex; flex-wrap: wrap; gap: 12px; }
.swatch {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 12px 8px 8px;
  background: var(--surface); border: 1px solid var(--line); border-radius: 4px;
}
.chip { width: 30px; height: 30px; border-radius: 2px; border: 1px solid rgba(128,128,128,.35); flex: none; }
.swatch-text { display: flex; flex-direction: column; line-height: 1.35; }
.swatch-name { font-size: 13px; font-weight: 500; }
.swatch-use { font-size: 12px; color: var(--muted); }

/* ---- Hinweiskasten ---- */
.note {
  padding: 16px 18px; background: var(--surface);
  border: 1px solid var(--line); border-left: 3px solid var(--red);
  border-radius: 4px;
}
.note p { margin: 0 0 8px; }
.note p:last-child { margin-bottom: 0; }
.note strong { font-weight: 500; }

/* ---- Fussbereich ---- */
.facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; }
.fact { padding: 14px 16px; background: var(--surface); border: 1px solid var(--line); border-radius: 4px; }
.fact dt { font-size: 12px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); margin-bottom: 6px; }
.fact dd { margin: 0; font-size: 14px; }
.fact dd + dt { margin-top: 12px; }

@media (max-width: 620px) {
  h1 { font-size: 30px; }
  .wrap { padding: 36px 16px 64px; gap: 40px; }
}
@media (prefers-reduced-motion: reduce) {
  * { animation: none !important; transition: none !important; }
}
</style>
"""


def tile(folder, name, face, note, unused=False):
    uri, size = data_uri(folder, name)
    classes = "tile unused" if unused else "tile"
    return f"""      <figure class="{classes}">
        <div class="frame"><img src="{uri}" alt="{name}" width="16" height="16"></div>
        <figcaption>
          <div class="tile-name">{face}</div>
          <div class="tile-note">{note}</div>
          <div class="tile-file mono">{name}.png · {size} B</div>
        </figcaption>
      </figure>"""


def build():
    parts = [STYLE, STYLE2, '<div class="wrap">']

    parts.append(f"""  <header>
    <h1>Factory Network Texturen</h1>
    <p class="lede">Zehn Bilder zu je 16&times;16 Pixeln, sämtlich mit
      <code>tools/textures.py</code> gezeichnet — keine Vanilla-Textur kopiert. Die
      Palette ist dieselbe, die der Code-Editor der Mod benutzt, damit Block und
      Programm sichtbar zusammengehören.</p>
    <div class="meta">
      <span><b>10</b> Texturen</span>
      <span><b>16&times;16</b> Pixel</span>
      <span><b>4</b> Blöcke, 1 Gegenstand</span>
      <span><b>Stand</b> 20.08.2026</span>
    </div>
  </header>""")

    parts.append("""  <div class="tools">
    <span>Vergrösserung</span>
    <button type="button" data-zoom="1">1&times;</button>
    <button type="button" data-zoom="4">4&times;</button>
    <button type="button" data-zoom="7" aria-pressed="true">7&times;</button>
    <button type="button" data-zoom="12">12&times;</button>
    <span style="margin-left:auto">Grund</span>
    <button type="button" data-theme="dark">dunkel</button>
    <button type="button" data-theme="light">hell</button>
  </div>""")

    for title, description, textures in GROUPS:
        tiles = "\n".join(
            tile("block", name, face, note, unused=(face.startswith("—")))
            for name, face, note in textures)
        parts.append(f"""  <section>
    <div class="group-head">
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
    <div class="tiles">
{tiles}
    </div>
  </section>""")

    name, title, description = ITEM
    parts.append(f"""  <section>
    <div class="group-head">
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
    <div class="tiles">
{tile("item", name, "Gegenstand", "Griff, Korpus, Mündung in Messing")}
    </div>
  </section>""")

    # Erkennbarkeitstest: alle Texturen ohne Beschriftung nebeneinander.
    strip_names = [("block", n) for _, _, textures in GROUPS for n, _, _ in textures]
    strip_names.append(("item", ITEM[0]))
    strip = "\n".join(
        '      <img src="%s" alt="%s">' % (data_uri(folder, name)[0], name)
        for folder, name in strip_names)
    parts.append(f"""  <section>
    <div class="group-head">
      <h2>Erkennbarkeitstest</h2>
      <p>Dieselben zehn Texturen, unbeschriftet und klein — so begegnen sie einem
        im Spiel. Was sich hier nicht auseinanderhalten lässt, lässt sich auch
        an der Wand nicht auseinanderhalten.</p>
    </div>
    <div class="strip">
{strip}
    </div>
    <div class="note">
      <p><strong>Das ist die Schwachstelle.</strong> Eindeutig sind nur drei:
        der Controller von oben, die Vorderseite des Connectors und die des
        Terminals. Die übrigen sind graue Rechtecke mit Rahmen oder Streifen —
        Kabel und Terminal-Seite unterscheiden sich fast gar nicht.</p>
      <p>Zwei Wege hinaus: jedem Block eine eigene Grundfarbe geben, oder auf
        jeder Seitenfläche ein kleines Kennzeichen anbringen. Das Erste ist
        deutlicher, das Zweite bleibt näher an der gedeckten Palette.</p>
    </div>
  </section>""")

    swatches = "\n".join(
        f"""      <div class="swatch">
        <div class="chip" style="background: {value}"></div>
        <div class="swatch-text">
          <span class="swatch-name">{name} <span class="mono">{value}</span></span>
          <span class="swatch-use">{use}</span>
        </div>
      </div>""" for value, name, use in PALETTE)
    parts.append(f"""  <section>
    <div class="group-head">
      <h2>Palette</h2>
      <p>Sechs Werte. Blau und Grün sind dieselben, mit denen der Editor
        Schlüsselwörter und Auswahlausdrücke einfärbt.</p>
    </div>
    <div class="swatches">
{swatches}
    </div>
  </section>""")

    parts.append("""  <section>
    <div class="group-head"><h2>Technisches</h2></div>
    <div class="facts">
      <dl class="fact">
        <dt>Herkunft</dt>
        <dd>Alle Bilder erzeugt <code>tools/textures.py</code> (Python, Pillow).
          Ein Aufruf schreibt sie neu.</dd>
        <dt>Ablage</dt>
        <dd><code>src/main/resources/assets/factorynetwork/textures/</code></dd>
      </dl>
      <dl class="fact">
        <dt>Modellvorlagen</dt>
        <dd><code>cube_bottom_top</code> für den Controller,
          <code>orientable</code> für Connector und Terminal,
          <code>handheld</code> für die Gun. Das Kabel hat ein eigenes Modell aus
          Kern und sechs Armen.</dd>
      </dl>
      <dl class="fact">
        <dt>Im Spiel ansehen</dt>
        <dd><code>./gradlew runClient</code>, dann der Kreativ-Reiter
          „Factory Network".</dd>
        <dt>Rezepte</dt>
        <dd>Eisen, Redstone, Glasscheibe, Stock, Goldnugget — bewusst billig.</dd>
      </dl>
    </div>
  </section>""")

    parts.append("</div>")
    parts.append("""<script>
(function () {
  var root = document.documentElement;
  function press(group, active) {
    document.querySelectorAll('button[data-' + group + ']').forEach(function (button) {
      button.setAttribute('aria-pressed', String(button.dataset[group] === active));
    });
  }
  document.querySelectorAll('button[data-zoom]').forEach(function (button) {
    button.addEventListener('click', function () {
      document.querySelectorAll('.frame').forEach(function (frame) {
        frame.style.setProperty('--zoom', button.dataset.zoom);
      });
      press('zoom', button.dataset.zoom);
    });
  });
  document.querySelectorAll('button[data-theme]').forEach(function (button) {
    button.addEventListener('click', function () {
      root.setAttribute('data-theme', button.dataset.theme);
      press('theme', button.dataset.theme);
    });
  });
})();
</script>""")
    return "\n".join(parts)


if __name__ == "__main__":
    with open(OUT, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(build())
    print("geschrieben: %s (%.1f KB)" % (OUT, os.path.getsize(OUT) / 1024))
