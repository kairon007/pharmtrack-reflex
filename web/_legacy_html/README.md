# PharmTrack Web Landing Page

This folder contains the landing page and legal documents for PharmTrack app.

## Files

### Main Pages
- **index.html** - Main landing page with features and download links
- **privacy.html** - Privacy Policy
- **terms.html** - Terms of Service
- **eula.html** - End User License Agreement

## Features

- ✅ Fully responsive design (mobile, tablet, desktop)
- ✅ Clean, modern styling with PharmTrack branding
- ✅ Accessibility compliant
- ✅ No external dependencies (self-contained HTML/CSS)
- ✅ Fast loading and SEO-friendly

## Quick Start

### Local Testing
Simply open `index.html` in any web browser:
```bash
open index.html
```

### Deployment

#### Option 1: GitHub Pages (Free)
1. Push this folder to GitHub
2. Go to repository Settings → Pages
3. Select branch and folder
4. Your site will be available at `https://yourusername.github.io/swiftcrossstarter/web`

#### Option 2: Netlify (Free)
1. Connect your GitHub repository
2. Set build folder to `/web`
3. Deploy in seconds at your custom domain

#### Option 3: Custom Domain
Upload all HTML files to your web hosting provider via FTP/SSH.

## Customization

### Update Links
Edit the following in all HTML files:
- `https://your-domain.com` - Replace with your actual domain
- `support@pharmtrack.app` - Replace with your support email
- `privacy@pharmtrack.app` - Replace with your privacy email
- `legal@pharmtrack.app` - Replace with your legal email

### Update App Store Links
In `index.html`, update the download buttons:
```html
<a href="https://apps.apple.com/app/pharmtrack/idXXXXXXXXXX" class="btn btn-primary">📱 Download on App Store</a>
<a href="https://play.google.com/store/apps/details?id=com.pharmtrack" class="btn btn-primary">🤖 Get on Google Play</a>
```

### Add Screenshots
Replace placeholder screenshots in `index.html`:
```html
<div class="screenshot">
    <img src="screenshot1.png" alt="Dashboard View">
</div>
```

### Customize Branding
Update colors in the CSS `:root` section:
```css
:root {
    --primary: #3b82f6;      /* Main blue color */
    --primary-dark: #1e40af; /* Darker blue */
    --text-dark: #1f2937;    /* Text color */
    --text-light: #6b7280;   /* Light text */
}
```

## Legal Documents

All legal documents are pre-written for a health app with these key points:

### Privacy Policy
- ✅ Explains local-only data storage
- ✅ No tracking or analytics
- ✅ GDPR/HIPAA/CCPA compliance
- ✅ No third-party sharing

### Terms of Service
- ✅ Clear usage restrictions
- ✅ Medical disclaimer
- ✅ Limitation of liability
- ✅ User responsibilities

### EULA
- ✅ License grant and restrictions
- ✅ Data ownership clarification
- ✅ Medical device disclaimer
- ✅ Warranty disclaimers

## SEO Optimization

### Meta Tags to Add
Update `<head>` section in `index.html`:
```html
<meta name="description" content="PharmTrack - Your personal medication and health management assistant. Track medications, consultations, appointments, and vital signs.">
<meta name="keywords" content="medication tracker, health tracking, pharmacy, vitals, appointments, medical">
<meta property="og:title" content="PharmTrack - Medication & Health Tracking">
<meta property="og:description" content="Never miss a medication again with PharmTrack">
<meta property="og:image" content="path/to/og-image.png">
```

### Structured Data (JSON-LD)
Add to `<head>`:
```html
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  "name": "PharmTrack",
  "description": "Medication & health management app",
  "url": "https://your-domain.com",
  "applicationCategory": "MedicalApplication",
  "offers": {
    "@type": "Offer",
    "price": "0",
    "priceCurrency": "USD"
  }
}
</script>
```

## Performance Tips

1. **Minify CSS** - Use online minifiers before deployment
2. **Optimize Images** - Compress screenshots to <100KB each
3. **Cache Headers** - Set appropriate cache-control headers
4. **CDN** - Use a CDN for faster global delivery
5. **Gzip** - Enable gzip compression on your server

## Analytics (Optional)

### Google Analytics
Add to `<head>`:
```html
<script async src="https://www.googletagmanager.com/gtag/js?id=GA_MEASUREMENT_ID"></script>
<script>
  window.dataLayer = window.dataLayer || [];
  function gtag(){dataLayer.push(arguments);}
  gtag('js', new Date());
  gtag('config', 'GA_MEASUREMENT_ID');
</script>
```

## Support

For questions about the landing page:
- Check links in footer for support contact
- Review legal documents for policy questions
- Update email addresses as needed

## License

The landing page content is part of PharmTrack and subject to the same terms.

---

**Note:** Remember to update all placeholder domains, emails, and store links before going live!
