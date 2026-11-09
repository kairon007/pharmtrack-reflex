const Terms = () => {
  return (
    <div className="legal-page container glass-panel">
      <h1>Terms of Service</h1>
      <div className="last-updated">Last updated: March 2024</div>

      <h2>1. Acceptance of Terms</h2>
      <p>By downloading, installing, and using PharmTrack ("App"), you agree to be bound by these Terms of Service. If you do not agree to these terms, you may not use the App.</p>

      <h2>2. License Grant</h2>
      <p>PharmTrack grants you a limited, non-exclusive, non-transferable license to use the App for personal health management purposes on devices you own or control.</p>

      <h2>3. Restrictions</h2>
      <p>You agree NOT to:</p>
      <ul>
          <li>Modify, reverse engineer, or decompile the App</li>
          <li>Use the App for any illegal purposes</li>
          <li>Attempt to gain unauthorized access to the App or its data</li>
          <li>Distribute or reproduce the App without permission</li>
          <li>Use the App to harm yourself or others</li>
          <li>Violate any applicable laws or regulations</li>
      </ul>

      <h2>4. User Responsibility</h2>
      <p>You are solely responsible for:</p>
      <ul>
          <li>The accuracy of health information you enter</li>
          <li>Maintaining the confidentiality of your device</li>
          <li>Backing up your data if you wish to retain it</li>
          <li>Following medical advice from licensed healthcare providers</li>
          <li>Not using PharmTrack as a substitute for professional medical advice</li>
      </ul>

      <h2>5. Disclaimer of Medical Advice</h2>
      <div className="alert-box">
          <strong>IMPORTANT:</strong> PharmTrack is a personal health management tool, NOT a medical device or service. It:
          <ul>
              <li>Does not provide medical diagnosis or treatment</li>
              <li>Does not replace professional medical advice</li>
              <li>Is not intended to diagnose, treat, cure, or prevent disease</li>
              <li>Should not be used as a substitute for consulting healthcare providers</li>
          </ul>
          Always consult with qualified healthcare professionals for medical advice, diagnosis, or treatment.
      </div>

      <h2>6. No Warranty</h2>
      <p>PharmTrack is provided "AS IS" without warranty of any kind, express or implied. We do not warrant that the App will be uninterrupted, error-free, or meet your expectations.</p>

      <h2>7. Limitation of Liability</h2>
      <p>To the maximum extent permitted by law, PharmTrack and its developers shall not be liable for:</p>
      <ul>
          <li>Loss of data or inability to access your information</li>
          <li>Any indirect, incidental, or consequential damages</li>
          <li>Medical consequences or health-related damages</li>
          <li>Third-party claims or damages</li>
      </ul>

      <h2>8. Data Loss</h2>
      <p>Since all data is stored locally on your device:</p>
      <ul>
          <li>Uninstalling the App will delete all local data</li>
          <li>PharmTrack cannot recover deleted information</li>
          <li>Device loss or damage means data loss</li>
          <li>You are responsible for backing up important data</li>
      </ul>

      <h2>9. Intellectual Property Rights</h2>
      <p>PharmTrack and all its content are protected by copyright and other intellectual property laws. You may not use PharmTrack's name, logo, or branding without permission.</p>

      <h2>10. Changes to the App</h2>
      <p>We may update, modify, or discontinue PharmTrack at any time. Material changes will be communicated through the App or this website.</p>

      <h2>11. Termination</h2>
      <p>Your right to use PharmTrack is terminated automatically upon:</p>
      <ul>
          <li>Violation of these Terms of Service</li>
          <li>Uninstallation of the App from your device</li>
          <li>Discontinuation of the App by PharmTrack</li>
      </ul>

      <h2>12. Third-Party Services</h2>
      <p>PharmTrack may use third-party libraries and services. These are subject to their respective licenses and terms.</p>

      <h2>13. Export Controls</h2>
      <p>PharmTrack may be subject to export controls. You agree not to export or re-export the App in violation of applicable laws.</p>

      <h2>14. Governing Law</h2>
      <p>These Terms of Service are governed by applicable laws. Any disputes shall be resolved in accordance with those laws.</p>

      <h2>15. Severability</h2>
      <p>If any provision of these terms is found unenforceable, the remaining provisions shall remain in effect.</p>

      <h2>16. Entire Agreement</h2>
      <p>These Terms of Service, along with our Privacy Policy and EULA, constitute the entire agreement regarding PharmTrack.</p>

      <h2>17. Contact Information</h2>
      <p>For questions about these terms:</p>
      <ul>
          <li>Email: <a href="mailto:support@pharmtrack.app">support@pharmtrack.app</a></li>
          <li>Website: <a href="#">pharmtrack.app</a></li>
      </ul>

      <h2>18. Updates to Terms</h2>
      <p>We may update these terms at any time. Continued use of PharmTrack after changes constitutes acceptance of the new terms.</p>

      <style>{`
        /* Inheritting standard .legal-page styles from Privacy if we used global CSS, but scoping here as fallback */
        .legal-page {
          max-width: 900px;
          margin: 4rem auto;
          padding: 3rem 4rem;
          line-height: 1.8;
          color: var(--text-secondary);
        }

        .legal-page h1 {
          font-size: 2.5rem;
          color: var(--text-primary);
          margin-bottom: 0.5rem;
        }

        .legal-page h2 {
          font-size: 1.5rem;
          margin-top: 2.5rem;
          margin-bottom: 1rem;
          color: var(--text-primary);
        }

        .last-updated {
          color: var(--text-muted);
          font-style: italic;
          margin-bottom: 3rem;
          padding-bottom: 1.5rem;
          border-bottom: 1px solid var(--glass-border);
        }

        .legal-page ul {
          margin-left: 1.5rem;
          margin-bottom: 1.5rem;
        }

        .alert-box {
          margin-top: 2rem;
          padding: 1.5rem;
          background: rgba(239, 68, 68, 0.05);
          border-left: 4px solid var(--accent-primary);
          border-radius: 4px;
        }
        
        @media (max-width: 768px) {
          .legal-page {
            padding: 2rem 1.5rem;
            margin: 2rem auto;
          }
        }
      `}</style>
    </div>
  );
};

export default Terms;
