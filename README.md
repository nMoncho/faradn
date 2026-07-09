# Farad'n

Faran'd is an ESC/POS implementation for Java. The goal of this project is to be
a one stop shop for your printing needs when dealing with ESC/POS printers.

## Usage

The current version supports defining a `Document` by using an HTML document,
which is later converted to ESC/POS commands. HTML is a widely used and understood
format, and can easily be templated with tools such as [Mustache](https://mustache.github.io/).

After having a valid document parsed, you can send your document as a print job:

```java
String html = "<html>...";
Document doc = Document.of(html);

Printer printer = Printer.of("");
printer.print(doc);
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
- Italic text
- Left, center, right, and justify text alignment
- 


## Contribution Guidelines

Please see [CONTRIBUTING.md](CONTRIBUTING.md).


## Why Farad'n?

_Dune_ is one of my favourite books, there Farad'n is the grandson of Padishah
Emperor Shaddam IV, and he's the royal scripe by occupation.
