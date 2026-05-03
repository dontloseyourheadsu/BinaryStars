use std::time::Duration;
use zbus::blocking::{fdo::PropertiesProxy, Connection, Proxy};
use zbus::names::InterfaceName;
use zbus::zvariant::{OwnedObjectPath, OwnedValue};
use crate::types::NativeLocation;

#[tauri::command]
pub async fn get_native_location() -> Result<NativeLocation, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let connection = Connection::system().map_err(|e| format!("dbus connection failed: {}", e))?;
        let manager = Proxy::new(&connection, "org.freedesktop.GeoClue2", "/org/freedesktop/GeoClue2/Manager", "org.freedesktop.GeoClue2.Manager").map_err(|e| format!("geoclue manager unavailable: {}", e))?;
        let client_path: OwnedObjectPath = manager.call("GetClient", &()).map_err(|e| format!("failed to create geoclue client: {}", e))?;
        let client_props = PropertiesProxy::new(&connection, "org.freedesktop.GeoClue2", client_path.as_str()).map_err(|e| format!("failed to create geoclue properties proxy: {}", e))?;
        let client_interface: InterfaceName<'static> = "org.freedesktop.GeoClue2.Client".try_into().map_err(|e| format!("invalid client interface name: {}", e))?;
        client_props.set(client_interface.clone(), "DesktopId", "com.tds.binarystars.linux".into()).map_err(|e| format!("failed to set geoclue DesktopId: {}", e))?;
        let client_proxy = Proxy::new(&connection, "org.freedesktop.GeoClue2", client_path.as_str(), "org.freedesktop.GeoClue2.Client").map_err(|e| format!("failed to create geoclue client proxy: {}", e))?;
        client_proxy.call::<_, _, ()>("Start", &()).map_err(|e| format!("failed to start geoclue client: {}", e))?;
        let mut location_path = String::from("/");
        for _ in 0..20 {
            let location_value: OwnedValue = client_props.get(client_interface.clone(), "Location").map_err(|e| format!("failed reading geoclue location path: {}", e))?;
            let next_path: OwnedObjectPath = <OwnedValue as TryInto<OwnedObjectPath>>::try_into(location_value).map_err(|e| format!("unexpected geoclue location path type: {}", e))?;
            let next_path = next_path.to_string();
            if next_path != "/" { location_path = next_path; break; }
            std::thread::sleep(Duration::from_millis(500));
        }
        if location_path == "/" {
            let _ = client_proxy.call::<_, _, ()>("Stop", &());
            let _ = manager.call::<_, _, ()>("DeleteClient", &(client_path.clone()));
            return Err("native location permission denied or no location fix available".to_string());
        }
        let location_props = PropertiesProxy::new(&connection, "org.freedesktop.GeoClue2", location_path.as_str()).map_err(|e| format!("failed to create geoclue location proxy: {}", e))?;
        let location_interface: InterfaceName<'static> = "org.freedesktop.GeoClue2.Location".try_into().map_err(|e| format!("invalid location interface name: {}", e))?;
        let latitude: f64 = location_props.get(location_interface.clone(), "Latitude").map_err(|e| format!("failed to read latitude: {}", e))?.try_into().map_err(|e| format!("invalid latitude value: {}", e))?;
        let longitude: f64 = location_props.get(location_interface.clone(), "Longitude").map_err(|e| format!("failed to read longitude: {}", e))?.try_into().map_err(|e| format!("invalid longitude value: {}", e))?;
        let accuracy_meters = location_props.get(location_interface, "Accuracy").ok().and_then(|value| value.try_into().ok());
        let _ = client_proxy.call::<_, _, ()>("Stop", &());
        let _ = manager.call::<_, _, ()>("DeleteClient", &(client_path));
        Ok(NativeLocation { latitude, longitude, accuracy_meters })
    }).await.map_err(|e| format!("spawn error: {}", e))?
}
