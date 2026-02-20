use serde::Serialize;
use std::collections::{BTreeMap, HashMap};
use std::net::IpAddr;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![get_device_info])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

#[derive(Serialize)]
pub struct InterfaceInfo {
    pub name: String,
    pub ips: Vec<String>,
    pub rx_bytes_per_sec: u64,
    pub tx_bytes_per_sec: u64,
}

#[derive(Serialize)]
pub struct DeviceInfo {
    pub hostname: String,
    pub ip_address: String,
    pub ipv6_address: String,
    pub battery_level: Option<u8>,
    pub cpu_load_percent: Option<u8>,
    pub memory_load_percent: Option<u8>,
    pub wifi_upload_speed: String,
    pub wifi_download_speed: String,
    pub interfaces: Vec<InterfaceInfo>,
}

fn read_battery_percent() -> Option<u8> {
    let power_supply = std::path::Path::new("/sys/class/power_supply");
    let entries = std::fs::read_dir(power_supply).ok()?;

    for entry in entries.flatten() {
        let capacity_path = entry.path().join("capacity");
        let raw = std::fs::read_to_string(capacity_path).ok()?;
        let parsed = raw.trim().parse::<i32>().ok()?;
        if (0..=100).contains(&parsed) {
            return Some(parsed as u8);
        }
    }

    None
}

fn format_kbps(bytes_per_sec: u64) -> String {
    let kbps = (bytes_per_sec.saturating_mul(8)) / 1000;
    format!("{} kbps", kbps)
}

#[tauri::command]
pub async fn get_device_info() -> Result<DeviceInfo, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let hostname = match hostname::get() {
            Ok(h) => h.into_string().unwrap_or_else(|_| "unknown".to_string()),
            Err(_) => "unknown".to_string(),
        };

        let ifaces = match if_addrs::get_if_addrs() {
            Ok(v) => v,
            Err(e) => return Err(format!("if_addrs error: {}", e)),
        };

        let mut sys = sysinfo::System::new_all();
        sys.refresh_cpu();
        std::thread::sleep(std::time::Duration::from_millis(250));
        sys.refresh_cpu();
        sys.refresh_memory();

        let cpu_load_percent = {
            let raw = sys.global_cpu_info().cpu_usage();
            if raw.is_finite() {
                Some(raw.round().clamp(0.0, 100.0) as u8)
            } else {
                None
            }
        };

        let memory_load_percent = {
            let total = sys.total_memory();
            let used = sys.used_memory();
            if total == 0 {
                None
            } else {
                let pct = ((used as f64 / total as f64) * 100.0).round();
                Some(pct.clamp(0.0, 100.0) as u8)
            }
        };

        sys.refresh_networks();
        let mut initial: HashMap<String, (u64, u64)> = HashMap::new();
        for (name, net) in sys.networks() {
            initial.insert(name.clone(), (net.received(), net.transmitted()));
        }

        std::thread::sleep(std::time::Duration::from_secs(1));

        sys.refresh_networks();

        let mut addresses_by_interface: BTreeMap<String, Vec<String>> = BTreeMap::new();
        let mut ipv4_address: Option<String> = None;
        let mut ipv6_address: Option<String> = None;

        for iface in ifaces {
            let ip = iface.ip();
            let ip_text = ip.to_string();

            match ip {
                IpAddr::V4(v4) => {
                    if !v4.is_loopback() && ipv4_address.is_none() {
                        ipv4_address = Some(ip_text.clone());
                    }
                }
                IpAddr::V6(v6) => {
                    if !v6.is_loopback() && ipv6_address.is_none() {
                        ipv6_address = Some(ip_text.clone());
                    }
                }
            }

            let entry = addresses_by_interface
                .entry(iface.name)
                .or_default();
            if !entry.iter().any(|candidate| candidate == &ip_text) {
                entry.push(ip_text);
            }
        }

        if ipv4_address.is_none() {
            ipv4_address = Some("127.0.0.1".to_string());
        }

        if ipv6_address.is_none() {
            ipv6_address = Some("::1".to_string());
        }

        let mut interfaces = Vec::new();
        let mut total_rx_bytes_per_sec = 0u64;
        let mut total_tx_bytes_per_sec = 0u64;

        for (name, ips) in addresses_by_interface {
            let (rx, tx) = if let Some((r0, t0)) = initial.get(&name) {
                if let Some(net) = sys.networks().get(&name) {
                    let r1 = net.received();
                    let t1 = net.transmitted();
                    (r1.saturating_sub(*r0), t1.saturating_sub(*t0))
                } else {
                    (0u64, 0u64)
                }
            } else {
                (0u64, 0u64)
            };

            total_rx_bytes_per_sec = total_rx_bytes_per_sec.saturating_add(rx);
            total_tx_bytes_per_sec = total_tx_bytes_per_sec.saturating_add(tx);

            interfaces.push(InterfaceInfo {
                name,
                ips,
                rx_bytes_per_sec: rx,
                tx_bytes_per_sec: tx,
            });
        }

        Ok(DeviceInfo {
            hostname,
            ip_address: ipv4_address.unwrap_or_else(|| "127.0.0.1".to_string()),
            ipv6_address: ipv6_address.unwrap_or_else(|| "::1".to_string()),
            battery_level: read_battery_percent(),
            cpu_load_percent,
            memory_load_percent,
            wifi_upload_speed: format_kbps(total_tx_bytes_per_sec),
            wifi_download_speed: format_kbps(total_rx_bytes_per_sec),
            interfaces,
        })
    })
    .await
    .map_err(|e| format!("spawn error: {}", e))?
}
