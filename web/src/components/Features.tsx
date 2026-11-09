import { motion } from 'framer-motion';
import { Pill, Activity, Calendar, Stethoscope, WifiOff, ShieldCheck } from 'lucide-react';

const featureData = [
  {
    icon: Pill,
    title: 'Medication Tracker',
    description: "Track your medications, dosages, and schedules. Get reminded when it's time to take your pills.",
    color: '#3b82f6'
  },
  {
    icon: Stethoscope,
    title: 'Consultation Records',
    description: 'Store detailed notes from doctor visits, diagnoses, and treatment plans all in one place.',
    color: '#10b981'
  },
  {
    icon: Calendar,
    title: 'Appointment Manager',
    description: 'Never miss a medical appointment. Keep track of dates, times, and locations easily.',
    color: '#f59e0b'
  },
  {
    icon: Activity,
    title: 'Vitals Tracking',
    description: 'Monitor blood pressure, temperature, weight, and other vital signs with detailed history.',
    color: '#ef4444'
  },
  {
    icon: WifiOff,
    title: 'Works Offline',
    description: 'Access your health data anytime, anywhere. No internet connection required.',
    color: '#8b5cf6'
  },
  {
    icon: ShieldCheck,
    title: '100% Private',
    description: 'Your data stays on your device. We never collect, store, or share your health information.',
    color: '#ec4899'
  }
];

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.1
    }
  }
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { type: 'spring' as const, stiffness: 100 }
  }
};

const Features = () => {
  return (
    <section id="features" className="features-section container">
      <div className="section-header">
        <h2 className="section-title">Powerful <span className="text-gradient">Features</span></h2>
        <p className="section-subtitle">Everything you need to manage your daily health routines.</p>
      </div>

      <motion.div 
        className="features-grid"
        variants={containerVariants}
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true, margin: "-100px" }}
      >
        {featureData.map((feature, idx) => {
          const Icon = feature.icon;
          return (
            <motion.div 
              key={idx} 
              className="feature-card glass-panel"
              variants={itemVariants}
              whileHover={{ y: -5, scale: 1.02 }}
            >
              <div 
                className="feature-icon-wrapper"
                style={{ background: `linear-gradient(135deg, ${feature.color}22, ${feature.color}11)`, border: `1px solid ${feature.color}44` }}
              >
                <Icon size={24} color={feature.color} />
              </div>
              <h3>{feature.title}</h3>
              <p>{feature.description}</p>
            </motion.div>
          );
        })}
      </motion.div>

      <style>{`
        .features-section {
          padding: 6rem 1.5rem;
        }

        .section-header {
          text-align: center;
          margin-bottom: 4rem;
        }

        .section-title {
          font-size: 3rem;
          margin-bottom: 1rem;
        }

        .section-subtitle {
          font-size: 1.25rem;
          color: var(--text-secondary);
        }

        .features-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
          gap: 2rem;
        }

        .feature-card {
          padding: 2.5rem 2rem;
          transition: all 0.3s ease;
          position: relative;
          overflow: hidden;
        }

        .feature-card::before {
          content: '';
          position: absolute;
          top: 0; left: 0; right: 0;
          height: 1px;
          background: linear-gradient(90deg, transparent, rgba(255,255,255,0.1), transparent);
          transform: translateX(-100%);
          transition: transform 0.5s ease;
        }

        .feature-card:hover::before {
          transform: translateX(100%);
        }

        .feature-icon-wrapper {
          width: 50px;
          height: 50px;
          border-radius: 12px;
          display: flex;
          align-items: center;
          justify-content: center;
          margin-bottom: 1.5rem;
        }

        .feature-card h3 {
          font-size: 1.25rem;
          margin-bottom: 1rem;
          color: var(--text-primary);
        }

        .feature-card p {
          color: var(--text-secondary);
          line-height: 1.6;
          font-size: 0.95rem;
        }
      `}</style>
    </section>
  );
};

export default Features;
