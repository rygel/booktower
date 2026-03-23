# Security Policy

## Supported Versions

Only the latest release receives security updates.

| Version | Supported |
|---------|-----------|
| Latest  | Yes       |
| Older   | No        |

## Reporting a Vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

Instead, use [GitHub Private Security Advisories](https://github.com/rygel/runary/security/advisories/new) to report vulnerabilities. This keeps the details private until a fix is available.

### What to include

- Description of the vulnerability
- Steps to reproduce
- Impact assessment (what an attacker could do)
- Affected version(s)

### What to expect

- **Acknowledgement** within 7 days
- We will work on a fix and coordinate a disclosure timeline with you
- Credit will be given in the security advisory (unless you prefer to remain anonymous)

### Scope

The following are in scope:
- Runary application code (Kotlin/Java backend, JTE templates, JavaScript frontend)
- Docker image configuration
- Default configuration security

The following are out of scope:
- Dependencies with their own security policies (report upstream)
- Issues requiring physical access to the server
- Social engineering

## Responsible Disclosure

We follow responsible disclosure practices. We ask that you:
- Give us reasonable time to fix the issue before public disclosure
- Do not access or modify other users' data
- Do not degrade the service for other users
