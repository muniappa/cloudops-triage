import { BrowserRouter, Route, Routes } from 'react-router-dom'
import AppShell from '@/layouts/AppShell'
import IncidentQueue from '@/pages/IncidentQueue'
import ServicesPage from '@/pages/ServicesPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<IncidentQueue />} />
          <Route path="services" element={<ServicesPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
