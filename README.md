# Farad'n

Farad'n is an HTML-to-ESC/POS printing library for Java. The goal of this
project is to be a one stop shop for your printing needs when dealing with
ESC/POS (thermal receipt) printers: write your receipt as HTML, print it on
paper.

HTML is a widely used and understood format, and can easily be templated
with tools such as [Mustache](https://mustache.github.io/).

**Status: work in progress.** The HTML front end (parsing, style resolution,
intermediate representation) and the low-level ESC/POS command layer are in
place and tested; the renderer connecting the two is the next milestone.

## Requirements

- Java 17+

## Usage

Parse an HTML document and send it to a printer as a print job:

```java
String html = "<html>...";
Document doc = Document.from(html);

// Look the printer up by its USB vendor id (and optionally product id)
Printer.from(0x04b8).ifPresent(printer -> printer.print(doc));
```

You can also inspect the intermediate representation the renderer consumes,
a flat, reading-order list of blocks with fully resolved styles:

```java
Document doc = Document.from("<h1>Receipt</h1><p>Total: <b>10,00</b></p>");
List<Block> blocks = doc.blocks();
// [Paragraph[runs=[TextRun[text=Receipt, style=... bold, 2x2 ...]], alignment=LEFT],
//  Paragraph[runs=[TextRun[text=Total: ...], TextRun[text=10,00, ... bold ...]], alignment=LEFT]]
```

## Architecture

Farad'n never translates the DOM directly into printer bytes. Documents flow
through a pipeline with an explicit intermediate representation (IR):

```
HTML string ── jsoup ──> DOM ── BlockBuilder ──> List<Block> (IR) ── renderer ──> ESC/POS bytes
```

- **`ComputedStyle`** resolves tags and inline CSS into the properties an
  ESC/POS printer can actually realize: bold, underline, width/height
  multiples (1x–8x), alignment, inverted print. There is deliberately no
  italic — most ESC/POS printers cannot print it.
- **`Block`** is a sealed hierarchy shaped by the fact that ESC/POS is
  line-oriented: `Paragraph` (styled text runs), `ImageBlock`, `Barcode`,
  `Rule`, `Feed`, and `Cut`. Every `TextRun` carries its fully resolved
  style, so renderers only diff consecutive runs to emit minimal state
  changes.
- **`BlockBuilder`** walks the DOM with an explicit style stack, normalizes
  whitespace HTML-style, and flushes inline content at block boundaries.

This decoupling keeps HTML handling printer-agnostic and lets each stage be
tested in isolation: HTML → IR as plain object assertions, IR → bytes as
golden-byte tests.

## Supported HTML

**Tags**

| Markup | Effect |
|---|---|
| `<b>`, `<strong>` | bold |
| `<u>` | underline |
| `<h1>` | bold, double width and height |
| `<h2>` | bold, double height |
| `<h3>` | bold |
| `<center>` | centered |
| `<p>`, `<div>`, headings, lists, … | paragraph (block) boundaries |
| `<br>`, `<hr>` | line break, horizontal rule |
| `<img>` | image (URL or Base64 `data:` URI; PNG, JPEG, BMP, WBMP) |
| `<em>`, `<i>` | ignored — ESC/POS printers have no italic |

**Inline CSS**

- `font-weight` (`bold`, `bolder`, `600`–`900` ⇒ bold; `normal` switches it off)
- `text-decoration` / `text-decoration-line` (`underline`, `none`)
- `text-align` (`left`, `center`, `right`)

**Barcodes**

Either a custom element or a `bar-code` class with a symbology modifier:

```html
<bar-code symbology="code128">12345678</bar-code>
<div class="bar-code bar-code--upc-a">72527273073</div>
```

## Supported Devices

This project aims to support as many devices as possible, not only ESC/POS
(i.e. Epson) printers, but other thermal printers such as Brother, Zebra, among
others. If you've access to a printer that's not listed below, and would like
to contribute, please see [CONTRIBUTING.md](CONTRIBUTING.md).

### Capabilities per Device

| Brand    | Model           | Basic Styles       | Images             | Tables             | Barcodes           |
|----------|-----------------|--------------------|--------------------|--------------------|--------------------|
| Epson    | TM-T88V         | :white_check_mark: | :white_check_mark: |                    |                    |

**Basic Styles**

- Bold text
- Underlined text
- Left, center, and right text alignment
- Double-width / double-height text (headings)

## Roadmap

- [x] HTML → IR: style resolution, paragraphs, images, barcodes
- [ ] IR → ESC/POS renderer (diffing run styles, reusing the command layer)
- [ ] Word wrapping based on printer profile (paper width × font size)
- [ ] Character-grid layout for `<table>` and column layouts
- [ ] Code page handling (Unicode → `ESC t` selection per printer)
- [ ] Printer capability database (see [escpos-printer-db](https://github.com/receipt-print-hq/escpos-printer-db))
- [ ] Raster fallback for complex layouts

## Contribution Guidelines

Please see [CONTRIBUTING.md](CONTRIBUTING.md).

## Why Farad'n?

_Dune_ is one of my favourite books, there Farad'n is the grandson of Padishah
Emperor Shaddam IV, and he's the royal scribe by occupation.
