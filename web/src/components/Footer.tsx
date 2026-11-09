import { Link } from 'react-router-dom';

const Footer = () => {
  return (
    <footer className="footer-container glass-panel">
      <div className="footer-content">
        <div className="footer-section">
          <h3>PharmTrack</h3>
          <p>Your personal medication and health management assistant.</p>
        </div>
        <div className="footer-section">
          <h3>Quick Links</h3>
          <ul>
            <li><a href="/#features">Features</a></li>
            <li><a href="/#download">Download</a></li>
            <li><a href="mailto:support@pharmtrack.app">Support</a></li>
          </ul>
        </div>
        <div className="footer-section">
          <h3>Legal</h3>
          <ul>
            <li><Link to="/privacy.html">Privacy Policy</Link></li>
            <li><Link to="/terms.html">Terms of Service</Link></li>
            <li><Link to="/eula.html">EULA</Link></li>
          </ul>
        </div>
      </div>
      <div className="footer-bottom">
        <p>&copy; {new Date().getFullYear()} PharmTrack. All rights reserved. | Privacy & Security First</p>
      </div>

      <style>{`
        .footer-container {
          margin-top: 6rem;
          border-radius: 40px 40px 0 0;
          border-bottom: none;
          padding: 4rem 2rem 2rem;
          background: rgba(10, 12, 26, 0.4);
        }

        .footer-content {
          max-width: 1200px;
          margin: 0 auto;
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
          gap: 3rem;
          margin-bottom: 3rem;
        }

        .footer-section h3 {
          font-size: 1.25rem;
          margin-bottom: 1.5rem;
          color: var(--text-primary);
          background: var(--accent-gradient-text);
          -webkit-background-clip: text;
          background-clip: text;
          display: inline-block;
        }

        .footer-section p {
          color: var(--text-secondary);
          max-width: 300px;
        }

        .footer-section ul {
          list-style: none;
        }

        .footer-section li {
          margin-bottom: 1rem;
        }

        .footer-section a {
          color: var(--text-secondary);
        }

        .footer-section a:hover {
          color: var(--text-primary);
          padding-left: 0.25rem;
        }

        .footer-bottom {
          border-top: 1px solid var(--glass-border);
          padding-top: 2rem;
          text-align: center;
          color: var(--text-muted);
          font-size: 0.9rem;
        }
      `}</style>
    </footer>
  );
};

export default Footer;
