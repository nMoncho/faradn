# Security Policy

## Supported versions

Farad'n is pre-1.0; only the latest release receives security fixes.

## Reporting a vulnerability

Please report suspected vulnerabilities **privately** through GitHub Security
Advisories — use the "Report a vulnerability" button on the repository's Security
tab — rather than opening a public issue. We aim to acknowledge reports within a few
days and to coordinate a fix and disclosure with you.

## Scope and hardening notes

Farad'n parses **untrusted HTML** into printer bytes and can open USB and network
connections. Two things are worth keeping in mind:

- Treat HTML from untrusted sources as any parser input; jsoup is kept up to date via
  Dependabot for this reason.
- The HTTP print server (`faradn serve`) has **no authentication** and prints whatever
  it receives. Do not expose it to untrusted networks — bind it to localhost or place
  an authenticating reverse proxy in front of it.
