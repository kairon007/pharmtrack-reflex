import { Link } from 'react-router-dom';
import { Pill } from 'lucide-react';

const Navbar = () => {
  return (
    <header className="navbar-container">
      <nav className="navbar glass-panel">
        <Link to="/" className="navbar-logo">
          <Pill className="logo-icon" size={28} />
          <span>PharmTrack</span>
        </Link>
        <div className="navbar-links">
          <a href="/#features">Features</a>
          <a href="/#home" className="btn-nav-download">Download</a>
          <div className="navbar-legal">
            <Link to="/privacy.html">Privacy</Link>
            <Link to="/terms.html">Terms</Link>
          </div>
        </div>
      </nav>
      <style>{`
        .navbar-container {
          position: sticky;
          top: 0;
          z-index: 100;
          padding: 1rem 1.5rem;
        }

        .navbar {
          max-width: 1200px;
          margin: 0 auto;
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 1rem 2rem;
          background: rgba(10, 12, 26, 0.7);
        }

        .navbar-logo {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          font-size: 1.5rem;
          font-weight: 800;
          color: var(--text-primary);
        }

        .logo-icon {
          color: var(--accent-primary);
          filter: drop-shadow(0 0 8px var(--accent-glow));
        }

        .navbar-links {
          display: flex;
          align-items: center;
          gap: 2rem;
        }

        .navbar-links a {
          color: var(--text-secondary);
          font-weight: 500;
          font-size: 1.05rem;
        }

        .navbar-links a:hover {
          color: var(--text-primary);
        }

        .btn-nav-download {
          background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
          color: white !important;
          padding: 0.5rem 1.25rem;
          border-radius: 9999px;
          font-weight: 600;
          transition: transform 0.2s, box-shadow 0.2s;
        }

        .btn-nav-download:hover {
          transform: scale(1.05);
          box-shadow: 0 0 20px var(--accent-glow);
        }

        .navbar-legal {
          display: flex;
          gap: 1.5rem;
          margin-left: 2rem;
          padding-left: 2rem;
          border-left: 1px solid var(--glass-border);
        }

        .navbar-legal a {
          font-size: 0.95rem;
          color: var(--text-muted);
        }

        .navbar-legal a:hover {
          color: var(--text-secondary);
        }

        @media (max-width: 768px) {
          .navbar-links {
            display: none; /* simple mobile view for now */
          }
        }
      `}</style>
    </header>
  );
};

export default Navbar;
