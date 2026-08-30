#!/usr/bin/env python3
"""Render a receipt to ESC/POS bytes through the Farad'n shared library.

Uses only the standard library (ctypes), so there is nothing to install: the
native `libfaradn` shared library is loaded at runtime. By default the raw
ESC/POS bytes are written to stdout; pass --printer HOST:PORT to send them
straight to a network printer's raw port instead.

Examples:
    # write raw bytes to a file
    python3 render.py > receipt.bin

    # pipe to a network printer (port 9100 is the ESC/POS raw default)
    python3 render.py | nc 192.168.1.50 9100

    # or let the script open the socket for you
    python3 render.py --printer 192.168.1.50:9100
"""

import argparse
import ctypes
import os
import socket
import sys
from ctypes import POINTER, byref, c_char_p, c_int, c_longlong, c_void_p

DEFAULT_HTML = "<h1>Receipt</h1><p>Total: <b>10,00</b></p>"
DEFAULT_PROFILE = "tm-t88v"


def library_candidates():
    """Yield plausible libfaradn paths across platforms.

    FARADN_LIB, when set, wins and is tried verbatim. Otherwise we look in the
    Maven build output (../../target) next to this file, using the platform's
    shared-library extension.
    """
    override = os.environ.get("FARADN_LIB")
    if override:
        yield override
        return

    here = os.path.dirname(os.path.abspath(__file__))
    target = os.path.join(here, "..", "..", "target")
    if sys.platform == "darwin":
        names = ["libfaradn.dylib"]
    elif sys.platform == "win32":
        names = ["libfaradn.dll", "faradn.dll"]
    else:
        names = ["libfaradn.so"]
    for name in names:
        yield os.path.join(target, name)


def load_library():
    tried = []
    for path in library_candidates():
        tried.append(path)
        if os.path.exists(path):
            return ctypes.CDLL(path)
    sys.exit(
        "could not find libfaradn. Build it first with:\n"
        "  mvn -Pnative-shared -pl faradn-ffi -am -DskipTests package\n"
        "or point FARADN_LIB at the shared library.\n"
        "Looked in:\n  " + "\n  ".join(tried)
    )


def bind(lib):
    """Declare the C signatures so ctypes marshals arguments correctly."""
    lib.graal_create_isolate.argtypes = [c_void_p, POINTER(c_void_p), POINTER(c_void_p)]
    lib.graal_create_isolate.restype = c_int

    lib.graal_tear_down_isolate.argtypes = [c_void_p]
    lib.graal_tear_down_isolate.restype = c_int

    lib.faradn_render.argtypes = [
        c_void_p,          # isolate thread
        c_char_p,          # html (UTF-8, null-terminated)
        c_char_p,          # profile
        POINTER(c_void_p),  # out_buffer
        POINTER(c_longlong),  # out_length
    ]
    lib.faradn_render.restype = c_int

    lib.faradn_free.argtypes = [c_void_p, c_void_p]
    lib.faradn_free.restype = None


def render(lib, html, profile):
    """Return the ESC/POS bytes for `html` rendered with `profile`."""
    isolate = c_void_p()
    thread = c_void_p()
    if lib.graal_create_isolate(None, byref(isolate), byref(thread)) != 0:
        raise RuntimeError("failed to create GraalVM isolate")

    try:
        out_buffer = c_void_p()
        out_length = c_longlong(0)
        rc = lib.faradn_render(
            thread,
            html.encode("utf-8"),
            profile.encode("utf-8"),
            byref(out_buffer),
            byref(out_length),
        )
        if rc != 0:
            raise RuntimeError(f"faradn_render failed: {rc}")

        # string_at copies the bytes into a Python bytes object, so the buffer
        # is safe to free immediately afterwards.
        data = ctypes.string_at(out_buffer, out_length.value)
        lib.faradn_free(thread, out_buffer)
        return data
    finally:
        lib.graal_tear_down_isolate(thread)


def send_to_printer(target, data):
    host, _, port = target.partition(":")
    with socket.create_connection((host, int(port or "9100"))) as sock:
        sock.sendall(data)


def main():
    parser = argparse.ArgumentParser(description="Render a receipt via libfaradn.")
    parser.add_argument("--html", default=DEFAULT_HTML, help="receipt HTML")
    parser.add_argument("--profile", default=DEFAULT_PROFILE, help="printer profile")
    parser.add_argument(
        "--printer",
        metavar="HOST[:PORT]",
        help="send the bytes to this network printer (raw port, default 9100) "
        "instead of writing them to stdout",
    )
    args = parser.parse_args()

    lib = load_library()
    bind(lib)
    data = render(lib, args.html, args.profile)

    if args.printer:
        send_to_printer(args.printer, data)
        print(f"sent {len(data)} bytes to {args.printer}", file=sys.stderr)
    else:
        sys.stdout.buffer.write(data)


if __name__ == "__main__":
    main()
