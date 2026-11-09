import { motion } from 'framer-motion';

const Showcase = () => {
  const screenshots = [
    { title: 'Dashboard View', description: 'At-a-glance health summary', img: '/log your health history.png' },
    { title: 'Medications List', description: 'Schedule and inventory', img: '/track medications daily.png' },
    { title: 'Consultations', description: 'Doctor visit records', img: '/never miss an appointment.png' },
    { title: 'Vitals Chart', description: 'Interactive history graphs', img: '/monitor your vitals.png' },
  ];

  return (
    <section id="download" className="showcase-section container">
      <div className="section-header">
        <h2 className="section-title">See It in <span className="text-gradient">Action</span></h2>
        <p className="section-subtitle">Experience a fluid, intuitive interface designed for humans.</p>
      </div>

      <div className="showcase-grid">
        {screenshots.map((s, i) => (
          <motion.div 
            key={i} 
            className="showcase-item glass-panel"
            initial={{ opacity: 0, scale: 0.9 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true }}
            transition={{ delay: i * 0.1, duration: 0.5 }}
            whileHover={{ y: -10 }}
          >
            <div className="showcase-image-placeholder">
              <img src={s.img} alt={s.title} style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '8px' }} />
            </div>
            <div className="showcase-info">
              <h3>{s.title}</h3>
              <p>{s.description}</p>
            </div>
          </motion.div>
        ))}
      </div>

      <div className="cta-banner glass-panel">
        <div className="cta-content">
          <h2>Ready to Get Started?</h2>
          <p>Download PharmTrack today and take control of your health natively on iOS and Android.</p>
          <a href="/#home" className="btn-primary" style={{ marginTop: '2rem' }}>Download Now</a>
        </div>
      </div>

      <style>{`
        .showcase-section {
          padding: 6rem 1.5rem;
        }

        .showcase-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
          gap: 2rem;
          margin-bottom: 6rem;
        }

        .showcase-item {
          padding: 1rem;
          display: flex;
          flex-direction: column;
          gap: 1.5rem;
        }

        .showcase-image-placeholder {
          background: rgba(0,0,0,0.2);
          border-radius: 12px;
          height: 500px;
          padding: 0.75rem;
          border: 1px solid rgba(255,255,255,0.05);
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
        }

        .showcase-info {
          text-align: center;
        }

        .showcase-info h3 {
          font-size: 1.1rem;
          color: var(--text-primary);
          margin-bottom: 0.25rem;
        }

        .showcase-info p {
          font-size: 0.9rem;
          color: var(--text-secondary);
        }

        .cta-banner {
          background: linear-gradient(135deg, rgba(59, 130, 246, 0.1), rgba(139, 92, 246, 0.1));
          border: 1px solid var(--glass-border);
          padding: 4rem 2rem;
          text-align: center;
          border-radius: 30px;
          position: relative;
          overflow: hidden;
        }

        .cta-banner::before {
          content: '';
          position: absolute;
          top: -50%;
          left: -50%;
          width: 200%;
          height: 200%;
          background: radial-gradient(circle, rgba(255,255,255,0.05) 0%, transparent 60%);
          transform: translate(10%, 10%);
          pointer-events: none;
        }

        .cta-content {
          position: relative;
          z-index: 1;
          max-width: 600px;
          margin: 0 auto;
        }

        .cta-content h2 {
          font-size: 2.5rem;
          margin-bottom: 1rem;
        }

        .cta-content p {
          color: var(--text-secondary);
          font-size: 1.1rem;
        }
      `}</style>
    </section>
  );
};

export default Showcase;
