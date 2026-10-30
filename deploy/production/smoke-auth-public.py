#!/usr/bin/env python3
"""Check anonymous auth boundaries without sending mail by default.

With no URL arguments, check the production user and admin portals on ports
3000 and 3001. Explicit URLs replace those defaults, so a local check never
silently includes production. Each portal gets a separate cookie jar and all
requests bypass environment proxy settings.

Examples:
    python3 smoke-auth-public.py
    python3 smoke-auth-public.py --base-url http://127.0.0.1:3000
    python3 smoke-auth-public.py --base-url http://localhost:3000 \\
        --admin-base-url http://localhost:3001

Only --email enables a real delivery: after all default checks pass, send once
through the first selected portal and immediately verify its resend cooldown.
Output never includes URLs, email addresses, cookies, tokens or response text.
"""

import argparse
import http.cookiejar
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


DEFAULT_USER_URL = "http://103.39.66.135:3000"
DEFAULT_ADMIN_URL = "http://103.39.66.135:3001"
INVALID_EMAIL = "invalid-email"


class ProbeFailure(Exception):
    """A failed probe whose sanitized result has already been printed."""


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, url):
        return None


def base_url(value):
    """Reject credentials and query strings without echoing supplied values."""
    try:
        parsed = urllib.parse.urlsplit(value)
        valid = (
            parsed.scheme in ("http", "https")
            and parsed.hostname
            and not parsed.username
            and not parsed.password
            and not parsed.query
            and not parsed.fragment
            and parsed.port != 0
        )
    except ValueError:
        valid = False
    if not valid:
        raise argparse.ArgumentTypeError(
            "base URL must be HTTP(S), without credentials, query or fragment"
        )
    return value.rstrip("/")


def email_address(value):
    if not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", value.strip()):
        raise argparse.ArgumentTypeError("email must have a valid address format")
    return value.strip()


def positive_timeout(value):
    try:
        timeout = float(value)
    except ValueError:
        timeout = 0
    if not 0 < timeout <= 120:
        raise argparse.ArgumentTypeError("timeout must be greater than 0 and at most 120")
    return timeout


class Portal:
    def __init__(self, url, role, label, timeout):
        self.url = url
        parsed = urllib.parse.urlsplit(url)
        self.origin = urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, "", "", ""))
        self.role = role
        self.label = label
        self.timeout = timeout
        self.csrf = None
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}),
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
            NoRedirects(),
        )

    def call(self, path, payload=None, csrf=False):
        headers = {"Accept": "application/json"}
        data = None
        if payload is not None:
            data = json.dumps(payload).encode("utf-8")
            headers.update({"Content-Type": "application/json", "Origin": self.origin})
        if csrf:
            headers[self.csrf["headerName"]] = self.csrf["token"]
        request = urllib.request.Request(self.url + path, headers=headers, data=data)
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                status, raw = response.status, response.read(65536)
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read(65536)
        except (OSError, urllib.error.URLError, ValueError):
            return None, None
        try:
            body = json.loads(raw)
        except (ValueError, UnicodeDecodeError):
            body = None
        return status, body if isinstance(body, dict) else None

    def check(self, name, status, body, expected_status, expected_data=None, check_data=False):
        code = body.get("code") if body else None
        data = body.get("data") if body else None
        passed = status == expected_status
        if check_data:
            passed = passed and code == 200 and data is expected_data
        # A CSRF response contains a secret object. Only boolean data is printable.
        print(json.dumps({
            "target": self.label,
            "check": name,
            "http": status,
            "code": code if isinstance(code, int) else None,
            "data": data if isinstance(data, bool) else None,
            "pass": passed,
        }), flush=True)
        if not passed:
            raise ProbeFailure()

    def bootstrap_csrf(self):
        status, body = self.call("/api/v1/auth/csrf")
        candidate = body.get("data") if body else None
        valid = (
            isinstance(candidate, dict)
            and isinstance(candidate.get("headerName"), str)
            and bool(candidate["headerName"])
            and isinstance(candidate.get("token"), str)
            and bool(candidate["token"])
            and body.get("code") == 200
        )
        # Invalid CSRF shape must fail even when the HTTP status is 200.
        self.check("csrf", status, body, 200 if valid else -1)
        self.csrf = candidate

    def default_checks(self):
        self.bootstrap_csrf()
        status, body = self.call(
            "/api/v1/auth/email-code", {"email": INVALID_EMAIL}, csrf=True
        )
        self.check("email-code-invalid", status, body, 200, False, check_data=True)

        # A fresh session proves rejection without either the CSRF header or cookie.
        anonymous = Portal(self.url, self.role, self.label, self.timeout)
        status, body = anonymous.call("/api/v1/auth/email-code", {"email": INVALID_EMAIL})
        self.check("email-code-no-csrf", status, body, 403)

        for name, path in (
            ("me-anonymous", "/api/v1/auth/me"),
            ("account-security-anonymous", "/api/v1/auth/account/security"),
            ("email-code-wrong-method", "/api/v1/auth/email-code"),
        ):
            status, body = self.call(path)
            self.check(name, status, body, 401)

        status, body = self.call("/api/v1/auth/password-reset/unknown", {}, csrf=True)
        self.check("password-reset-unknown", status, body, 401)
        status, body = self.call("/api/v1/auth/admin/unknown")
        self.check("admin-anonymous", status, body, 404 if self.role == "user" else 401)

    def delivery_checks(self, email):
        status, body = self.call("/api/v1/auth/email-code", {"email": email}, csrf=True)
        self.check("email-code-delivery", status, body, 200, True, check_data=True)
        status, body = self.call("/api/v1/auth/email-code", {"email": email}, csrf=True)
        self.check("email-code-cooldown", status, body, 200, False, check_data=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", action="append", type=base_url, metavar="URL",
                        help="user portal URL; repeat to check multiple user portals")
    parser.add_argument("--admin-base-url", action="append", type=base_url, metavar="URL",
                        help="admin portal URL; repeat to check multiple admin portals")
    parser.add_argument("--timeout", type=positive_timeout, default=20.0, metavar="SECONDS")
    parser.add_argument("--email", type=email_address, metavar="ADDRESS",
                        help="opt in to one real email followed by a cooldown check")
    args = parser.parse_args(argv)
    targets = [(url, "user") for url in args.base_url or []]
    targets.extend((url, "admin") for url in args.admin_base_url or [])
    if not targets:
        targets = [(DEFAULT_USER_URL, "user"), (DEFAULT_ADMIN_URL, "admin")]
    portals = [Portal(url, role, role + "-" + str(index + 1), args.timeout)
               for index, (url, role) in enumerate(targets)]
    try:
        for portal in portals:
            portal.default_checks()
        if args.email:
            portals[0].delivery_checks(args.email)
    except ProbeFailure:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
