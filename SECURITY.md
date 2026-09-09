# Security Policy

## Supported versions

Farad'n is pre-1.0; only the latest release receives security fixes.

## Reporting a vulnerability

Please report suspected vulnerabilities **privately** through GitHub Security
Advisories - use the "Report a vulnerability" button on the repository's Security
tab - rather than opening a public issue. We aim to acknowledge reports within a few
days and to coordinate a fix and disclosure with you.

## Scope and hardening notes

Farad'n parses **untrusted HTML** into printer bytes and can open USB and network
connections. The following controls apply:

- **Image fetching (SSRF).** By default the renderer decodes only `data:` image
  URIs and fetches nothing, so untrusted HTML cannot make the process request a URL
  of the attacker's choosing (an internal host, a cloud metadata endpoint, a local
  `file:`). Fetching `file:` or `http(s)` images is opt-in
  (`Image.policy(ImagePolicy)`, or `--allow-remote-images` on the CLI), and when
  enabled it is limited to those schemes, does not follow redirects, and is bounded
  by connect/read timeouts and a maximum response size. `faradn print` allows local
  `file:` images (the HTML is your own file); `faradn serve` stays `data:`-only
  unless you pass `--allow-remote-images`.
- **Image bombs.** Decoded image dimensions are capped before any pixel buffer is
  allocated, and PNG decompression and chunk sizes are bounded, so a crafted image
  cannot exhaust memory.
- **The HTTP print server (`faradn serve`) has no authentication** and prints
  whatever it receives. It binds to loopback (`127.0.0.1`) by default; expose it on
  other interfaces only with `--bind`, and then only behind an authenticating
  reverse proxy. It caps the request body (5&nbsp;MB), bounds request/response time
  to limit slow-client denial of service, and returns generic error bodies
  (details are logged server-side, not returned to the client).
- Treat HTML from untrusted sources as any parser input; jsoup is kept up to date
  via Dependabot for this reason. Library embedders that render untrusted HTML
  should keep the default `Image.policy(ImagePolicy.DATA_URIS_ONLY)`.
