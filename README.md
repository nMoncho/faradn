# Farad'n

Farad'n is an HTML-to-ESC/POS printing library written in Java (but **not**
limited to Java). The goal of this project is to be a one-stop shop for
your printing needs when dealing with  ESC/POS (thermal receipt) printers:
write your receipt as HTML, print it on paper.

HTML is a widely used and understood format, and can easily be templated
with tools such as [Mustache](https://mustache.github.io/).

We also provide native binaries and FFI libraries so if you don't work with
Java you can still use the project.

**Status.** The full pipeline works - HTML parsing, style resolution, the
renderer (text with word-wrap, images, barcodes, QR, tables), and transports
(USB and network) - plus a command line and HTTP server shipped as a GraalVM
native binary. Pre-1.0: the public API is not yet stable.

## Requirements

- Java 17+ (to use the library)
- GraalVM (only to build the native binary)

## Library usage

Parse an HTML document and send it to a printer:

```java
Document doc = Document.from("<h1>Receipt</h1><p>Total: <b>10,00</b></p>");

// Printer profiles are loaded by device name from the bundled capability
// database (escpos-printer-db); an unknown name yields an empty Optional.
PrinterProfile profile = PrinterProfile.load("TM-T88V").orElseThrow();

// Over USB, by the printer's USB vendor id (Epson is 0x04b8):
Printer.from(0x04b8).ifPresent(printer -> printer.print(doc, "TM-T88V"));

// Over Ethernet (raw TCP, port 9100), or any other Transport:
try (Transport transport = new NetworkTransport("192.168.1.50")) {
  Printer.print(transport, doc, profile);
}
```

Profiles come from the bundled `capabilities.conf` (generated from
[escpos-printer-db](https://github.com/receipt-print-hq/escpos-printer-db) by
`scripts/fetch_capabilities.py`). `PrinterProfile.load(name)` matches the device
name case-insensitively; a job fails if the name has no usable profile.

You can also inspect the intermediate representation the renderer consumes - a
flat, reading-order list of blocks with fully resolved styles - or render
straight to ESC/POS bytes:

```java
List<Block> blocks = doc.blocks();
byte[] escpos = new EscPosRenderer(TmT88vProfile.INSTANCE).render(blocks);
```

## Command line

The `faradn` binary (a native executable, or `java -jar faradn-cli.jar`) has
three modes:

```console
$ faradn list                                     # connected USB printers
$ faradn print receipt.html --printer 0x04b8      # print over USB
$ faradn print receipt.html --host 192.168.1.50   # print over Ethernet (TCP 9100)
$ faradn print receipt.html --dry-run > job.bin   # render to ESC/POS bytes
$ faradn serve --port 8080 --host 192.168.1.50    # run the HTTP print server
```

`print` also accepts `--profile` and `--copies`.

## HTTP server

`faradn serve` starts a small, dependency-free HTTP server:

| Endpoint        | Description                                                        |
|-----------------|--------------------------------------------------------------------|
| `POST /print`   | render the HTML request body and print it to the configured target |
| `GET /printers` | list connected USB printers                                        |
| `GET /health`   | liveness check                                                     |

```console
$ curl -X POST --data '<h1>Hi</h1>' http://localhost:8080/print
{"status":"printed","bytes":123}
```

The server has no authentication - keep it off untrusted networks (see
[SECURITY.md](SECURITY.md)).

## Architecture

Farad'n never translates the DOM directly into printer bytes. Documents flow
through a pipeline with an explicit intermediate representation (IR):

```
HTML ─jsoup─▶ DOM ─BlockBuilder─▶ List<Block> (IR) ─EscPosRenderer─▶ ESC/POS ─Transport─▶ printer
```

- **`ComputedStyle`** resolves tags and inline CSS into properties an ESC/POS
  printer can realize: bold, italic, underline, width/height multiples (1x–8x),
  alignment, inverted print. Italic uses the ESC/P `ESC 4`/`ESC 5` commands -
  printers that support italic render it, and the rest ignore the command.
- **`Block`** is a sealed hierarchy shaped by the line-oriented nature of
  ESC/POS: `Paragraph`, `ImageBlock`, `Barcode`, `Rule`, `Feed`, `Cut`, and
  `Table`. Every `TextRun` carries its fully resolved style, so renderers only
  diff consecutive runs to emit minimal state changes.
- **`BlockBuilder`** walks the DOM with an explicit style stack, normalizes
  whitespace HTML-style, and flushes inline content at block boundaries.
- **`EscPosRenderer`** turns the IR into bytes: it diffs run styles, word-wraps
  to the profile's column budget, selects a code page (`ESC t`), rasterizes
  images to `GS v 0` with Floyd–Steinberg dithering, emits barcodes (`GS k`) and
  QR/PDF417 (`GS ( k`), and lays tables out on a character grid with content-sized
  columns, `colspan`, and inline-styled cells. At end of job it feeds and cuts, unless
  the document already ends with an explicit `Cut`.
- **`Transport`** decouples byte generation from delivery: `UsbTransport`,
  `NetworkTransport` (TCP 9100) and `DumpTransport`, each able to read real-time
  status (`DLE EOT`) so a job can be refused before printing to an offline,
  covered, or out-of-paper printer.

This decoupling keeps HTML handling printer-agnostic and lets each stage be
tested in isolation: HTML → IR as plain object assertions, IR → bytes as
golden-byte tests.

### Modules

- **`faradn-core`**: the library (published to Maven Central).
- **`faradn-cli`**: the command line and HTTP server, shipped as a GraalVM
  native binary and attached to GitHub Releases.
- **`faradn-ffi`**: a native library that can be used with other languages,
  such as C or Rust.

## Supported HTML

**Tags**

| Markup                            | Effect                                                                                                     |
|-----------------------------------|------------------------------------------------------------------------------------------------------------|
| `<b>`, `<strong>`                 | bold                                                                                                       |
| `<u>`                             | underline                                                                                                  |
| `<small>`                         | narrower Font B (`ESC M`) - more columns per line                                                          |
| `<span>`                          | inline styling span - applies its inline CSS (below) to the enclosed text                                  |
| `<h1>`                            | bold, double width and height                                                                              |
| `<h2>`                            | bold, double height                                                                                        |
| `<h3>`                            | bold                                                                                                       |
| `<center>`                        | centered                                                                                                   |
| `<p>`, `<div>`, headings          | paragraph (block) boundaries                                                                               |
| `<ul>`, `<ol>`, `<li>`            | list items with `- ` / `1. ` markers (nested indents)                                                      |
| `<pre>`                           | preformatted: whitespace and line breaks preserved                                                         |
| `<br>`, `<hr>`                    | line break, horizontal rule                                                                                |
| `<table>`, `<tr>`, `<td>`, `<th>` | character-grid table: content-sized columns, `colspan`, per-cell `text-align`, inline styling, bold `<th>`, optional grid borders (below) |
| `<img>`                           | image (URL or Base64 `data:` URI; PNG, JPEG, BMP, WBMP)                                                    |
| `<em>`, `<i>`                     | italic (`ESC 4`/`ESC 5`); printers without italic ignore the command                                       |

**Inline CSS**

Applies to any element (`<span style="…">` is the usual carrier for styling a section of
text, but the same properties work on `<p>`, `<td>`, headings, and so on), and overrides
the tag defaults:

- `font-weight` (`bold`, `bolder`, `600`–`900` ⇒ bold; `normal` switches it off)
- `font-style` (`italic`, `oblique` ⇒ italic; `normal` switches it off)
- `text-decoration` / `text-decoration-line` (`underline`, `none`)
- `text-align` (`left`, `center`, `right`)
- `font-family` (`font-a`, `font-b`, `font-c`, …): select a font by slot (`font-a` is
  Font A, `font-b` Font B, and so on for printers with more fonts); unlike `<small>` it
  works on blocks, so `<table style="font-family: font-b">` renders the whole table in that
  font. The available fonts and their widths come from the capability database.
- `font-size`: scales the whole glyph via `GS !` magnification (width and height together).
  The printer only offers integer 1×–8× of the base font, so the value maps to the nearest
  multiple: `100%`/`1em`/`16px` ⇒ 1×, `200%`/`2em`/`32px` ⇒ 2×, and so on (`%`, `em`/`rem`, and
  keywords like `large`/`x-large` also work). Sub-1× sizes clamp to 1×. It overrides a tag's own
  size, so `<h2 style="font-size: 300%">` is 3× rather than double-height.
- `line-height` (a unit-less multiple like `1.5`, a `%`, or a length `px`/`mm`/`cm`): the
  spacing between a paragraph's lines, mapped to ESC/POS line spacing (`ESC 3`). Unit-less and
  `%` are relative to the font height (`1.0` packs lines tight, `2.0` doubles the gap); `px` is
  1:1 with dots. Inherits, so setting it on `<body>` or a `<div>` styles everything inside;
  `normal` restores the printer default.
- `font` (shorthand): sets `font-style`, `font-weight`, `font-size`, `line-height`, and
  `font-family` in one declaration, e.g. `font: italic bold 200%/1.5 font-b`. Font-size and
  font-family are required; `font-variant`/`font-stretch` and system-font keywords (`menu`, …)
  are ignored. As in CSS, the shorthand resets the components it omits (so `font: 2em font-a` is
  not bold even on a `<b>`).

**Table borders.** ESC/POS standard mode has no line command, so borders are drawn with
box-drawing characters on the same monospace grid the table already uses (PC437/PC850 carry the
glyphs; the encoder switches to them automatically). Turn them on with the HTML `border`
attribute or a CSS `border` on the `<table>`:

```html
<table border="1">…</table>                        <!-- single-line grid ─│┼ -->
<table style="border-style: double">…</table>      <!-- double-line grid ═║╬ -->
```

This frames the table and draws separators between every cell; the border eats `columnCount + 1`
columns of width. `border: none` (or `border="0"`) turns it off. Only single vs double weight is
expressible — colour, radius, dashed/dotted, and per-side widths don't map. A `colspan` cell
merges correctly, and the grid joins around it pick the right glyph (`┴`/`┬`/`─`) so the lines
meet cleanly.

A CSS `border-top` / `border-bottom` (or the `border` shorthand) on a **`<p>` or `<div>`** draws
a full-width rule above and/or below the paragraph — handy for a line under a total:

```html
<p style="border-bottom: 1px solid">Subtotal      7,50</p>
<p style="border-bottom: 3px double">TOTAL         9,00</p>
```

`border-style: double` (or a `double` in the shorthand) uses the `═` line. Adding the left/right
sides (or the `border` shorthand, which sets all four) draws a **full box** — corners, side
`│` rails, and content wrapped to fit inside:

```html
<p style="border: 1px solid">Keep this receipt for any returns.</p>
```

The box is full paper width; content is wrapped to `columns − 2` and aligned inside per the
paragraph's `text-align`. A box spanning several separate blocks (a bordered `<div>` around
multiple paragraphs) isn't supported yet.

**Barcodes**

Either a custom element or a `bar-code` class with a symbology modifier:

```html
<bar-code symbology="code128">12345678</bar-code>
<bar-code symbology="qr">https://example.com</bar-code>
<div class="bar-code bar-code--upc-a">72527273073</div>
```

Supported symbologies: `code128`, `code39`, `code93`, `ean13`, `ean8`, `upca`,
`upce`, `itf`, `codabar` (1D), and `qr` / `pdf417` (2D).

Rendering is configurable per barcode through attributes:

| Attribute | Applies to | Values                              | Default       |
|-----------|------------|-------------------------------------|---------------|
| `height`  | 1D         | height in dots (1–255)              | `100`         |
| `module`  | all        | module/bar width (1D 2–6; 2D 1–16)  | per symbology |
| `hri`     | 1D         | `none`, `above`, `below`, `both`    | `below`       |
| `ec`      | QR         | error correction `l`, `m`, `q`, `h` | `m`           |

```html
<bar-code symbology="ean13" height="80" module="3" hri="below">123456789012</bar-code>
<bar-code symbology="qr" module="8" ec="h">https://example.com</bar-code>
```

**Positioned layout (page mode).** Most receipts flow top to bottom, but a bounded region -
a header, a coupon, a label - can place its pieces at exact coordinates using ESC/POS *page
mode*. A sized, `position: relative` container with `position: absolute` children maps to such
a region: each child is drawn at its `left`/`top`, and the whole area prints at once.

```html
<div style="position: relative; width: 512px; height: 160px">
  <span style="position: absolute; left: 0;    top: 0">Order #42</span>
  <span style="position: absolute; left: 320px; top: 0">Table 7</span>
  <img   style="position: absolute; left: 0;    top: 40px" src="data:image/png;base64,…">
  <bar-code style="position: absolute; left: 0; top: 96px" symbology="qr">…</bar-code>
</div>
```

- The container needs `position: relative` (or `absolute`) **and** an explicit `width` and
  `height`; without both it stays a normal flowing block.
- Positioned children may hold text (`<span>`/`<p>`/…), an `<img>`, or a `<bar-code>`. Lengths
  accept `px` (1 px = 1 dot), `mm`/`cm` (converted with the printer's dpi), and `%` (of the
  area); `left`/`top` default to `0`.
- Children *without* `position: absolute` are ignored inside the container. Content past the
  area is clipped by the printer, so give the container enough height.
- `transform: rotate(90deg | 180deg | 270deg)` on the container rotates the whole region
  (mapped to the ESC/POS print direction); other angles snap to the nearest right angle.

Giving the **`<body>` itself** a `width` and `height` makes the *whole job* one such area - a
fixed-size label, badge, or ticket rather than a flowing receipt:

```html
<body style="width: 512px; height: 260px">
  <span style="position: absolute; left: 0; top: 0"><b>FARAD'N CONF</b></span>
  <span style="position: absolute; left: 0; top: 64px">Ada Lovelace</span>
  <bar-code style="position: absolute; left: 0; top: 152px" symbology="code128">ID-0042</bar-code>
</body>
```

A body without a size prints as a normal top-to-bottom receipt, so this only kicks in when you
ask for it. The label ends with the usual feed and cut, which separates labels on continuous
receipt paper.

**Text encoding.** Text starts on the profile's default code page (TM-T88V's is
PC437) and the renderer switches pages inline (`ESC t`) for glyphs outside the
current one, so mixed-script receipts encode faithfully instead of collapsing to
`?`. The selectable pages are the printer's own `ESC t` slots, read from its
capability-database profile (`PrinterProfile.codePages()`, single-byte pages
only) rather than a fixed list, so each model switches only among the pages it
actually has. The current page is preferred, so a run of one script costs a
single switch and ASCII never forces one; a glyph in none of the printer's pages
still falls back to `?`.

**Images.** PNG is decoded in pure Java, so it works everywhere, including the
GraalVM native binary. The JVM library (and `java -jar`) additionally reads JPEG,
BMP and WBMP via `javax.imageio`; those formats are **not** available in the native
binary, which has no AWT.

## Previewing print jobs

Thermal output is hard to picture from HTML alone, so this repo provides a stylesheet
that renders a print job in a browser roughly the way it will come off the printer:
a narrow monospace paper roll on a character grid, with each supported tag mapped to
the visual the ESC/POS renderer produces (double-size headings, `- `/`1.` list
markers, dashed rules, content-sized table columns, 1D/2D barcode placeholders).

Add this line to the `<head>` of any print job and open it in a browser:

```html
<link rel="stylesheet" href="docs/faradn-preview.css">
```

- **[`docs/faradn-preview.css`](docs/faradn-preview.css)** — the stylesheet. Two
  knobs at the top: `--columns` (paper width in characters — `42` ≈ 80mm, `32` ≈
  58mm) and `--font-size`.
- **[`docs/preview-demo.html`](docs/preview-demo.html)** — a sample receipt that
  exercises every supported feature; open it to see the whole vocabulary at once.

This is an approximation, not an emulator: word-wrap points, exact column widths and
image dithering are the browser's, not the printer's. It conveys the feel and catches
layout mistakes early, but is not byte-accurate — the printer is the source of truth.

## Supported Devices

This project aims to support as many devices as possible, not only ESC/POS
(i.e. Epson) printers, but other thermal printers such as Brother, Zebra, among
others. If you've access to a printer that's not listed below, and would like
to contribute, please see [CONTRIBUTING.md](CONTRIBUTING.md).

### Capabilities per Device

| Brand    | Model           | Basic Styles       | Images             | Tables             | Barcodes           |
|----------|-----------------|--------------------|--------------------|--------------------|--------------------|
| Epson    | TM-T88V         | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |

**Basic Styles**

- Bold text
- Underlined text
- Left, center, and right text alignment
- Double-width / double-height text (headings)

## Building

```console
$ ./mvnw verify                                    # build and test everything
$ ./mvnw -pl faradn-cli -am -Pnative package       # build the native binary (needs GraalVM)
```

On **Apple Silicon**, usb4java 1.3.0 ships no `darwin-aarch64` native on Maven
Central. Build/obtain that `libusb4java` jar once and install it locally:

```console
$ mvn install:install-file -Dfile=libusb4java-1.3.0-darwin-aarch64.jar \
    -DgroupId=org.usb4java -DartifactId=libusb4java -Dversion=1.3.0 \
    -Dclassifier=darwin-aarch64 -Dpackaging=jar
```

The `macos-aarch64` Maven profile then wires it in automatically.

## Roadmap

- [x] HTML → IR: style resolution, paragraphs, images, barcodes
- [x] IR → ESC/POS renderer (diffing run styles, reusing the command layer)
- [x] Word wrapping based on printer profile (paper width × font size)
- [x] Character-grid layout for `<table>` and column layouts
- [x] Code page handling (Unicode → `ESC t` selection per printer)
- [x] Transports (USB and raw TCP 9100) with real-time status
- [x] CLI and HTTP server as a GraalVM native binary
- [ ] Publish `faradn-core` to Maven Central and native binaries to GitHub Releases
- [x] List markers, preformatted text, per-barcode options, aligned table cells
- [x] Per-run code page switching for mixed-script text
- [x] Table colspan and column widths
- [x] Printer capability database: load profiles by device name from [escpos-printer-db](https://github.com/receipt-print-hq/escpos-printer-db)

## Contribution Guidelines

Please see [CONTRIBUTING.md](CONTRIBUTING.md).

## Why Farad'n?

_Dune_ is one of my favourite books, there Farad'n is the grandson of Padishah
Emperor Shaddam IV, and he's the royal scribe by occupation.
