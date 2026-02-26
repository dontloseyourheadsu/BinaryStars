import { MapContainer, Marker, Popup, TileLayer } from "react-leaflet";
import type { Device, LocationPoint } from "../../types";

type Props = {
  devices: Device[];
  selectedMapDevice: Device | null;
  selectedMapDeviceId: string;
  mapFocusPoint: LocationPoint | null;
  latestPoint: LocationPoint | null;
  history: LocationPoint[];
  mapDetailOpen: boolean;
  locationEnabled: boolean;
  locationMinutes: number;
  onSelectMapDevice: (deviceId: string) => void;
  onSetMapDetailOpen: (open: boolean) => void;
  onRefreshMapHistory: (deviceId: string) => void;
  onSelectHistoryPoint: (point: LocationPoint) => void;
  onSetLocationEnabled: (enabled: boolean) => void;
  onSetLocationMinutes: (minutes: number) => void;
};

export default function MapTab({
  devices,
  selectedMapDevice,
  selectedMapDeviceId,
  mapFocusPoint,
  latestPoint,
  history,
  mapDetailOpen,
  locationEnabled,
  locationMinutes,
  onSelectMapDevice,
  onSetMapDetailOpen,
  onRefreshMapHistory,
  onSelectHistoryPoint,
  onSetLocationEnabled,
  onSetLocationMinutes,
}: Props) {
  const activePoint = mapFocusPoint ?? latestPoint;
  const centerLatitude = activePoint?.latitude ?? 20;
  const centerLongitude = activePoint?.longitude ?? 0;

  return (
    <section className="panel-grid">
      {!mapDetailOpen && (
        <div className="panel">
          <h3>Devices</h3>
          <div className="list">
            {devices.map((device) => (
              <button
                className={`row-card ${selectedMapDeviceId === device.id ? "active" : ""}`}
                key={device.id}
                onClick={() => {
                  onSelectMapDevice(device.id);
                  onSetMapDetailOpen(true);
                }}
                type="button"
              >
                <strong>{device.name}</strong>
                <span className="muted">{device.isOnline ? "Online" : "Offline"}</span>
              </button>
            ))}
          </div>
          {devices.length === 0 && <p className="empty-state">No devices available</p>}
          <p className="section-label">Location Updates</p>
          <div className="panel inset-panel">
            <label className="inline">
              Share this device location in background
              <input checked={locationEnabled} onChange={(event) => onSetLocationEnabled(event.target.checked)} type="checkbox" />
            </label>
            <select
              aria-label="Location update interval"
              onChange={(event) => onSetLocationMinutes(Number(event.target.value))}
              value={locationMinutes}
            >
              <option value={15}>15 minutes</option>
              <option value={30}>30 minutes</option>
              <option value={60}>60 minutes</option>
            </select>
          </div>
        </div>
      )}
      {mapDetailOpen && (
        <div className="panel">
          <div className="split-row">
            <button className="ghost" onClick={() => onSetMapDetailOpen(false)} type="button">Back</button>
            <h3>{selectedMapDevice?.name ?? "Map"}</h3>
            <button onClick={() => selectedMapDeviceId && onRefreshMapHistory(selectedMapDeviceId)} type="button">Live</button>
          </div>
          <div className="map-wrap">
            <MapContainer
              center={[centerLatitude, centerLongitude]}
              key={activePoint ? `${activePoint.id}-${activePoint.recordedAt}` : "map-empty-center"}
              zoom={activePoint ? 15.5 : 2}
              style={{ height: "100%", width: "100%" }}
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              {activePoint && (
                <Marker position={[activePoint.latitude, activePoint.longitude]}>
                  <Popup>{activePoint.title}</Popup>
                </Marker>
              )}
            </MapContainer>
            {!activePoint && <div className="map-empty">No location history available</div>}
          </div>
          <p className="section-label">Location History</p>
          <div className="list compact">
            {history.map((point) => (
              <button className="row-card" key={point.id} onClick={() => onSelectHistoryPoint(point)} type="button">
                <strong>{point.title}</strong>
                <span className="muted">
                  {point.latitude.toFixed(5)}, {point.longitude.toFixed(5)} · {new Date(point.recordedAt).toLocaleString()}
                </span>
              </button>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
