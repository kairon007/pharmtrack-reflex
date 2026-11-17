#!/usr/bin/env python3
"""
build-legal.py — build the PharmTrack prose pages.

Privacy, Terms, EULA, Support and Delete-your-data are static pages that use
the design system's `.s-page` prose layout. The legal copy is carried over
verbatim from the pages the React site shipped, so the only things that change
here are the markup and the dead links.

Run from web/:  python3 tools/build-legal.py
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUPPORT_EMAIL = "support@pharmtrack.app"
PRIVACY_EMAIL = "privacy@pharmtrack.app"
EULA_EMAIL = "creationsapk@gmail.com"
SITE = "https://pharmtrack.app"

# Each page is (slug, title, lede, [blocks], foot_html)
# A block is ("h2"|"p"|"ul"|"note", text) — text may contain simple <b>/<a> tags.
PAGES = [
    (
        "privacy",
        "Privacy Policy",
        "Last updated: March 2024",
        [
            ("h2", "1. Introduction"),
            ("p", "PharmTrack (&quot;we&quot;, &quot;us&quot;, &quot;our&quot;, or &quot;Company&quot;) is committed to protecting your privacy. This Privacy Policy explains how we handle information in our application and on our website."),
            ("h2", "2. Data We Collect"),
            ("p", "PharmTrack is designed with privacy as a core principle. We collect minimal data:"),
            ("ul", "<li><strong>Health Data You Enter:</strong> Medications, consultations, appointments, vital signs — stored entirely on your device</li>"
                   "<li><strong>Device Information:</strong> Only basic info needed for app functionality (OS version, app version)</li>"
                   "<li><strong>Analytics:</strong> None. We do not track your usage or behavior</li>"
                   "<li><strong>Identifiers:</strong> We do not collect any personally identifiable information</li>"),
            ("h2", "3. How We Use Your Data"),
            ("p", "All health data you enter into PharmTrack is stored exclusively on your device. We:"),
            ("ul", "<li>Do NOT upload your data to any server</li>"
                   "<li>Do NOT share your data with third parties</li>"
                   "<li>Do NOT sell your data</li>"
                   "<li>Do NOT use your data for advertising</li>"
                   "<li>Do NOT analyze your health information</li>"),
            ("h2", "4. Data Storage"),
            ("p", "Your health information is stored locally on your device using encrypted storage. We have no access to this data and cannot retrieve it."),
            ("ul", "<li><strong>iOS:</strong> Data stored in app's encrypted sandbox</li>"
                   "<li><strong>Android:</strong> Data stored in app's encrypted sandbox</li>"
                   "<li><strong>Backup:</strong> Only backed up if you enable device backup</li>"),
            ("h2", "5. Data Security"),
            ("p", "We implement strong security measures:"),
            ("ul", "<li>Encrypted local storage on your device</li>"
                   "<li>No network transmission of health data</li>"
                   "<li>Regular security updates</li>"
                   "<li>No accounts or logins required</li>"),
            ("h2", "6. Third-Party Services"),
            ("p", "PharmTrack does NOT use any third-party analytics, advertising, or tracking services. We do not integrate with:"),
            ("ul", "<li>Google Analytics or similar</li>"
                   "<li>Ad networks</li>"
                   "<li>Health data platforms</li>"
                   "<li>Cloud storage services</li>"),
            ("h2", "7. Children's Privacy"),
            ("p", "PharmTrack does not knowingly collect information from children under 13. Parents may use the app to manage their child's medications under parental supervision."),
            ("h2", "8. Data Deletion"),
            ("p", "You have complete control over your data:"),
            ("ul", "<li>Delete individual entries anytime from within the app</li>"
                   "<li>Clear all data by uninstalling the app</li>"
                   "<li>No cloud copies mean no data recovery needed</li>"),
            ("h2", "9. Website Cookies"),
            ("p", "Our website does not use cookies for tracking. We may use technical cookies only for essential functionality."),
            ("h2", "10. Changes to This Policy"),
            ("p", "We may update this policy occasionally. We will notify users of significant changes through the app or website."),
            ("h2", "11. Contact"),
            ("p", "For any privacy question, write to <a href=\"mailto:%s\">%s</a>." % (PRIVACY_EMAIL, PRIVACY_EMAIL)),
        ],
        None,
    ),
    (
        "terms",
        "Terms of Service",
        "Last updated: March 2024",
        [
            ("h2", "1. Acceptance of Terms"),
            ("p", "By downloading, installing, and using PharmTrack (&quot;App&quot;), you agree to be bound by these Terms of Service. If you do not agree to these terms, you may not use the App."),
            ("h2", "2. License Grant"),
            ("p", "PharmTrack grants you a limited, non-exclusive, non-transferable license to use the App for personal health management purposes on devices you own or control."),
            ("h2", "3. Restrictions"),
            ("p", "You agree NOT to:"),
            ("ul", "<li>Modify, reverse engineer, or decompile the App</li>"
                   "<li>Use the App for any illegal purposes</li>"
                   "<li>Attempt to gain unauthorized access to the App or its data</li>"
                   "<li>Distribute or reproduce the App without permission</li>"
                   "<li>Use the App to harm yourself or others</li>"
                   "<li>Violate any applicable laws or regulations</li>"),
            ("h2", "4. User Responsibility"),
            ("p", "You are solely responsible for:"),
            ("ul", "<li>The accuracy of health information you enter</li>"
                   "<li>Maintaining the confidentiality of your device</li>"
                   "<li>Backing up your data if you wish to retain it</li>"
                   "<li>Following medical advice from licensed healthcare providers</li>"
                   "<li>Not using PharmTrack as a substitute for professional medical advice</li>"),
            ("h2", "5. Disclaimer of Medical Advice"),
            ("note", "<strong>IMPORTANT:</strong> PharmTrack is a personal health management tool, NOT a medical device or service. It:"
                     "<ul>"
                     "<li>Does not provide medical diagnosis or treatment</li>"
                     "<li>Does not replace professional medical advice</li>"
                     "<li>Is not intended to diagnose, treat, cure, or prevent disease</li>"
                     "<li>Should not be used as a substitute for consulting healthcare providers</li>"
                     "</ul>"
                     "Always consult with qualified healthcare professionals for medical advice, diagnosis, or treatment."),
            ("h2", "6. No Warranty"),
            ("p", "PharmTrack is provided &quot;AS IS&quot; without warranty of any kind, express or implied. We do not warrant that the App will be uninterrupted, error-free, or meet your expectations."),
            ("h2", "7. Limitation of Liability"),
            ("p", "To the maximum extent permitted by law, PharmTrack and its developers shall not be liable for:"),
            ("ul", "<li>Loss of data or inability to access your information</li>"
                   "<li>Any indirect, incidental, or consequential damages</li>"
                   "<li>Medical consequences or health-related damages</li>"
                   "<li>Third-party claims or damages</li>"),
            ("h2", "8. Data Loss"),
            ("p", "Since all data is stored locally on your device:"),
            ("ul", "<li>Uninstalling the App will delete all local data</li>"
                   "<li>PharmTrack cannot recover deleted information</li>"
                   "<li>Device loss or damage means data loss</li>"
                   "<li>You are responsible for backing up important data</li>"),
            ("h2", "9. Intellectual Property Rights"),
            ("p", "PharmTrack and all its content are protected by copyright and other intellectual property laws. You may not use PharmTrack's name, logo, or branding without permission."),
            ("h2", "10. Changes to the App"),
            ("p", "We may update, modify, or discontinue PharmTrack at any time. Material changes will be communicated through the App or this website."),
            ("h2", "11. Termination"),
            ("p", "Your right to use PharmTrack is terminated automatically upon:"),
            ("ul", "<li>Violation of these Terms of Service</li>"
                   "<li>Uninstallation of the App from your device</li>"
                   "<li>Discontinuation of the App by PharmTrack</li>"),
            ("h2", "12. Third-Party Services"),
            ("p", "PharmTrack may use third-party libraries and services. These are subject to their respective licenses and terms."),
            ("h2", "13. Export Controls"),
            ("p", "PharmTrack may be subject to export controls. You agree not to export or re-export the App in violation of applicable laws."),
            ("h2", "14. Governing Law"),
            ("p", "These Terms of Service are governed by applicable laws. Any disputes shall be resolved in accordance with those laws."),
            ("h2", "15. Severability"),
            ("p", "If any provision of these terms is found unenforceable, the remaining provisions shall remain in effect."),
            ("h2", "16. Entire Agreement"),
            ("p", "These Terms of Service, along with our Privacy Policy and EULA, constitute the entire agreement regarding PharmTrack."),
            ("h2", "17. Contact Information"),
            ("p", "For questions about these terms:"),
            ("ul", "<li>Email: <a href=\"mailto:%s\">%s</a></li>"
                   "<li>Website: <a href=\"%s\">pharmtrack.app</a></li>" % (SUPPORT_EMAIL, SUPPORT_EMAIL, SITE)),
            ("h2", "18. Updates to Terms"),
            ("p", "We may update these terms at any time. Continued use of PharmTrack after changes constitutes acceptance of the new terms."),
        ],
        None,
    ),
    (
        "eula",
        "End User License Agreement (EULA)",
        "Last updated: March 2024",
        [
            ("note", "<strong>READ THIS AGREEMENT CAREFULLY.</strong> By installing and using PharmTrack, you acknowledge that you have read, understood, and agree to be bound by the terms of this End User License Agreement."),
            ("h2", "1. Grant of License"),
            ("p", "PharmTrack grants you a limited, revocable, non-exclusive, non-transferable license to download, install, and use PharmTrack on devices you own or control for personal, non-commercial health management purposes."),
            ("h2", "2. Scope of License"),
            ("p", "This license permits you to:"),
            ("ul", "<li>Install PharmTrack on one or more devices you own</li>"
                   "<li>Use PharmTrack for personal health management</li>"
                   "<li>Create and store health-related information</li>"
                   "<li>Access your data offline</li>"),
            ("h2", "3. Restrictions on Use"),
            ("p", "You may NOT:"),
            ("ul", "<li>Copy, modify, or create derivative works of PharmTrack</li>"
                   "<li>Reverse engineer, decompile, or attempt to derive source code</li>"
                   "<li>Rent, lease, lend, or transfer the App</li>"
                   "<li>Use PharmTrack for commercial purposes</li>"
                   "<li>Remove or obscure any proprietary notices</li>"
                   "<li>Use PharmTrack in any unlawful manner</li>"
                   "<li>Distribute, sell, or sublicense PharmTrack</li>"),
            ("h2", "4. Ownership"),
            ("p", "PharmTrack remains the exclusive property of PharmTrack developers. This license does not grant you ownership of the App or any part thereof. You own only the data you create within the App."),
            ("h2", "5. Your Data Ownership"),
            ("p", "You retain full ownership of all health data you enter into PharmTrack:"),
            ("ul", "<li>You own your medications, consultations, appointments, and vitals data</li>"
                   "<li>You can delete any or all data at any time</li>"
                   "<li>You can export data (if feature is provided)</li>"
                   "<li>PharmTrack does not claim ownership of your health information</li>"),
            ("h2", "6. Updates and Changes"),
            ("p", "PharmTrack may automatically update to fix bugs, add features, or improve security. You authorize these updates. Updates may not be compatible with older devices."),
            ("h2", "7. Third-Party Content"),
            ("p", "PharmTrack may contain links to third-party websites or services. We are not responsible for their content, policies, or practices. Use at your own risk."),
            ("h2", "8. Limitations on Your Use"),
            ("p", "PharmTrack is for personal use only. You may not:"),
            ("ul", "<li>Use it for medical diagnosis or treatment (see disclaimer below)</li>"
                   "<li>Share login credentials if a login system existed</li>"
                   "<li>Use it to store information on behalf of others for resale</li>"
                   "<li>Use it to harm, harass, or threaten anyone</li>"),
            ("h2", "9. Medical Disclaimer"),
            ("note", "<strong>PharmTrack is NOT a medical device.</strong> It is a personal health management tool only. It does not:"
                     "<ul>"
                     "<li>Diagnose, treat, cure, or prevent any disease</li>"
                     "<li>Provide medical advice or recommendations</li>"
                     "<li>Replace consultation with healthcare professionals</li>"
                     "<li>Have any clinical validity or medical claims</li>"
                     "</ul>"
                     "Always consult qualified healthcare providers for medical concerns."),
            ("h2", "10. No Warranty"),
            ("p", "PharmTrack is provided &quot;AS IS&quot; without any warranty, express or implied, including but not limited to:"),
            ("ul", "<li>Warranties of merchantability or fitness for a particular purpose</li>"
                   "<li>Warranty that the App is error-free or uninterrupted</li>"
                   "<li>Warranty of security or data protection</li>"),
            ("h2", "11. Limitation of Liability"),
            ("p", "To the maximum extent permitted by law, in no event shall PharmTrack be liable for:"),
            ("ul", "<li>Loss of data, files, or information</li>"
                   "<li>Loss of profits or revenue</li>"
                   "<li>Indirect, incidental, special, or consequential damages</li>"
                   "<li>Any health-related damages or medical consequences</li>"
                   "<li>Damages arising from your use of the App</li>"),
            ("h2", "12. Data Security and Loss"),
            ("p", "You acknowledge that:"),
            ("ul", "<li>All data is stored locally on your device</li>"
                   "<li>PharmTrack cannot recover deleted data</li>"
                   "<li>Device loss or damage may result in data loss</li>"
                   "<li>You are responsible for backing up important data</li>"
                   "<li>Uninstalling the App will delete all local data</li>"),
            ("h2", "13. License Termination"),
            ("p", "This license is automatically terminated when:"),
            ("ul", "<li>You violate any term of this EULA</li>"
                   "<li>You uninstall PharmTrack</li>"
                   "<li>PharmTrack is discontinued</li>"
                   "<li>You exceed the scope of this license</li>"),
            ("p", "Upon termination, you must cease all use of PharmTrack and delete it from your devices."),
            ("h2", "14. Compliance with Laws"),
            ("p", "You agree to use PharmTrack in compliance with all applicable laws and regulations in your jurisdiction."),
            ("h2", "15. No Support or Updates"),
            ("p", "PharmTrack is provided without guarantee of technical support, maintenance, or future updates. Support may be provided at our discretion."),
            ("h2", "16. Governing Law and Jurisdiction"),
            ("p", "This EULA is governed by applicable laws. Any disputes shall be subject to those laws."),
            ("h2", "17. Entire Agreement"),
            ("p", "This EULA, along with the Privacy Policy and Terms of Service, constitutes the entire agreement between you and PharmTrack regarding use of the App."),
            ("h2", "18. Severability"),
            ("p", "If any provision of this EULA is found invalid or unenforceable, the remaining provisions shall continue in full effect."),
            ("h2", "19. No Waiver"),
            ("p", "Failure to enforce any provision of this EULA does not constitute a waiver of that provision or any other provision."),
            ("h2", "20. Changes to This Agreement"),
            ("p", "PharmTrack may update this EULA at any time. Continued use of the App after changes constitutes acceptance of the new terms."),
            ("h2", "21. Contact Us"),
            ("p", "For questions about this EULA:"),
            ("ul", "<li>Email: <a href=\"mailto:%s\">%s</a></li>"
                   "<li>Website: <a href=\"%s\">pharmtrack.app</a></li>" % (EULA_EMAIL, EULA_EMAIL, SITE)),
            ("h2", "Acknowledgment"),
            ("p", "By downloading and installing PharmTrack, you acknowledge that:"),
            ("ul", "<li>You have read and understood this EULA</li>"
                   "<li>You agree to be bound by all terms herein</li>"
                   "<li>You understand PharmTrack is not a medical device</li>"
                   "<li>You accept all limitations and disclaimers</li>"),
        ],
        None,
    ),
    (
        "support",
        "Support",
        "We read every message.",
        [
            ("p", "Something not behaving the way it should, or a question about how a feature works? Write to us and we will get back to you."),
            ("h2", "Common questions"),
            ("ul", "<li><strong>My data looks wrong after an update.</strong> Uninstalling removes everything; reinstalling restores nothing, because there is no cloud copy. If you had a device backup before the update, you can restore from it.</li>"
                   "<li><strong>Notifications are not arriving.</strong> Check the app's notification settings, then your device settings for PharmTrack, and make sure Focus or Do Not Disturb is not silencing them.</li>"
                   "<li><strong>A medication is missing from the schedule.</strong> Deactivated medications are hidden from today's list — check the Inventory tab.</li>"
                   "<li><strong>I want to start over.</strong> Clear all data from within the app's settings, or uninstall it. Both are permanent.</li>"),
            ("h2", "Report a bug"),
            ("p", "Please include your device model, the operating system version, the app version and what you expected to happen instead. That is usually enough to reproduce it."),
            ("h2", "Write to us"),
            ("p", "Email <a href=\"mailto:%s\">%s</a> and we will reply as soon as we can." % (SUPPORT_EMAIL, SUPPORT_EMAIL)),
        ],
        None,
    ),
    (
        "delete-account",
        "Delete your data",
        "There is no account to delete — and that is on purpose.",
        [
            ("p", "PharmTrack has no sign-up, no login and no server. There is no account for us to close, because there is no copy of your health data anywhere but your own device."),
            ("h2", "Delete everything"),
            ("p", "Open the app's settings and choose the option that clears all data. That removes every medication, consultation, appointment and reading from the device, and cancels the reminders that go with them."),
            ("ul", "<li><strong>Uninstalling works too.</strong> Removing the app deletes the local file along with it.</li>"
                   "<li><strong>Deleting one item is enough.</strong> Every record can be removed on its own, from the list it belongs to.</li>"),
            ("h2", "What we can and cannot do"),
            ("p", "Because we never receive your data, there is nothing for us to delete on our side. If you want a copy of what you entered before you clear it, take a screenshot or a device backup first — once it is gone, it is gone."),
            ("h2", "Questions"),
            ("p", "Write to <a href=\"mailto:%s\">%s</a> and we will point you at the exact setting." % (SUPPORT_EMAIL, SUPPORT_EMAIL)),
        ],
        None,
    ),
]

TEMPLATE = """<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>{title} — PharmTrack</title>
    <meta name="description" content="{description}" />
    <link rel="canonical" href="{canonical}" />
    <link rel="icon" href="assets/img/favicon.png" type="image/png" />
    <link rel="apple-touch-icon" href="assets/img/apple-touch-icon.png" />
    <meta name="theme-color" content="#f4f8f7" />
    <meta name="color-scheme" content="light" />
    <meta property="og:type" content="website" />
    <meta property="og:site_name" content="PharmTrack" />
    <meta property="og:title" content="{title} — PharmTrack" />
    <meta property="og:description" content="{description}" />
    <meta property="og:url" content="{canonical}" />
    <meta property="og:image" content="{site}/assets/img/og.png" />
    <meta name="twitter:card" content="summary_large_image" />
    <link rel="stylesheet" href="assets/css/base.css" />
    <link rel="stylesheet" href="assets/css/shell.css" />
    <style>
      .s-page-note {{
        background: var(--accent-soft);
        border-left: 4px solid var(--accent);
        border-radius: 0 14px 14px 0;
        padding: 20px 24px;
        margin: 1.5rem 0;
      }}
      .s-page-note p,
      .s-page-note li {{
        color: var(--ink);
      }}
      .s-page-note ul {{
        padding-left: 1.25em;
        margin: 0.75rem 0 0;
      }}
      .s-page-note li {{
        margin: 0.35rem 0;
      }}
      .s-page-foot {{
        border-top: 1px solid var(--line);
        margin-top: 3rem;
        padding-top: 1.5rem;
        font-size: 0.95rem;
      }}
    </style>
  </head>
  <body>
    <div class="site">
      <main class="s-page">
        <div class="s-page-in">
          <a class="s-page-back" href="/">← Back to PharmTrack</a>
          <h1>{title}</h1>
          <p class="s-page-lede">{lede}</p>
{body}
          <p class="s-page-foot">
            Questions? <a href="mailto:{support_email}">{support_email}</a>
          </p>
        </div>
      </main>
    </div>
  </body>
</html>
"""

DESCRIPTIONS = {
    "privacy": "How PharmTrack handles your data: everything you enter stays on your device, and nothing is collected, sold or shared.",
    "terms": "The terms that apply when you download and use PharmTrack, including the medical disclaimer and data loss terms.",
    "eula": "The End User License Agreement for PharmTrack, covering the licence grant, your data ownership and the medical disclaimer.",
    "support": "Get help with PharmTrack: answers to common questions, and how to report a bug.",
    "delete-account": "PharmTrack has no account and no server. Here is how to permanently delete the health data stored on your device.",
}


def render(blocks):
    out = []
    for kind, text in blocks:
        if kind == "h2":
            out.append("          <h2>%s</h2>" % text)
        elif kind == "p":
            out.append("          <p>%s</p>" % text)
        elif kind == "ul":
            out.append("          <ul>\n            %s\n          </ul>" % text)
        elif kind == "note":
            out.append('          <div class="s-page-note">\n            %s\n          </div>' % text)
        else:
            sys.exit("unknown block kind: " + kind)
    return "\n".join(out)


def main():
    for slug, title, lede, blocks, foot in PAGES:
        html = TEMPLATE.format(
            title=title,
            description=DESCRIPTIONS[slug],
            canonical="%s/%s.html" % (SITE, slug),
            lede=lede,
            body=render(blocks),
            support_email=SUPPORT_EMAIL,
            site=SITE,
        )
        path = os.path.join(ROOT, "%s.html" % slug)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(html)
        print("legal: %s" % os.path.basename(path))


if __name__ == "__main__":
    main()
