import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import LandingPage from './pages/LandingPage';
import Privacy from './pages/Privacy';
import Terms from './pages/Terms';
import EULA from './pages/EULA';

function App() {
  return (
    <Router>
      <div className="App">
        <Navbar />
        <main>
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/privacy.html" element={<Privacy />} />
            <Route path="/terms.html" element={<Terms />} />
            <Route path="/eula.html" element={<EULA />} />
            {/* Catch all to support extensionless URLs if needed, but retaining old .html paths for CI/CD backwards compatibility */}
            <Route path="/privacy" element={<Privacy />} />
            <Route path="/terms" element={<Terms />} />
            <Route path="/eula" element={<EULA />} />
          </Routes>
        </main>
        <Footer />
      </div>
    </Router>
  );
}

export default App;
