"""Build a bilingual HTML report page from two Markdown files and the skill's template."""
import argparse
import html
from pathlib import Path

TEMPLATE = Path(__file__).resolve().parent.parent / "assets" / "template.html"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--en", required=True, help="English Markdown file")
    p.add_argument("--l2", required=True, help="Second-language Markdown file")
    p.add_argument("--l2-code", default="pl", help="Second language code, e.g. pl, de, cs")
    p.add_argument("--title", required=True, help="Page <title>: a 2-4 word name")
    p.add_argument("--eyebrow-en", default="Evidence review")
    p.add_argument("--eyebrow-l2", default="Evidence review")
    p.add_argument("--out", required=True, help="Output HTML path")
    a = p.parse_args()

    en = Path(a.en).read_text(encoding="utf-8")
    l2 = Path(a.l2).read_text(encoding="utf-8")
    for name, text in (("en", en), ("l2", l2)):
        if "</script" in text.lower():
            raise SystemExit(f"{name} Markdown contains '</script'; remove it before building")

    code = a.l2_code.lower()
    page = TEMPLATE.read_text(encoding="utf-8")
    for key, value in {
        "__TITLE__": html.escape(a.title),
        "__L2__": code,
        "__L2_LABEL__": code.upper(),
        "__EYEBROW_EN__": html.escape(a.eyebrow_en),
        "__EYEBROW_L2__": html.escape(a.eyebrow_l2),
        "__MD_EN__": en,
        "__MD_L2__": l2,
    }.items():
        page = page.replace(key, value)

    Path(a.out).write_text(page, encoding="utf-8")
    print(f"wrote {a.out} ({len(page)} bytes)")


if __name__ == "__main__":
    main()
