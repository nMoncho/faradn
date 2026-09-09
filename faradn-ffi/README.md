# faradn-ffi - the Farad'n shared library

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

- `libfaradn.{dylib,so,dll}` - the shared library. It bundles the SubstrateVM
  runtime, so it is a self-contained, several-MB file (no JRE needed on the
  target).
- `libfaradn.h`, `graal_isolate.h` - the C headers.
- On **Windows**, `libfaradn.lib` (the import library) is produced too, for
  compile-time linking; runtime consumers can also `LoadLibrary` the DLL directly.

## API

```c
int   faradn_render(graal_isolatethread_t *thread,
                    char *html, char *profile,
                    char **out_buffer, long long *out_length);
char *faradn_last_error(graal_isolatethread_t *thread);
char *faradn_version(graal_isolatethread_t *thread);
void  faradn_free(graal_isolatethread_t *thread, char *buffer);
```

`faradn_render` returns `0` (`FARADN_OK`) on success and writes a freshly
allocated buffer of ESC/POS bytes - and its length - to the out-parameters;
release it with `faradn_free`. On failure it returns one of these codes, which
are a **stable part of the C ABI**:

| Code | Name                          | Meaning                                                    |
|------|-------------------------------|------------------------------------------------------------|
| `0`  | `FARADN_OK`                   | success                                                    |
| `-1` | `FARADN_ERR_UNKNOWN`          | an unexpected or unclassified failure                      |
| `-2` | `FARADN_ERR_INVALID_ARGUMENT` | a null or invalid argument (null HTML or out-parameter)    |
| `-3` | `FARADN_ERR_UNKNOWN_PROFILE`  | the named profile is not in the capability database        |
| `-4` | `FARADN_ERR_RENDER`           | the HTML could not be parsed or rendered                   |
| `-5` | `FARADN_ERR_OUT_OF_MEMORY`    | a memory allocation failed                                 |

After a failure, `faradn_last_error` returns a human-readable message for the
calling thread (or `NULL` after a success); it is valid until that thread's next
call. `faradn_version` returns the library version. Both, like the render buffer,
are freshly allocated C strings you release with `faradn_free`. All strings are
UTF-8 and null-terminated.

## Isolate / threading contract

GraalVM code runs inside an *isolate*. Create one once with
`graal_create_isolate`, pass the returned `graal_isolatethread_t *` to every call,
and tear it down with `graal_tear_down_isolate`. Additional OS threads must attach
with `graal_attach_thread` before they call in.

## Examples

Runnable integrations, one per language, under [`examples`](examples) (see its
[README](examples/README.md) for an overview):

- [`examples/c`](examples/c) - render to stdout, pipe to the printer.
- [`examples/rust`](examples/rust) - render and send to a network printer on
  port 9100.
- [`examples/python`](examples/python) - render with `ctypes` (no install),
  to stdout or straight to a network printer.
