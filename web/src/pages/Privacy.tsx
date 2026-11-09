const Privacy = () => {
  return (
    <div className="legal-page container glass-panel">
      <h1>Privacy Policy</h1>
      <div className="last-updated">Last updated: March 2024</div>

      <h2>1. Introduction</h2>
      <p>PharmTrack ("we", "us", "our", or "Company") is committed to protecting your privacy. This Privacy Policy explains how we handle information in our application and on our website.</p>

      <h2>2. Data We Collect</h2>
      <p>PharmTrack is designed with privacy as a core principle. We collect minimal data:</p>
      <ul>
          <li><strong>Health Data You Enter:</strong> Medications, consultations, appointments, vital signs - stored entirely on your device</li>
          <li><strong>Device Information:</strong> Only basic info needed for app functionality (OS version, app version)</li>
          <li><strong>Analytics:</strong> None. We do not track your usage or behavior</li>
          <li><strong>Identifiers:</strong> We do not collect any personally identifiable information</li>
      </ul>

      <h2>3. How We Use Your Data</h2>
      <p>All health data you enter into PharmTrack is stored exclusively on your device. We:</p>
      <ul>
          <li>Do NOT upload your data to any server</li>
          <li>Do NOT share your data with third parties</li>
          <li>Do NOT sell your data</li>
          <li>Do NOT use your data for advertising</li>
          <li>Do NOT analyze your health information</li>
      </ul>

      <h2>4. Data Storage</h2>
      <p>Your health information is stored locally on your device using encrypted storage. We have no access to this data and cannot retrieve it.</p>
      <ul>
          <li><strong>iOS:</strong> Data stored in app's encrypted sandbox</li>
          <li><strong>Android:</strong> Data stored in app's encrypted sandbox</li>
          <li><strong>Backup:</strong> Only backed up if you enable device backup</li>
      </ul>

      <h2>5. Data Security</h2>
      <p>We implement strong security measures:</p>
      <ul>
          <li>Encrypted local storage on your device</li>
          <li>No network transmission of health data</li>
          <li>Regular security updates</li>
          <li>No accounts or logins required</li>
      </ul>

      <h2>6. Third-Party Services</h2>
      <p>PharmTrack does NOT use any third-party analytics, advertising, or tracking services. We do not integrate with:</p>
      <ul>
          <li>Google Analytics or similar</li>
          <li>Ad networks</li>
          <li>Health data platforms</li>
          <li>Cloud storage services</li>
      </ul>

      <h2>7. Children's Privacy</h2>
      <p>PharmTrack does not knowingly collect information from children under 13. Parents may use the app to manage their child's medications under parental supervision.</p>

      <h2>8. Data Deletion</h2>
      <p>You have complete control over your data:</p>
      <ul>
          <li>Delete individual entries anytime from within the app</li>
          <li>Clear all data by uninstalling the app</li>
          <li>No cloud copies mean no data recovery needed</li>
      </ul>

      <h2>9. Website Cookies</h2>
      <p>Our website does not use cookies for tracking. We may use technical cookies only for essential functionality.</p>

      <h2>10. Changes to This Policy</h2>
      <p>We may update this policy occasionally. We will notify users of significant changes through the app or website.</p>

      <h2>11. Contact Us</h2>
      <p>For privacy questions or concerns:</p>
      <ul>
          <li>Email: <a href="mailto:privacy@pharmtrack.app">privacy@pharmtrack.app</a></li>
          <li>Website: <a href="#">pharmtrack.app</a></li>
      </ul>

      <h2>12. Legal Compliance</h2>
      <p>PharmTrack complies with:</p>
      <ul>
          <li>GDPR (General Data Protection Regulation)</li>
          <li>HIPAA principles for health data security</li>
          <li>CCPA (California Consumer Privacy Act)</li>
          <li>App Store and Play Store privacy guidelines</li>
      </ul>

      <div className="summary-box">
        <h2>Summary</h2>
        <p><strong>PharmTrack is fundamentally different from other health apps:</strong></p>
        <ul>
            <li>✅ Your data stays on your device</li>
            <li>✅ We never access your health information</li>
            <li>✅ No accounts or logins</li>
            <li>✅ No tracking or analytics</li>
            <li>✅ No ads</li>
            <li>✅ Complete privacy and control</li>
        </ul>
      </div>

      <style>{`
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

        .legal-page li {
          margin-bottom: 0.5rem;
        }

        .summary-box {
          margin-top: 4rem;
          padding: 2rem;
          background: rgba(16, 185, 129, 0.05);
          border: 1px solid rgba(16, 185, 129, 0.2);
          border-radius: 16px;
        }

        .summary-box h2 {
          margin-top: 0;
          color: #10b981;
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

export default Privacy;
