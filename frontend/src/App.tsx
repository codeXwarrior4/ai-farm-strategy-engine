import { BrowserRouter, Routes, Route } from 'react-router-dom';
import StatusPage from './pages/StatusPage';
import FarmerProfilePage from './pages/FarmerProfilePage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<StatusPage />} />
        <Route path="/profile" element={<FarmerProfilePage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
