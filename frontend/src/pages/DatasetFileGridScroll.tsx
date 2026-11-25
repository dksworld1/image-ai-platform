import { useEffect, useRef, useState, type ReactNode } from 'react'

export default function DatasetFileGridScroll({ label, children }: { label: string; children: ReactNode }) {
  const viewport = useRef<HTMLDivElement>(null)
  const horizontalTrack = useRef<HTMLDivElement>(null)
  const [trackWidth, setTrackWidth] = useState(0)

  useEffect(() => {
    const region = viewport.current
    const table = region?.querySelector('table')
    if (!region || !table) return
    const measure = () => setTrackWidth(region.scrollWidth > region.clientWidth ? region.scrollWidth : 0)
    const observer = new ResizeObserver(measure)
    observer.observe(region)
    observer.observe(table)
    measure()
    return () => observer.disconnect()
  }, [])

  return <div className="dataset-file-grid">
    <div ref={viewport} className="dataset-table-scroll" role="region" aria-label={label} tabIndex={0}
      onScroll={event => {
        const track = horizontalTrack.current
        if (track && track.scrollLeft !== event.currentTarget.scrollLeft) track.scrollLeft = event.currentTarget.scrollLeft
      }}>
      {children}
    </div>
    {trackWidth > 0 && <div ref={horizontalTrack} className="dataset-file-grid-horizontal-scroll" role="region"
      aria-label={`${label} 가로 스크롤`} tabIndex={0} onScroll={event => {
        const region = viewport.current
        if (region && region.scrollLeft !== event.currentTarget.scrollLeft) region.scrollLeft = event.currentTarget.scrollLeft
      }}>
      <div style={{ width: trackWidth }} />
    </div>}
  </div>
}
