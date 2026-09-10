# faradn changelog

Changelog of faradn.

## v1.0.0 (2026-09-10)

### Features

-  release FFI lib for Linux ARM64 ([48757](https://github.com/nMoncho/faradn/commit/48757296aa10dfb) Gustavo De Micheli)
-  bundle LICENSE / NOTICE / THIRD-PARTY.txt with binaries ([fa492](https://github.com/nMoncho/faradn/commit/fa49231a5c97f7e) Gustavo De Micheli)
-  add documentation on security, plus Trivy Scan on GitHub ([8432d](https://github.com/nMoncho/faradn/commit/8432d833f40c185) Gustavo De Micheli)
-  **cli**  add `--bind` parameter to set on which interface the server is bound to ([75114](https://github.com/nMoncho/faradn/commit/75114e95c57ed05) Gustavo De Micheli)
-  **cli**  use `ImagePolicy` on module ([ef93e](https://github.com/nMoncho/faradn/commit/ef93ee3224a5a26) Gustavo De Micheli)
-  **core**  add CycloneDX SBOM plugin to project ([f5473](https://github.com/nMoncho/faradn/commit/f54739f6cf30e99) Gustavo De Micheli)
-  **core**  make Image class use policy and raster size checks ([a9a2c](https://github.com/nMoncho/faradn/commit/a9a2c1deec0f669) Gustavo De Micheli)
-  **core**  limit the image size the library is willing to decode ([70270](https://github.com/nMoncho/faradn/commit/7027059a6fa6ff4) Gustavo De Micheli)
-  **core**  limit the image size the library is willing to raster ([d8710](https://github.com/nMoncho/faradn/commit/d8710ecf623ef08) Gustavo De Micheli)
-  **core**  add `ImagePolicy` as a way to configure the images that are accepted in print jobs ([7fe43](https://github.com/nMoncho/faradn/commit/7fe43da27b455b7) Gustavo De Micheli)
-  **cli**  add `profiles` command so users can see available profiles ([be809](https://github.com/nMoncho/faradn/commit/be8095ba7ba4ab5) Gustavo De Micheli)
-  **core**  implement `CapabilityAwareProfile` to properly use vendor-db ([e0f36](https://github.com/nMoncho/faradn/commit/e0f36e77db3d52c) Gustavo De Micheli)
-  **cli**  **ffi**  wire release version from BuildInfo, instead of hardcoded string ([485f2](https://github.com/nMoncho/faradn/commit/485f2e5bbef7161) Gustavo De Micheli)
-  **ffi**  add error contract, meaning different error codes depending on the failure ([c3322](https://github.com/nMoncho/faradn/commit/c3322e3985f60a4) Gustavo De Micheli)
-  **core**  add support for line-feed and cut with custom HTML element ([169e6](https://github.com/nMoncho/faradn/commit/169e6c343f8f7d6) Gustavo De Micheli)
-  **core**  add support for pulse, or cash drawer open, with `<cash-drawer>` custom HTML element ([7dd19](https://github.com/nMoncho/faradn/commit/7dd19facdfdc5a5) Gustavo De Micheli)
-  **core**  add support for per-element rotation ([edc51](https://github.com/nMoncho/faradn/commit/edc5155dcafad21) Gustavo De Micheli)
-  **core**  add support for highlighted text, or video reverse ([f619c](https://github.com/nMoncho/faradn/commit/f619c3455c05bf0) Gustavo De Micheli)
-  **core**  add support for `padding-top` and `padding-bottom` ([3ccbf](https://github.com/nMoncho/faradn/commit/3ccbf9ba5ab98f6) Gustavo De Micheli)
-  **core**  add support for margin, padding, and list item indentation ([36838](https://github.com/nMoncho/faradn/commit/368389333ce1e19) Gustavo De Micheli)
-  **core**  add support for Leader Line ([95fd2](https://github.com/nMoncho/faradn/commit/95fd2faa04558ae) Gustavo De Micheli)
-  **core**  allow mutiple blocks inside a bordered container ([3d171](https://github.com/nMoncho/faradn/commit/3d17148923a9c43) Gustavo De Micheli)
-  **core**  add left/right border support for block elements ([16aa9](https://github.com/nMoncho/faradn/commit/16aa9e9f1d94e1e) Gustavo De Micheli)
-  **core**  add border support for block elements, not only tables ([21656](https://github.com/nMoncho/faradn/commit/21656fe2fc934d8) Gustavo De Micheli)
-  **core**  add shorthand CSS `font` property ([7e84d](https://github.com/nMoncho/faradn/commit/7e84de9673eac49) Gustavo De Micheli)
-  **core**  add CSS `font-size` support ([a0400](https://github.com/nMoncho/faradn/commit/a0400904c96c650) Gustavo De Micheli)
-  **core**  add border support based on line glyphs, much like an ASCII table ([55707](https://github.com/nMoncho/faradn/commit/55707c51d25de83) Gustavo De Micheli)
-  **core**  add support for `line-height` property ([85209](https://github.com/nMoncho/faradn/commit/85209795368c8cd) Gustavo De Micheli)
-  **core**  implement rotation support for PageMode ([a79c8](https://github.com/nMoncho/faradn/commit/a79c8df25bef464) Gustavo De Micheli)
-  **core**  wire `BlockBuilder` with new utility methods ([772d6](https://github.com/nMoncho/faradn/commit/772d681d31b64c8) Gustavo De Micheli)
-  **core**  add DPI to blocks so we can convert from CSS units to print points ([f2de2](https://github.com/nMoncho/faradn/commit/f2de2a7933bba76) Gustavo De Micheli)
-  **core**  add PageMode HTML test job ([e9d18](https://github.com/nMoncho/faradn/commit/e9d185dc499c6d0) Gustavo De Micheli)
-  **core**  add `Canvas` rendering to `EscPosRenderer` ([99024](https://github.com/nMoncho/faradn/commit/9902464c867bf26) Gustavo De Micheli)
-  **core**  add `Canvas` abstraction to define the PageMode print area ([a36d6](https://github.com/nMoncho/faradn/commit/a36d63f3a21ba77) Gustavo De Micheli)
-  **core**  add `Placement` and `Placeable` abstractions to start working on Page Mode ([0f2fe](https://github.com/nMoncho/faradn/commit/0f2fe949cc0c7a7) Gustavo De Micheli)
-  add CSS styling file for previewing jobs ([01ebb](https://github.com/nMoncho/faradn/commit/01ebbf4753f589a) Gustavo De Micheli)
-  **core**  add `Direction`, `Word16`, and `PrintArea` abstractions ([9d0a2](https://github.com/nMoncho/faradn/commit/9d0a2ead101dbb0) Gustavo De Micheli)
-  **core**  add print position, and print direction parametric codes ([19532](https://github.com/nMoncho/faradn/commit/19532aaa4a386e0) Gustavo De Micheli)
-  **core**  add `SELECT_PAGE_MODE` and `SELECT_STANDARD_MODE` codes ([742dc](https://github.com/nMoncho/faradn/commit/742dca17b6069bf) Gustavo De Micheli)
-  **ffi**  add C, Python, and Rust examples ([b6538](https://github.com/nMoncho/faradn/commit/b6538fddc30ea15) Gustavo De Micheli)
-  **core**  add support for italics ([5bc05](https://github.com/nMoncho/faradn/commit/5bc05793a835742) Gustavo De Micheli)
-  **core**  support multiple fonts, coming from the capabitilies db, instead of from the hardcoded list ([6924b](https://github.com/nMoncho/faradn/commit/6924bb13c269180) Gustavo De Micheli)
-  **core**  support colspan and styles on tables ([eafd0](https://github.com/nMoncho/faradn/commit/eafd0f31fc4ac49) Gustavo De Micheli)
-  **core**  use only CodePages supported by the loaded profile, not the hardcoded list ([cfcc5](https://github.com/nMoncho/faradn/commit/cfcc552e959594c) Gustavo De Micheli)
-  **core**  load `PrinterProfile` from capabilities database ([a0133](https://github.com/nMoncho/faradn/commit/a013321fd80244d) Gustavo De Micheli)
-  **core**  generate capabilities configuration file from `github.com/receipt-print-hq/escpos-printer-db` with a script ([ba957](https://github.com/nMoncho/faradn/commit/ba957d44cc062a8) Gustavo De Micheli)
-  **core**  switch code pages on the fly during the print job ([59769](https://github.com/nMoncho/faradn/commit/59769844119df95) Gustavo De Micheli)
-  **core**  align table cell content ([798d4](https://github.com/nMoncho/faradn/commit/798d4e2ff02d267) Gustavo De Micheli)
-  **core**  preserve whitespace on `<pre>` elements ([4d4de](https://github.com/nMoncho/faradn/commit/4d4de74cd1bd0f0) Gustavo De Micheli)
-  **core**  render list markers on list elements ([6f066](https://github.com/nMoncho/faradn/commit/6f06697f693385c) Gustavo De Micheli)
-  **core**  add configuration options for barcodes and QR codes, such as height and HRI position ([46288](https://github.com/nMoncho/faradn/commit/462884158567ca4) Gustavo De Micheli)
-  **core**  send a status-read as pre-flight with timeout ([dfeef](https://github.com/nMoncho/faradn/commit/dfeefc1ff7f06fe) Gustavo De Micheli)
-  **cli**  list usb devices with a vendor name, if exists in `vendors.conf` ([20bda](https://github.com/nMoncho/faradn/commit/20bdad7b1c46c74) Gustavo De Micheli)
-  **core**  implement pure Java PNG decoder so images can be print with binary ([7a9c3](https://github.com/nMoncho/faradn/commit/7a9c3daa5d8563e) Gustavo De Micheli)
-  include Windows binary and FFI ([eb428](https://github.com/nMoncho/faradn/commit/eb4285335fa377d) Gustavo De Micheli)
-  add Foreign Function Interface (ffi) so the project can be embedded in other languages, such as C or Rust ([8fba2](https://github.com/nMoncho/faradn/commit/8fba2557393c4b9) Gustavo De Micheli)
-  build CLI with GraalVM ([47efb](https://github.com/nMoncho/faradn/commit/47efbf03e298729) Gustavo De Micheli)
-  add remaining blocks, like Image, QR, BarCodes ([897c0](https://github.com/nMoncho/faradn/commit/897c0f199f1af82) Gustavo De Micheli)
-  introduce `Transport` as a way to parametrize where the printer is located ([ca744](https://github.com/nMoncho/faradn/commit/ca744505223fbff) Gustavo De Micheli)
-  render e2e ticket on real hardware ([01b1f](https://github.com/nMoncho/faradn/commit/01b1f0c1d31d3ae) Gustavo De Micheli)
- 

### Bug Fixes

-  use Trivy proper version ([19ce1](https://github.com/nMoncho/faradn/commit/19ce1e48141bc04) Gustavo De Micheli)
-  move license plugin to pluginManagement to `license:format` and `license:check` actually work ([2f7c1](https://github.com/nMoncho/faradn/commit/2f7c10160115705) Gustavo De Micheli)
-  **core**  use GS code before ESC 3 to unsure proper line-height is set ([7414e](https://github.com/nMoncho/faradn/commit/7414e594fc1df02) Gustavo De Micheli)
-  properly set anchor point on PageMode ([03d64](https://github.com/nMoncho/faradn/commit/03d64e787cfd502) Gustavo De Micheli)
-  update release workflow ([ebeda](https://github.com/nMoncho/faradn/commit/ebeda7dd1babea7) Gustavo De Micheli)
-  **core**  fix failing tests on paper-cut at end of job ([8b304](https://github.com/nMoncho/faradn/commit/8b304ac2a9701ec) Gustavo De Micheli)
-  **core**  avoid double cutting if job already has a cut ([9d5b4](https://github.com/nMoncho/faradn/commit/9d5b431687aca27) Gustavo De Micheli)
-  **cli**  update GraalVM JNI and Resource configuration to actually be able to print with binary ([cc809](https://github.com/nMoncho/faradn/commit/cc809004ea6886e) Gustavo De Micheli)
-  update CI matrix to use the latest macOS intel-base variant `macos-26` ([79b02](https://github.com/nMoncho/faradn/commit/79b02b61417bddc) Gustavo De Micheli)


### Dependency updates

- bump softprops/action-gh-release from 2 to 3 ([a44a4](https://github.com/nMoncho/faradn/commit/a44a45c057ff468) dependabot[bot])
- bump actions/setup-java from 4 to 5 ([95a2b](https://github.com/nMoncho/faradn/commit/95a2b2fce1ef415) dependabot[bot])
- bump actions/checkout from 4 to 7 ([2099b](https://github.com/nMoncho/faradn/commit/2099b9447f9a3f8) dependabot[bot])
- bump the maven-dependencies group with 21 updates ([1d903](https://github.com/nMoncho/faradn/commit/1d903abb3fc0b31) dependabot[bot])

### Other changes

**Update README: architecture, supported HTML, honest status and roadmap**

* Documents the pipeline (HTML -&gt; DOM -&gt; Block IR -&gt; renderer), the
* supported tags/CSS/barcode markup with their printer effects, and a
* roadmap reflecting the plan (renderer, word wrap, character-grid
* tables, code pages, capability database, raster fallback).
* Also fixes the usage example: Printer.of(&quot;&quot;) never existed; the API
* is Printer.from(vendorId) returning Optional. Document.of -&gt; from too.

[d9941](https://github.com/nMoncho/faradn/commit/d9941701d713194) Gustavo De Micheli *2026-07-09 16:56:03*

**Cover Utils.findStyleValue and IR construction rules with unit tests**

* UtilsTest pins down the style-declaration parser: values with and
* without trailing semicolons, multiple declarations, whitespace
* tolerance, case-insensitive property names, whole-name matching
* (text-decoration vs text-decoration-line), and malformed declarations.
* BlockValidationTest asserts every IR record&#x27;s contract: rejected null
* or empty components, Paragraph&#x27;s defensive copy and immutable runs,
* Barcode&#x27;s symbology default, and Feed&#x27;s positive line count.=

[f2d79](https://github.com/nMoncho/faradn/commit/f2d795a1904f213) Gustavo De Micheli *2026-07-09 16:55:01*

**Test the IR: ComputedStyle resolution and HTML-to-Block translation**

* ComputedStyleTest covers tag defaults, CSS overrides (including
* font-weight: normal beating &lt;b&gt;), no-op italic identity, inheritance,
* size validation, and a regression for style attributes without a
* trailing semicolon.
* BlockBuilderTest asserts HTML-to-IR as plain objects: run splitting
* and merging, whitespace collapsing, block boundaries (&lt;br&gt;, &lt;hr&gt;,
* block tags), alignment inheritance, style unwinding, and barcode
* subtree consumption. The existing HTML resources (paragraph, ticket01,
* barcode) now serve as golden IR tests instead of println drivers.

[f1f71](https://github.com/nMoncho/faradn/commit/f1f716df6b51bf5) Gustavo De Micheli *2026-07-09 13:52:20*

**Add BlockBuilder: HTML to IR translation with an explicit style stack**

* Walks the body with a jsoup NodeVisitor and a Deque&lt;ComputedStyle&gt;:
* push the combined style entering each element, pop leaving it. This
* replaces the statesByElement map + parent-walking rollback of the old
* visitors with symmetric push/pop.
* - Block-level tags, &lt;br&gt;, &lt;hr&gt;, &lt;img&gt; and barcodes flush pending
* inline runs into a Paragraph; alignment comes from the first run&#x27;s
* computed style
* - Barcodes are recognized both as a &lt;bar-code&gt; element and as the
* bar-code BEM class used by the existing test resources, with the
* symbology taken from the symbology attribute or bar-code--&lt;name&gt;
* modifier class
* - Whitespace is normalized HTML-style: runs collapse to one space,
* spaces at block boundaries drop, and inter-run separators attach to
* the preceding run so styled runs don&#x27;t start with invisible styled
* characters
* - Consecutive runs with identical styles merge
* Document gains blocks(), the IR entry point renderers will consume.

[f00e0](https://github.com/nMoncho/faradn/commit/f00e091f7a089a7) Gustavo De Micheli *2026-07-09 13:50:51*

**Add the IR: sealed Block hierarchy with style-resolved text runs**

* The intermediate representation between the jsoup DOM and printer
* bytes. ESC/POS is line-oriented, so the IR is a flat List&lt;Block&gt;
* rather than a box-model tree:
* - Paragraph: inline TextRuns + block-level alignment
* - ImageBlock, Barcode (from the &lt;bar-code&gt; custom element)
* - Rule, Feed, Cut
* TextRun carries text plus a fully resolved ComputedStyle, so renderers
* only diff consecutive runs to emit minimal state changes; no style
* inheritance or events survive into the IR. Tables are a future Block
* subtype and should not require touching any of these.

[ba7b1](https://github.com/nMoncho/faradn/commit/ba7b16e33b16640) Gustavo De Micheli *2026-07-09 13:42:21*

**Add ComputedStyle: resolved, printer-realizable style model**

* First piece of the new IR. An immutable record holding only properties
* ESC/POS can realize: bold, underline, width/height multiples (1-8 per
* GS !), alignment, invert. No italic on purpose - most ESC/POS printers
* have no italic command; emulation is a per-printer renderer decision.
* process(Element) computes the style entered by an element: tag defaults
* (b/strong/u/h1-h3/center) first, inline CSS (font-weight,
* text-decoration, text-align) second, so CSS wins over tags. Returns
* &#x27;this&#x27; when nothing changes so transitions are detectable by identity.
* Supporting fixes:
* - Utils.findStyleValue: the regex required a literal &#x27;;&#x27; after the
* value ([;$] is a character class, not an end anchor), so
* style&#x3D;&quot;text-align: center&quot; without a trailing semicolon never
* matched. Replaced with a declaration split.
* - formatter-maven-plugin 2.18.0 -&gt; 2.23.0 and eclipse-formatter.xml
* compliance 10 -&gt; 17: the old JDT could not parse record syntax and
* mangled the source on every build.

[d609d](https://github.com/nMoncho/faradn/commit/d609ddcfa7a4c02) Gustavo De Micheli *2026-07-09 13:40:17*

**Clean baseline: target Java 17, drop abandoned document-package prototype**

* - Bump compiler from Java 11 to 17 (maven.compiler.release) to allow
* records and sealed interfaces for the upcoming IR
* - Remove document.NodeVisitor and document.PrintJobStates: the
* event-stream IR direction, abandoned mid-refactor (did not compile)
* - Remove ParagraphJobTest which depended on them; replaced by IR tests
* in a follow-up commit
* - Formatter-plugin normalization (tabs to spaces) picked up by the build
