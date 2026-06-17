"""Render mock_requests.log into a screenshot-friendly summary for the blog.

Usage: python format_log.py <challenge|nochallenge> "<title>" <outfile>
The response status is derived from the mock's deterministic rule
(challenge + no creds -> 401, otherwise -> 200).
"""
import json
import sys

mode, title, outfile = sys.argv[1], sys.argv[2], sys.argv[3]
rows = [l for l in open("mock_requests.log", encoding="utf-8") if l.strip()]

out = [title, "=" * len(title), ""]
for i, raw in enumerate(rows, 1):
    r = json.loads(raw)
    ap = r["authorization_present"]
    status = "401  (WWW-Authenticate: Basic)" if (mode == "challenge" and not ap) else "200"
    out.append(f"{i}. {r['method']} {r['path']}")
    out.append(f"     auth_present = {ap}")
    if ap:
        out.append(f"     Authorization: {r.get('authorization')}")
        out.append(f"     decoded:       {r.get('authorization_decoded')}")
    else:
        out.append("     (no Authorization header sent)")
    out.append(f"     -> mock responded {status}")
    out.append("")

text = "\n".join(out)
open(outfile, "w", encoding="utf-8").write(text)
print(text)
