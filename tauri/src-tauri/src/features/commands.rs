use std::process::Command;
use std::fs;
use std::time::Duration;
use tokio::time::sleep;

fn run_cmd(program: &str, args: &[&str]) -> Option<String> {
    let output = Command::new(program)
        .args(args)
        .output()
        .ok()?;
    if output.status.success() {
        Some(String::from_utf8_lossy(&output.stdout).trim().to_string())
    } else {
        None
    }
}

async fn get_occupied_cpu() -> String {
    let get_cpu_times = || -> Option<(u64, u64)> {
        let stat = fs::read_to_string("/proc/stat").ok()?;
        let line = stat.lines().next()?;
        if !line.starts_with("cpu") {
            return None;
        }
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() < 5 {
            return None;
        }
        let mut total = 0;
        let mut idle = 0;
        for (i, val_str) in parts.iter().enumerate() {
            if i == 0 { continue; }
            if let Ok(val) = val_str.parse::<u64>() {
                total += val;
                if i == 4 || i == 5 {
                    idle += val;
                }
            }
        }
        Some((total, idle))
    };

    if let Some((t1, i1)) = get_cpu_times() {
        sleep(Duration::from_millis(200)).await;
        if let Some((t2, i2)) = get_cpu_times() {
            let total_delta = t2.saturating_sub(t1);
            let idle_delta = i2.saturating_sub(i1);
            if total_delta > 0 {
                let usage = 100.0 * (1.0 - (idle_delta as f64) / (total_delta as f64));
                return format!("{:.1}%", usage);
            }
        }
    }
    "N/A".to_string()
}

pub async fn get_device_info_string() -> String {
    if !cfg!(target_os = "linux") {
        return "not supported yet".to_string();
    }
    
    // 1. IP & Interface
    let mut interface = None;
    let mut ip_addr = None;
    if let Some(route_out) = run_cmd("ip", &["route", "get", "1.1.1.1"]) {
        let parts: Vec<&str> = route_out.split_whitespace().collect();
        for i in 0..parts.len() {
            if parts[i] == "dev" && i + 1 < parts.len() {
                interface = Some(parts[i + 1].to_string());
            }
            if parts[i] == "src" && i + 1 < parts.len() {
                ip_addr = Some(parts[i + 1].to_string());
            }
        }
    }
    
    if interface.is_none() {
        if let Ok(entries) = fs::read_dir("/sys/class/net") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name != "lo" {
                    if let Ok(operstate) = fs::read_to_string(entry.path().join("operstate")) {
                        if operstate.trim() == "up" {
                            interface = Some(name);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    if ip_addr.is_none() {
        if let Some(iface) = &interface {
            if let Some(addr_out) = run_cmd("ip", &["addr", "show", iface]) {
                for line in addr_out.lines() {
                    let trimmed = line.trim();
                    if trimmed.starts_with("inet ") {
                        let parts: Vec<&str> = trimmed.split_whitespace().collect();
                        if parts.len() >= 2 {
                            if let Some(idx) = parts[1].find('/') {
                                ip_addr = Some(parts[1][..idx].to_string());
                            } else {
                                ip_addr = Some(parts[1].to_string());
                            }
                            break;
                        }
                    }
                }
            }
        }
    }
    
    // 2. MAC
    let mut mac_addr = None;
    if let Some(iface) = &interface {
        if let Ok(mac) = fs::read_to_string(format!("/sys/class/net/{}/address", iface)) {
            mac_addr = Some(mac.trim().to_string());
        }
    }
    if mac_addr.is_none() {
        if let Ok(entries) = fs::read_dir("/sys/class/net") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name != "lo" {
                    if let Ok(mac) = fs::read_to_string(entry.path().join("address")) {
                        let mac_trim = mac.trim().to_string();
                        if !mac_trim.is_empty() && mac_trim != "00:00:00:00:00:00" {
                            mac_addr = Some(mac_trim);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    // 3. WiFi Speed / Link Speed
    let mut wifi_speed = None;
    if let Some(iface) = &interface {
        if let Some(iw_out) = run_cmd("iw", &["dev", iface, "link"]) {
            let mut rx_rate = None;
            let mut tx_rate = None;
            for line in iw_out.lines() {
                let trimmed = line.trim();
                if trimmed.starts_with("rx bitrate:") {
                    rx_rate = Some(trimmed.replace("rx bitrate:", "").trim().to_string());
                } else if trimmed.starts_with("tx bitrate:") {
                    tx_rate = Some(trimmed.replace("tx bitrate:", "").trim().to_string());
                }
            }
            if rx_rate.is_some() || tx_rate.is_some() {
                wifi_speed = Some(format!(
                    "Up: {} | Down: {}",
                    tx_rate.unwrap_or_else(|| "N/A".to_string()),
                    rx_rate.unwrap_or_else(|| "N/A".to_string())
                ));
            }
        }
    }
    if wifi_speed.is_none() {
        if let Some(iface) = &interface {
            if let Ok(speed) = fs::read_to_string(format!("/sys/class/net/{}/speed", iface)) {
                wifi_speed = Some(format!("Link Speed: {} Mbps", speed.trim()));
            }
        }
    }
    
    // 4. Storage
    let mut occupied_storage = "N/A".to_string();
    if let Some(df_out) = run_cmd("df", &["-h", "/"]) {
        let lines: Vec<&str> = df_out.lines().collect();
        if lines.len() >= 2 {
            let parts: Vec<&str> = lines[1].split_whitespace().collect();
            if parts.len() >= 5 {
                occupied_storage = format!("{} / {} ({} occupied, {} available)", parts[2], parts[1], parts[4], parts[3]);
            }
        }
    }
    
    // 5. Battery
    let mut battery_info = "N/A".to_string();
    if let Ok(entries) = fs::read_dir("/sys/class/power_supply") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with("BAT") {
                let cap = fs::read_to_string(entry.path().join("capacity")).map(|c| c.trim().to_string()).unwrap_or_default();
                let stat = fs::read_to_string(entry.path().join("status")).map(|s| s.trim().to_string()).unwrap_or_default();
                if !cap.is_empty() {
                    if !stat.is_empty() {
                        battery_info = format!("{}% ({})", cap, stat);
                    } else {
                        battery_info = format!("{}%", cap);
                    }
                    break;
                }
            }
        }
    }
    if battery_info == "N/A" {
        if std::path::Path::new("/sys/class/power_supply/AC").exists() {
            battery_info = "AC Powered (No Battery)".to_string();
        }
    }
    
    // 6. RAM
    let mut occupied_ram = "N/A".to_string();
    if let Ok(mem_str) = fs::read_to_string("/proc/meminfo") {
        let mut total_kb = None;
        let mut avail_kb = None;
        for line in mem_str.lines() {
            if line.starts_with("MemTotal:") {
                total_kb = line.split_whitespace().nth(1).and_then(|s| s.parse::<u64>().ok());
            } else if line.starts_with("MemAvailable:") {
                avail_kb = line.split_whitespace().nth(1).and_then(|s| s.parse::<u64>().ok());
            }
        }
        if let (Some(total), Some(avail)) = (total_kb, avail_kb) {
            let used = total.saturating_sub(avail);
            let total_gb = (total as f64) / 1024.0 / 1024.0;
            let used_gb = (used as f64) / 1024.0 / 1024.0;
            let pct = if total > 0 { (used * 100) / total } else { 0 };
            occupied_ram = format!("{:.2} GB / {:.2} GB ({}% occupied)", used_gb, total_gb, pct);
        }
    }
    
    // 7. CPU
    let occupied_cpu = get_occupied_cpu().await;
    
    format!(
        "--- Device Info (Linux) ---\n\
        MAC: {}\n\
        IP: {}\n\
        WiFi: {}\n\
        Occupied Storage: {}\n\
        Battery: {}\n\
        Occupied RAM: {}\n\
        Occupied CPU: {}",
        mac_addr.unwrap_or_else(|| "N/A".to_string()),
        ip_addr.unwrap_or_else(|| "N/A".to_string()),
        wifi_speed.unwrap_or_else(|| "N/A".to_string()),
        occupied_storage,
        battery_info,
        occupied_ram,
        occupied_cpu
    )
}
