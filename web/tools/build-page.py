#!/usr/bin/env python3
"""
build-page.py — build the PharmTrack landing page from the Timmly design system.

The design system itself is never edited in place: tools/design-system/ holds a
pristine copy of the skill's CSS, motion module and page skeleton, and this
script regenerates assets/css, assets/js/landing.js and index.html from it on
every run. Two passes:

  1. rebrand   — swap the design system's warm tokens for PharmTrack's teal
                  (base.css tokens, the six accent families, the hero sky).
  2. content   — fill every {{PLACEHOLDER}} and rewrite the sample data that
                  ships as literal text inside the visual mockups.

Run from web/:  python3 tools/build-page.py
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "design-system")
CSS_SRC = [os.path.join(SRC, n) for n in ("base.css", "shell.css", "sections.css")]
CSS_OUT = [os.path.join(ROOT, "assets", "css", n) for n in ("base.css", "shell.css", "sections.css")]
HTML = os.path.join(ROOT, "index.html")
JS = os.path.join(ROOT, "assets", "js", "landing.js")

# --------------------------------------------------------------------------
# 1. Rebrand — warm neutrals and the terracotta accent become PharmTrack teal.
#    Values come from ios/Sources/App/Theme.swift (the 1.0 app palette).
# --------------------------------------------------------------------------
REBRAND = [
    # ink / neutrals: warm near-black -> teal-tinted slate
    ("#1918146b", "#1b27256b"),
    ("#1918141a", "#1b27251a"),
    ("#191814", "#1b2725"),
    ("#3a3833", "#3c4c47"),
    ("#3d3a34", "#35443f"),
    ("#0e0d0b", "#0d1513"),
    ("#57544c", "#4b5d58"),
    ("#8a857a", "#5c6d68"),
    ("#cdc8be", "#cbd7d4"),
    # surfaces
    ("#fcfbf8", "#f4f8f7"),
    ("#efece5", "#eaf1ef"),
    ("#e6e2d9", "#e2eae8"),
    ("#f4f2ec", "#d9ecea"),
    ("#edebe6", "#e6eeec"),
    # the accent itself
    ("#f24e1e", "#0f766e"),
    ("#b8350f", "#0b5e57"),
    ("#ffe6dc", "#d9ecea"),
    # warm artwork -> teal artwork
    ("#ffcfb4", "#cfe7e3"),
    ("#ffe2d0", "#dcedea"),
    ("#ffeee3", "#eaf4f2"),
    ("#ffece0", "#e4f1ee"),
    ("#fff1e8", "#eef7f5"),
    ("#ffe2d2", "#dcedea"),
    ("#ffe2d4", "#dfeceb"),
    ("#78321459", "#0f766e59"),
    ("#78321473", "#0f766e73"),
    ("#78321440", "#0f766e40"),
    # mint family -> emerald, so it no longer reads as the teal accent
    ("#34c39a", "#10b981"),
    ("#d9f4ea", "#d8f5e9"),
    ("#0b6e52", "#0a6a4d"),
    # stone family -> cool grey
    ("#9c968a", "#7c8b86"),
    ("#5b574f", "#4b5d58"),
]

# Targeted edits that a global replace would get wrong.
TARGETED_CSS = [
    # the hero "sun" disc: a peach sun becomes a teal glow
    (".s-hx-sun {\n  background: #ff7a45;", ".s-hx-sun {\n  background: #57b3a8;"),
    ("0 0 0 70px #ff7a4529,", "0 0 0 70px #57b3a829,"),
    ("0 0 0 150px #ff7a4514;", "0 0 0 150px #57b3a814;"),
    # the live notification chip fills with the brand colour
    ("background: #ff7a45;\n    transform: scale(1.1);", "background: #0f766e;\n    transform: scale(1.1);"),
    ("background: #ff7a45;\n    transform: scale(1);", "background: #0f766e;\n    transform: scale(1);"),
]


CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


def build_og_card():
    """Render the social share card.

    The card is an HTML page (tools/og-card.html) rendered by headless Chrome
    at 1200 x 630, so it uses the same typefaces as the site itself. If Chrome
    is not installed, the committed card is left in place.
    """
    out = os.path.join(ROOT, "assets", "img", "og.png")
    source = os.path.join(ROOT, "tools", "og-card.html")
    if not os.path.exists(CHROME):
        print("og: Chrome not found — keeping the committed share card")
        return
    try:
        subprocess.run(
            [
                CHROME,
                "--headless",
                "--disable-gpu",
                "--hide-scrollbars",
                "--force-device-scale-factor=1",
                "--window-size=1200,630",
                "--screenshot=" + out,
                "file://" + source,
            ],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    except subprocess.CalledProcessError:
        sys.exit("og: headless Chrome failed to render the share card")
    print("og: share card rendered")


def build_screenshots():
    """Render the responsive webp derivatives of the four app screenshots.

    The source screenshots live in tools/screenshots/ at 1170 x 2532 — the
    3x size of the 390 x 844 device frame the mockups use. ImageMagick
    produces the 400 / 800 / 1170 webp candidates the page references.
    """
    src = os.path.join(ROOT, "tools", "screenshots")
    out = os.path.join(ROOT, "assets", "img")
    if not os.path.isdir(src):
        sys.exit("missing screenshot source directory: %s" % src)
    shots = sorted(f for f in os.listdir(src) if f.endswith(".png"))
    if len(shots) != 4:
        sys.exit("expected 4 source screenshots, found %d" % len(shots))
    for name in shots:
        base = name[:-4]
        for width in (400, 800, 1170):
            target = os.path.join(out, "%s-%d.webp" % (base, width))
            subprocess.run(
                ["magick", os.path.join(src, name), "-resize", "%dx" % width,
                 "-quality", "84", "-define", "webp:method=6", target],
                check=True,
            )
    print("screenshots: %d shots rendered at 400 / 800 / 1170 webp" % len(shots))


CSS_EXTRA = """
/* --- PharmTrack extensions ------------------------------------------------
   The two store download buttons. The design system ships no store badge
   artwork, so they are drawn here — token-driven, like everything else. */
.s-store-btn {
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  border: 1.5px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  color: var(--ink);
  font-size: 16px;
  font-weight: 700;
  text-decoration: none;
  display: inline-flex;
  transition:
    transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 0.2s;
}
.s-store-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}
.s-store-btn.dark {
  background: var(--ink);
  border-color: var(--ink);
  color: var(--on-ink);
}
.s-store-btn .ic {
  fill: currentColor;
}
"""


def rebrand_css():
    pending = list(TARGETED_CSS)
    for src, out in zip(CSS_SRC, CSS_OUT):
        with open(src, encoding="utf-8") as fh:
            css = fh.read()
        for old, new in REBRAND:
            css = css.replace(old, new)
        for edit in list(pending):
            old, new = edit
            if old in css:
                css = css.replace(old, new, 1)
                pending.remove(edit)
        with open(out, "w", encoding="utf-8") as fh:
            fh.write(css + CSS_EXTRA)
    if pending:
        sys.exit("targeted CSS edit not found:\n%s" % pending[0][0])
    print("rebrand: 3 stylesheets retuned to PharmTrack teal")


# --------------------------------------------------------------------------
# 2. Content
# --------------------------------------------------------------------------
TOKENS = {
    # identity
    "APP_NAME": "PharmTrack",
    "APP_TAGLINE": "Your private medication and health companion",
    "APP_DESCRIPTION": (
        "PharmTrack keeps your medications, consultations, appointments and vital signs in one place. "
        "It works fully offline and your health data never leaves your device."
    ),
    "APP_CATEGORY": "MedicalApplication",
    "CANONICAL_URL": "https://pharmtrack.app",
    # contact
    "SUPPORT_EMAIL": "support@pharmtrack.app",
    "SUPPORT_URL": "/support.html",
    "PRIVACY_URL": "/privacy.html",
    "TERMS_URL": "/terms.html",
    "DELETE_ACCOUNT_URL": "/delete-account.html",
    # nav
    "NAV_BADGE": "Free",
    # stores
    "APP_STORE_URL": "https://apps.apple.com/us/app/pharmtrack/id6760744803",
    "GOOGLE_PLAY_URL": "https://play.google.com/store/apps/details?id=com.tunnxo.medtrack",
    # hero
    "HERO_HEADLINE": "Own your doses, one routine at a time.",
    "HERO_LEDE": (
        "PharmTrack is your medication and health companion. Every dose, visit, appointment and reading "
        "in one private app that works without a signal — and without an account."
    ),
    "PRIMARY_CTA": "Download free",
    "SECONDARY_CTA": "See the features",
    "HERO_CARD_1_TITLE": "Dose taken",
    "HERO_CARD_1_META": "Vitamin D3 · 9:00 AM",
    "HERO_CARD_2_TITLE": "Next dose",
    "HERO_CARD_2_META": "Lisinopril · 8:00 PM",
    "HERO_CARD_3_TITLE": "5-day dose streak",
    "HERO_CARD_4_VALUE": "71%",
    "HERO_CARD_4_LABEL": "of doses on time",
    # upcoming
    "UP_HEADLINE": (
        "From the first dose to the last, PharmTrack lines up what is coming next "
        "so nothing slips by unseen."
    ),
    # features
    "FEATURES_HEADLINE": "Everything your health day needs, in one place",
    "FEATURES_LEDE": (
        "Medication, reminders, appointments, records and vitals — read at a glance, "
        "stored on your own phone, and never uploaded anywhere."
    ),
    "FEATURE_1_TITLE": "Never miss a dose",
    "FEATURE_1_BODY": (
        "Every medication carries its dosage, instructions and time of day. The day's schedule "
        "groups itself into morning, afternoon and evening, and each dose is ticked off as you take it."
    ),
    "FEATURE_2_TITLE": "Reminders that actually reach you",
    "FEATURE_2_BODY": (
        "Choose the moment you want to be nudged — at the time of the dose, or five minutes, "
        "an hour or a day before. Appointments get the same treatment, and a low-stock warning "
        "arrives before you run out."
    ),
    "FEATURE_3_TITLE": "Your records, kept safe",
    "FEATURE_3_BODY": (
        "Every consultation is stored with the doctor, the specialty and the note you wrote "
        "afterwards. Nothing is uploaded, nothing is profiled, and there is no analytics SDK in the app."
    ),
    "FEATURE_4_TITLE": "See the trend, not just the number",
    "FEATURE_4_BODY": (
        "Log blood pressure, weight and blood sugar and watch the line move across the weeks. "
        "PharmTrack shows the direction of the change, so a quiet shift never hides inside a single reading."
    ),
    # pricing
    "PRICING_HEADLINE": "Free, and it stays free",
    "PRICING_LEDE": (
        "PharmTrack costs nothing on either store. No subscription, no in-app purchase, no ads "
        "— and no account to create before you use it."
    ),
    "YEARLY_PRICE": "0",    "MARQUEE_1": "Own your doses",
    "MARQUEE_2": "Never miss a dose",
    "MARQUEE_3": "Private by design",
    "MARQUEE_4": "Works offline",
    # faq
    "FAQ_1_QUESTION": "Is PharmTrack really free?",
    "FAQ_PRICING_ANSWER": (
        "Yes, completely. There is no subscription, no in-app purchase and no advertising — "
        "and no feature is held back. Download it, install it, and everything is unlocked."
    ),
    "FAQ_2_QUESTION": "Do I need an account to use it?",
    "FAQ_CANCEL_ANSWER": (
        "No. There is no sign-up and no login. You install the app, and your records stay on "
        "your device. Uninstalling removes them for good — there is no cloud copy to restore from."
    ),
    "FAQ_3_QUESTION": "Where is my health data stored?",
    "FAQ_3_ANSWER": (
        "On your phone, in the app's own sandboxed storage. It never travels to a server, "
        "because there is no server. The only copy that exists is the one on your device."
    ),
    "FAQ_4_QUESTION": "Does it work without internet?",
    "FAQ_4_ANSWER": (
        "Yes — everything does. The app never needs a network connection, so it works in a "
        "basement, on a plane, or wherever your signal does not."
    ),
    "FAQ_5_QUESTION": "Can it track more than medication?",
    "FAQ_5_ANSWER": (
        "Much more. PharmTrack also keeps consultations with their notes and treatments, "
        "upcoming appointments, and readings for blood pressure, weight and blood sugar."
    ),
    "FAQ_6_QUESTION": "Can PharmTrack replace my doctor?",
    "FAQ_6_ANSWER": (
        "No, and it is not meant to. PharmTrack is a record-keeping and reminder tool, not a "
        "medical device. It does not diagnose or treat anything — always talk to your doctor "
        "before you change how you take a medication."
    ),
    "FAQ_7_QUESTION": "Do you collect or share my data?",
    "FAQ_7_ANSWER": (
        "No. No analytics, no advertising identifiers, no third-party tracking of any kind."
    ),
    # accessibility
    "SHOT_1_ALT": (
        "PharmTrack home screen showing today's plan: two of seven doses taken, "
        "Vitamin D3 as the next dose, and three upcoming appointments."
    ),
    "SHOT_2_ALT": (
        "PharmTrack medications screen showing the day's schedule grouped into morning, "
        "afternoon and evening, with Ibuprofen flagged as low stock."
    ),
    "SHOT_3_ALT": (
        "PharmTrack consultations screen listing six active treatments with the doctor, "
        "specialty and date of the last visit."
    ),
    "SHOT_4_ALT": (
        "PharmTrack vitals screen showing a blood pressure trend of 130 over 81 millimetres "
        "of mercury, down 1.5 percent over eight weeks."
    ),
    "SHOT_5_ALT": (
        "A lock screen notification from PharmTrack reminding you to take a dose."
    ),
    "LOCKSCREEN_NOTIFICATION": "Vitamin D3 · 1000IU — take with food",
    "LOCKSCREEN_NOTIFICATION_2": "Next dose at 8:00 PM · Lisinopril 5mg",
    # footer
    "FOOTER_TAGLINE": (
        "Your personal medication and health management assistant. "
        "Works offline, stores everything on your device, and asks for nothing in return."
    ),
}

# --- store buttons ---------------------------------------------------------
# The design system ships no store badge artwork, so the two download buttons
# are drawn here. Each button names only its own store.
APPLE_GLYPH = (
    "M17.05 20.28c-.98.95-2.05.8-3.08.35-1.09-.46-2.09-.48-3.24 0-1.44.62-2.2.44-3.06-.35"
    "C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.8 1.18-.24 2.31-.93 3.57-.84"
    "1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.38 5.98.48 7.13-.57 1.5-1.31 2.99-2.54 4.09l.01-.01z"
    "M12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z"
)
PLAY_GLYPH = "M4.8 2.6 17.4 12 4.8 21.4V2.6z"

STORE_BUTTONS = """<div class="s-get-badges">
                <a class="s-store-btn dark" href="{{APP_STORE_URL}}" target="_blank" rel="noopener">
                  <svg class="ic" width="22" height="22" viewBox="0 0 24 24" aria-hidden="true"><path d="%s"></path></svg>
                  <span>Download on the App Store</span>
                </a>
                <a class="s-store-btn" href="{{GOOGLE_PLAY_URL}}" target="_blank" rel="noopener">
                  <svg class="ic" width="22" height="22" viewBox="0 0 24 24" aria-hidden="true"><path d="%s"></path></svg>
                  <span>Get it on Google Play</span>
                </a>
              </div>""" % (APPLE_GLYPH, PLAY_GLYPH)

# --------------------------------------------------------------------------
# Sample data that ships as literal text inside the visual mockups.
# Every key is (search, replacement). Each search must appear exactly once.
# --------------------------------------------------------------------------
LITERALS = [
    # --- the hero word rotator: the first word is written into the markup ---
    (
        '<span class="s-hx-w" data-hero-word><i class="s-hw">day,</i></span>',
        '<span class="s-hx-w" data-hero-word><i class="s-hw">doses,</i></span>',
    ),
    # --- the pricing section: real store links ---
    (
        '<div class="s-get-badges">\n'
        '                <span class="s-gnav-soon">{{STORE_BADGE}}</span>\n'
        '              </div>',
        STORE_BUTTONS,
    ),
    # --- footer: a download link ---
    (
        '<li><a href="/#faq">FAQ</a></li>',
        '<li><a href="/#faq">FAQ</a></li>'
        '<li><a href="/#pricing">Download</a></li>',
    ),
    # --- hero glass card 2: the next dose ---
    (
        '<svg class="ic" width="18" height="18"><use href="#i-dumbbell"></use></svg></span\n'
        '              ><span class="s-glass-t"><b>{{HERO_CARD_2_TITLE}}</b><span>{{HERO_CARD_2_META}}</span></span\n'
        '              ><span class="s-mini-now">Now</span>',
        '<svg class="ic" width="18" height="18"><use href="#i-clock"></use></svg></span\n'
        '              ><span class="s-glass-t"><b>{{HERO_CARD_2_TITLE}}</b><span>{{HERO_CARD_2_META}}</span></span\n'
        '              ><span class="s-mini-now">Soon</span>',
    ),
    # --- the upcoming event stack ---
    (
        '<span class="s-ev-when">Tomorrow</span><span class="s-ev-date">Thu 16 Oct</span>',
        '<span class="s-ev-when">Today</span><span class="s-ev-date">Tue 17 Nov</span>',
    ),
    (
        '<span class="s-ev-t"><b>Dentist</b><span>4:30 – 5:00 pm</span></span',
        '<span class="s-ev-t"><b>Dr. Michael Smith</b><span>2:30 pm</span></span',
    ),
    (
        '<span class="s-ev-when">Friday</span><span class="s-ev-date">Fri 17 Oct</span>',
        '<span class="s-ev-when">Today</span><span class="s-ev-date">Tue 17 Nov</span>',
    ),
    (
        '<span class="s-ev-t"><b>Flight to Dubai</b><span>7:10 am</span></span',
        '<span class="s-ev-t"><b>Ibuprofen · 400mg</b><span>Every 6 hours</span></span',
    ),
    (
        '<span class="s-ev-when">Saturday</span><span class="s-ev-date">Sat 18 Oct</span>',
        '<span class="s-ev-when">Tomorrow</span><span class="s-ev-date">Wed 18 Nov</span>',
    ),
    (
        '<svg class="ic" width="22" height="22"><use href="#i-dumbbell"></use></svg></span\n'
        '                ><span class="s-ev-t"><b>Yoga class</b><span>9:00 – 10:00 am</span></span',
        '<svg class="ic" width="22" height="22"><use href="#i-trophy"></use></svg></span\n'
        '                ><span class="s-ev-t"><b>6-day dose streak</b><span>Keep it going</span></span',
    ),
    (
        '<span class="s-ev-when">Sunday</span><span class="s-ev-date">Sun 19 Oct</span>',
        '<span class="s-ev-when">Thursday</span><span class="s-ev-date">Thu 19 Nov</span>',
    ),
    (
        '<span class="s-ev-t"><b>Birthday dinner</b><span>8:00 pm</span></span',
        '<span class="s-ev-t"><b>Dr. Elena Ruiz</b><span>10:00 am</span></span',
    ),
    (
        '<span class="s-ev-when">Next week</span><span class="s-ev-date">Mon 20 Oct</span>',
        '<span class="s-ev-when">Next week</span><span class="s-ev-date">Fri 20 Nov</span>',
    ),
    (
        '<span class="s-ev-t"><b>Team review</b><span>11:00 am</span></span',
        '<span class="s-ev-t"><b>Annual checkup</b><span>5:15 pm</span></span',
    ),
    (
        '<span class="s-ev-when">Next week</span><span class="s-ev-date">Tue 21 Oct</span>',
        '<span class="s-ev-when">Next week</span><span class="s-ev-date">Sat 21 Nov</span>',
    ),
    (
        '<span class="s-ev-t"><b>Haircut</b><span>6:30 pm</span></span',
        '<span class="s-ev-t"><b>Lisinopril · 5mg</b><span>8:00 pm</span></span',
    ),
    # --- the floating tags over the upcoming stack ---
    (
        '<span class="s-up-tag t1 floaty" aria-hidden="true">Tomorrow</span',
        '<span class="s-up-tag t1 floaty" aria-hidden="true">Today</span',
    ),
    # --- feature row 1: the dose timeline cards ---
    (
        '<span class="s-ev-t"><b>Wake up</b><span>7:30 am</span></span',
        '<span class="s-ev-t"><b>Vitamin D3</b><span>8:00 am</span></span',
    ),
    (
        '<svg class="ic" width="20" height="20"><use href="#i-dumbbell"></use></svg></span\n'
        '                      ><span class="s-ev-t"><b>{{HERO_CARD_2_TITLE}}</b><span>{{HERO_CARD_2_META}}</span></span\n'
        '                      ><span class="s-kn-now"><span class="s-kn-dot"></span>Now</span>',
        '<svg class="ic" width="20" height="20"><use href="#i-list-checks"></use></svg></span\n'
        '                      ><span class="s-ev-t"><b>{{HERO_CARD_2_TITLE}}</b><span>{{HERO_CARD_2_META}}</span></span\n'
        '                      ><span class="s-kn-now"><span class="s-kn-dot"></span>Next</span>',
    ),
    (
        '<b class="s-kn-free">2h free</b>',
        '<b class="s-kn-free">As needed</b>',
    ),
    (
        '<span class="s-ev-t"><b>Dentist</b><span>Task · 4:30 pm</span></span',
        '<span class="s-ev-t"><b>Dr. Michael Smith</b><span>Visit · 2:30 pm</span></span',
    ),
    # --- feature row 2: the lock screen ---
    (
        '<span class="s-nt-date">Tuesday, 20 October</span',
        '<span class="s-nt-date">Tuesday, 17 November</span',
    ),
    (
        '<span class="s-nt-time">8:00</span>',
        '<span class="s-nt-time">9:00</span>',
    ),
    # --- feature row 4: the insight cards ---
    (
        '<div class="s-in-card c1"><b>79%</b> of tasks done this month</div>',
        '<div class="s-in-card c1"><b>130/81</b> mmHg — latest blood pressure</div>',
    ),
    (
        '<div class="s-in-card c2"><span class="s-in-ic">🔥</span>Longest streak: Wake up, 12 days</div>',
        '<div class="s-in-card c2"><span class="s-in-ic">🔥</span>Longest dose streak: 12 days</div>',
    ),
    (
        '<div class="s-in-card c3"><span class="s-in-ic">🔥</span>Stand-up is on a roll, 6 days</div>',
        '<div class="s-in-card c3"><span class="s-in-ic">🔥</span>Morning routine is on a roll, 6 days</div>',
    ),
    # --- the upcoming headline: rewritten as a PharmTrack sentence ---
    (
        '<span aria-hidden="true"><span class="w" style="--i: 0">From</span> <span class="w" style="--i: 1">the</span> '
        '<span class="w" style="--i: 2">first</span><span class="s-up-ic c-sky" aria-hidden="true">'
        '<svg class="ic" width="20" height="20"><use href="#i-calendar-check"></use></svg></span> '
        '<span class="w" style="--i: 3">thing</span> <span class="w" style="--i: 4">to</span> '
        '<span class="w" style="--i: 5">the</span> <span class="w" style="--i: 6">last,</span>'
        '<span class="s-up-ic c-tangerine" aria-hidden="true">'
        '<svg class="ic" width="20" height="20"><use href="#i-sparkle-fill"></use></svg></span> '
        '<span class="w" style="--i: 7">{{APP_NAME}}</span> <span class="w" style="--i: 8">lines</span> '
        '<span class="w" style="--i: 9">up</span> <span class="w" style="--i: 10">what’s</span> '
        '<span class="w" style="--i: 11">coming</span> <span class="w" style="--i: 12">next</span>'
        '<span class="s-up-ic c-sun" aria-hidden="true">'
        '<svg class="ic" width="20" height="20"><use href="#i-bell"></use></svg></span> '
        '<span class="w" style="--i: 13">so</span> <span class="w" style="--i: 14">nothing</span> '
        '<span class="w" style="--i: 15">sneaks</span> <span class="w" style="--i: 16">up</span> '
        '<span class="w" style="--i: 17">on</span> <span class="w" style="--i: 18">you.</span></span></span',
        '<span aria-hidden="true"><span class="w" style="--i: 0">From</span> <span class="w" style="--i: 1">the</span> '
        '<span class="w" style="--i: 2">first</span><span class="s-up-ic c-sky" aria-hidden="true">'
        '<svg class="ic" width="20" height="20"><use href="#i-calendar-check"></use></svg></span> '
        '<span class="w" style="--i: 3">dose</span> <span class="w" style="--i: 4">to</span> '
        '<span class="w" style="--i: 5">the</span> <span class="w" style="--i: 6">last,</span>'
        '<span class="s-up-ic c-tangerine" aria-hidden="true">'
        '<svg class="ic" width="20" height="20"><use href="#i-sparkle-fill"></use></svg></span> '
        '<span class="w" style="--i: 7">{{APP_NAME}}</span> <span class="w" style="--i: 8">lines</span> '
        '<span class="w" style="--i: 9">up</span> <span class="w" style="--i: 10">what’s</span> '
        '<span class="w" style="--i: 11">coming</span> <span class="w" style="--i: 12">next</span>'
        '<span class="s-up-ic c-sun" aria-hidden="true">'
        '<svg class="ic" width="20" height="20"><use href="#i-bell"></use></svg></span> '
        '<span class="w" style="--i: 13">so</span> <span class="w" style="--i: 14">nothing</span> '
        '<span class="w" style="--i: 15">slips</span> <span class="w" style="--i: 16">by</span> '
        '<span class="w" style="--i: 17">unseen.</span></span></span',
    ),
    # --- the JSON-LD offer: the app is free, so there are no paid plans ---
    (
        '"offers": [\n'
        '                {\n'
        '                  "@type": "Offer",\n'
        '                  "name": "Yearly plan, after a free week",\n'
        '                  "price": "{{YEARLY_PRICE}}",\n'
        '                  "priceCurrency": "USD"\n'
        '                },\n'
        '                {\n'
        '                  "@type": "Offer",\n'
        '                  "name": "Monthly plan, after a free week",\n'
        '                  "price": "{{MONTHLY_PRICE}}",\n'
        '                  "priceCurrency": "USD"\n'
        '                }\n'
        '              ]',
        '"offers": {\n'
        '                "@type": "Offer",\n'
        '                "name": "Free",\n'
        '                "price": "{{YEARLY_PRICE}}",\n'
        '                "priceCurrency": "USD"\n'
        '              }',
    ),
]

# --- the brand mark ---------------------------------------------------------
# The PharmTrack mark is the app icon itself: the green-to-teal flower tile.
# It is a bitmap rather than inline SVG because that gradient is the brand, and
# recolouring it would make the site stop looking like the app.
MARK_PATTERN = re.compile(
    r'<svg width="(\d+)" height="\1" viewBox="0 0 512 512" aria-hidden="true"'
    r' style="flex-shrink: 0">.*?</svg\s*>',
    re.S,
)


def apply_mark(html):
    def repl(match):
        size = int(match.group(1))
        radius = round(size * 0.2237, 1)  # the iOS icon corner radius
        return (
            '<img src="assets/img/mark.png" width="%d" height="%d" alt="" '
            'style="border-radius: %spx; flex-shrink: 0" />' % (size, size, radius)
        )

    html, count = MARK_PATTERN.subn(repl, html)
    if count != 4:
        sys.exit("expected 4 brand marks, rewrote %d" % count)
    print("content: %d brand marks swapped for the app icon" % count)
    return html


def fix_srcsets(html):
    """Point the phone mockups at the real responsive screenshots.

    The skeleton ships hashed webp candidates from the reference build; the
    PharmTrack page uses its own 400 / 800 / 1170 webp renders instead.
    """
    pattern = re.compile(
        r'src="assets/img/(shot-[a-z]+)\.png"\s*srcset="assets/img/shot-[a-z]+\.png".*?1170w\s*"',
        re.S,
    )

    def repl(match):
        name = match.group(1)
        return (
            'src="assets/img/%s-1170.webp"\n'
            '                    srcset="assets/img/%s-400.webp 400w,\n'
            '                      assets/img/%s-800.webp 800w,\n'
            '                      assets/img/%s-1170.webp 1170w\n'
            '                    "' % (name, name, name, name)
        )

    html, count = pattern.subn(repl, html)
    if count != 4:
        sys.exit("expected 4 screenshot srcsets, rewrote %d" % count)
    print("content: %d responsive srcsets rewired" % count)
    return html


# The footer illustration and the upcoming swirl are inline SVG in the
# skeleton, so their peach linework is retuned here alongside the CSS.
HTML_REBRAND = [
    ("#f0c4ad", "#9ec9c3"),
    ("#ffad8c", "#6fb0a8"),
    ("#d6cfc8", "#c6d6d2"),
    ("#f5dccf", "#dcedea"),
    ("#e2b9a2", "#b6d7d1"),
    ("#ffc9b3", "#c6e3de"),
    ("#ffb59a", "#a8d2cb"),
    ("rgba(255,196,168,.55)", "rgba(255,255,255,.5)"),
    ("rgba(255,220,203,.5)", "rgba(255,255,255,.5)"),
    ("rgba(255,210,189,.5)", "rgba(255,255,255,.5)"),
    ("#191814", "#1b2725"),
    ("#fcfbf8", "#f4f8f7"),
]


def apply_content():
    with open(os.path.join(SRC, "skeleton.html"), encoding="utf-8") as fh:
        html = fh.read()

    html = apply_mark(html)

    for old, new in HTML_REBRAND:
        html = html.replace(old, new)

    # the favicon is the app icon too
    html = html.replace(
        '<link rel="icon" href="assets/img/favicon.svg" type="image/svg+xml" />',
        '<link rel="icon" href="assets/img/favicon.png" type="image/png" />',
    )

    # sample data
    for old, new in LITERALS:
        if html.count(old) != 1:
            sys.exit("literal not found exactly once (%d hits):\n%s" % (html.count(old), old[:120]))
        html = html.replace(old, new, 1)

    html = fix_srcsets(html)

    # tokens
    for key, value in TOKENS.items():
        token = "{{%s}}" % key
        if token not in html:
            sys.exit("token not found: " + token)
        html = html.replace(token, value)

    left = re.findall(r"\{\{[A-Z0-9_]+\}\}", html)
    if left:
        sys.exit("unfilled tokens remain: %s" % sorted(set(left)))

    # the hero word rotator lives in the script
    with open(os.path.join(SRC, "landing.js"), encoding="utf-8") as fh:
        script = fh.read()
    old_words = 'const HERO_WORDS = ["day", "habits", "tasks", "routines", "goals"];'
    new_words = 'const HERO_WORDS = ["doses", "meds", "vitals", "visits", "records"];'
    if old_words not in script:
        sys.exit("hero word list not found")
    script = script.replace(old_words, new_words, 1)
    with open(JS, "w", encoding="utf-8") as fh:
        fh.write(script)

    with open(HTML, "w", encoding="utf-8") as fh:
        fh.write(html)
    print("content: %d tokens filled, %d sample-data literals rewritten" % (len(TOKENS), len(LITERALS)))


if __name__ == "__main__":
    build_screenshots()
    build_og_card()
    rebrand_css()
    apply_content()
