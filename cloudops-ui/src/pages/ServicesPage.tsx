import { useState } from 'react'
import ServiceHealthList, { SignalTable } from '@/components/ServiceHealthList'

export default function ServicesPage() {
  const [selectedId, setSelectedId] = useState<string | null>(null)

  return (
    <div className="flex gap-6 h-full">
      {/* ── Service list ─────────────────────────────────────────────── */}
      <div className="w-72 shrink-0">
        <h2 className="text-base font-semibold mb-3">Service Health</h2>
        <ServiceHealthList
          onSelectService={setSelectedId}
          selectedServiceId={selectedId}
        />
      </div>

      {/* ── Signal detail ────────────────────────────────────────────── */}
      <div className="flex-1 min-w-0">
        {selectedId ? (
          <div className="space-y-3">
            <h2 className="text-base font-semibold">Recent Signals</h2>
            <p className="text-xs text-gray-400">Polling every 10 s · newest first</p>
            <SignalTable serviceId={selectedId} />
          </div>
        ) : (
          <div className="flex h-48 items-center justify-center text-sm text-gray-400 border border-dashed border-border rounded-lg">
            Select a service to view its recent signals
          </div>
        )}
      </div>
    </div>
  )
}
