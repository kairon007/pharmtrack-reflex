import { motion } from 'framer-motion';
import { Apple, Play } from 'lucide-react';

const Hero = () => {
  return (
    <section id="home" className="hero-section container">
      <motion.div 
        className="hero-content"
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8, ease: "easeOut" }}
      >
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.2, duration: 0.5 }}
          className="badge"
        >
          <span className="badge-dot"></span>
          Now available for iOS & Android
        </motion.div>
        
        <h1 className="hero-title">
          Take Control of Your <br />
          <span className="text-gradient">Health Journey</span>
        </h1>
        
        <p className="hero-subtitle">
          PharmTrack is your personal medication and health management assistant. 
          Never miss a dose, keep track of vitals, and maintain your records securely offline.
        </p>

        <div className="hero-cta">
          <motion.a
            href="https://apps.apple.com/us/app/pharmtrack/id6760744803"
            target="_blank"
            rel="noopener noreferrer"
            className="btn-primary"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            <Apple size={20} />
            Download for iOS
          </motion.a>
          <motion.a
            href="https://play.google.com/store/apps/details?id=com.tunnxo.medtrack"
            target="_blank"
            rel="noopener noreferrer"
            className="btn-secondary"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            <Play size={20} />
            Get on Google Play
          </motion.a>
        </div>
      </motion.div>

      <motion.div
        className="hero-visual animate-float"
        initial={{ opacity: 0, scale: 0.8 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ delay: 0.4, duration: 0.8 }}
      >
        <div className="phone-mockup glass-panel">
          <div className="screen">
             <img src="/log your health history.png" alt="PharmTrack Dashboard" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '30px' }} />
          </div>
        </div>
        <div className="glow-orb primary"></div>
        <div className="glow-orb secondary"></div>
      </motion.div>

      <style>{`
        .hero-section {
          padding: 8rem 1.5rem 4rem;
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 4rem;
          align-items: center;
          min-height: 90vh;
        }

        .hero-content {
          max-width: 600px;
        }

        .badge {
          display: inline-flex;
          align-items: center;
          gap: 0.5rem;
          background: rgba(255, 255, 255, 0.05);
          border: 1px solid var(--glass-border);
          padding: 0.5rem 1rem;
          border-radius: 9999px;
          font-size: 0.875rem;
          color: var(--text-secondary);
          margin-bottom: 2rem;
          backdrop-filter: blur(8px);
        }

        .badge-dot {
          width: 8px;
          height: 8px;
          background: #10b981;
          border-radius: 50%;
          box-shadow: 0 0 10px #10b981;
        }

        .hero-title {
          font-size: 4.5rem;
          line-height: 1.1;
          margin-bottom: 1.5rem;
          color: var(--text-primary);
        }

        .hero-subtitle {
          font-size: 1.25rem;
          color: var(--text-secondary);
          margin-bottom: 3rem;
          max-width: 90%;
        }

        .hero-cta {
          display: flex;
          gap: 1.5rem;
          flex-wrap: wrap;
        }

        /* Mockup Styles */
        .hero-visual {
          position: relative;
          display: flex;
          justify-content: center;
          align-items: center;
        }

        .phone-mockup {
          width: 300px;
          height: 600px;
          border-radius: 40px;
          padding: 0.5rem;
          background: rgba(255, 255, 255, 0.1);
          border: 1px solid rgba(255,255,255,0.2);
          box-shadow: inset 0 0 20px rgba(255,255,255,0.1), 0 20px 50px rgba(0,0,0,0.5);
          position: relative;
          z-index: 2;
        }

        .screen {
          background: var(--bg-secondary);
          border-radius: 35px;
          width: 100%;
          height: 100%;
          overflow: hidden;
          position: relative;
        }

        .glow-orb {
          position: absolute;
          width: 300px;
          height: 300px;
          border-radius: 50%;
          filter: blur(80px);
          z-index: 1;
        }

        .glow-orb.primary {
          background: var(--accent-glow);
          top: 10%;
          right: -10%;
        }

        .glow-orb.secondary {
          background: rgba(139, 92, 246, 0.3);
          bottom: -10%;
          left: 10%;
        }

        @media (max-width: 1024px) {
          .hero-title {
            font-size: 3.5rem;
          }
          .hero-section {
            grid-template-columns: 1fr;
            text-align: center;
          }
          .hero-content {
            margin: 0 auto;
          }
          .badge {
            margin: 0 auto 2rem;
          }
          .hero-subtitle {
            margin: 0 auto 3rem;
          }
          .hero-cta {
            justify-content: center;
          }
          .hero-visual {
            margin-top: 2rem;
          }
        }

        @media (max-width: 768px) {
          .hero-title {
            font-size: 2.5rem;
          }
        }
      `}</style>
    </section>
  );
};

export default Hero;
