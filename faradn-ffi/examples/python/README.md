# Python example

Renders a receipt to ESC/POS bytes through the shared library using `ctypes`,
so there is nothing to `pip install`. The library is loaded at runtime.

Build the shared library first (from the repo root):

```console
$ mvn -Pnative-shared -pl faradn-ffi -am -DskipTests package
```

Then, from this directory, run it. By default `render.py` finds
`libfaradn` under `../../target` and writes the raw ESC/POS bytes to stdout:

```console
# save to a file
$ python3 render.py > receipt.bin

# pipe to a network printer's raw port 9100
$ python3 render.py | nc 192.168.1.50 9100

# or let the script open the socket for you
$ python3 render.py --printer 192.168.1.50:9100
```

Override the input with `--html` and `--profile`, and point `FARADN_LIB` at the
shared library if it lives somewhere other than `../../target`:

```console
$ FARADN_LIB=/path/to/libfaradn.so \
    python3 render.py --profile tm-t88v --html '<h1>Hi</h1>' > receipt.bin
```

The script creates one GraalVM isolate, renders, frees the returned buffer with
`faradn_free`, and tears the isolate down again, mirroring the isolate and
threading contract in the module [README](../../README.md).
