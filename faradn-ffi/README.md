# faradn-ffi — the Farad'n shared library

`faradn-ffi` compiles Farad'n's `HTML → ESC/POS bytes` rendering into a native
**C ABI shared library**, so any language with a C FFI (C, Rust, Go, Python, …)
can render receipts in-process. It exposes only the pure, transport-free
rendering path; the host language writes the returned bytes to the printer (a TCP
socket on port 9100, or the USB device).

## Building

```console
$ mvn -Pnative-shared -pl faradn-ffi -am -DskipTests package   # needs GraalVM
```

Produces, under `faradn-ffi/target/`:

- `libfaradn.{dylib,so,dll}` — the shared library. It bundles the SubstrateVM
  runtime, so it is a self-contained, several-MB file (no JRE needed on the
  target).
- `libfaradn.h`, `graal_isolate.h` — the C headers.

## API

```c
int  faradn_render(graal_isolatethread_t *thread,
                   char *html, char *profile,
                   char **out_buffer, long long *out_length);
void faradn_free(graal_isolatethread_t *thread, char *buffer);
```

`faradn_render` returns `0` on success and writes a freshly allocated buffer of
ESC/POS bytes — and its length — to the out-parameters; release it with
`faradn_free`. Strings are UTF-8 and null-terminated. A negative return means the
render failed.

## Isolate / threading contract

GraalVM code runs inside an *isolate*. Create one once with
`graal_create_isolate`, pass the returned `graal_isolatethread_t *` to every call,
and tear it down with `graal_tear_down_isolate`. Additional OS threads must attach
with `graal_attach_thread` before they call in.

## Examples

- [`examples/c`](examples/c) — render to stdout, pipe to the printer.
- [`examples/rust`](examples/rust) — render and send to a network printer on
  port 9100.
