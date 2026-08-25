# C example

Renders a receipt to ESC/POS bytes through the shared library.

Build the shared library first (from the repo root):

```console
$ mvn -Pnative-shared -pl faradn-ffi -am -DskipTests package
```

Then, from this directory, compile and run against it:

```console
$ cc render.c -I../../target -L../../target -lfaradn -o render

# the runtime linker needs libfaradn; on macOS:
$ DYLD_LIBRARY_PATH=../../target ./render | nc 192.168.1.50 9100
# on Linux, use LD_LIBRARY_PATH instead of DYLD_LIBRARY_PATH
```

`render` writes the ESC/POS bytes to stdout; pipe them to the printer's raw port
9100 (as above) or redirect to a file.
