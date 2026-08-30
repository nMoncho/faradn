# faradn-ffi examples

Three small applications that render a receipt by calling the Farad'n shared
library through its C ABI, one per language. They all do the same thing (create
a GraalVM isolate, call `faradn_render`, release the buffer with `faradn_free`,
tear the isolate down) and differ only in what they do with the ESC/POS bytes.

## Build the shared library once

Every example links or loads `libfaradn`, so build it first from the repo root
(needs GraalVM with `native-image`):

```console
$ mvn -Pnative-shared -pl faradn-ffi -am -DskipTests package
```

That writes `libfaradn.{dylib,so,dll}` and the C headers into
`faradn-ffi/target/`.

## The examples

| Language           | Links how                      | Output                | Run                      |
|--------------------|--------------------------------|-----------------------|--------------------------|
| [`c`](c)           | compile-time (`-lfaradn`)      | raw bytes to stdout   | `make run`               |
| [`rust`](rust)     | compile-time (`build.rs`)      | TCP to printer :9100  | `cargo run -- host:9100` |
| [`python`](python) | runtime (`ctypes`, no install) | stdout or `--printer` | `python3 render.py`      |

Each subdirectory has its own README with the exact commands. The bytes an
example prints to stdout are raw ESC/POS; redirect them to a file or pipe them to
a printer's raw port, for example `... | nc 192.168.1.50 9100`.

See the module [README](../README.md) for the C API and the isolate / threading
contract.
