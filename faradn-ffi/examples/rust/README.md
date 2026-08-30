# Rust example

Renders a receipt to ESC/POS bytes through the shared library and sends them to
a network printer on the ESC/POS raw port (9100).

Build the shared library first (from the repo root):

```console
$ mvn -Pnative-shared -pl faradn-ffi -am -DskipTests package
```

The [`build.rs`](build.rs) links against `../../target/libfaradn` and bakes that
directory into the binary's rpath, so no `LD_LIBRARY_PATH` / `DYLD_LIBRARY_PATH`
is needed at runtime. Then, from this directory:

```console
$ cargo run -- 192.168.1.50:9100
```

The argument is the printer's `host:port`; it defaults to `192.168.1.50:9100`
when omitted. The program creates one GraalVM isolate, renders, frees the
returned buffer with `faradn_free`, tears the isolate down, and writes the bytes
to the printer socket.

The `extern "C"` block declares the four entry points by hand for clarity; you
can instead generate them from `../../target/libfaradn.h` with `bindgen`.
