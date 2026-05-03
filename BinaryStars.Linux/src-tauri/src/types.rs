use serde::{Serialize, Deserialize};
use bluer::Session;
use tokio::sync::mpsc;
use std::sync::Mutex;

#[derive(Clone, Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct BluetoothMessage {
    pub sender: String,
    pub content: String,
    pub is_file: bool,
    pub file_name: Option<String>,
    pub base64_data: Option<String>,
    pub sent_at: u128,
}

pub struct BluetoothState {
    pub messages: Mutex<Vec<BluetoothMessage>>,
    pub tx: Mutex<Option<mpsc::UnboundedSender<String>>>,
    pub session: Mutex<Option<Session>>,
    pub connected_device_id: Mutex<Option<String>>,
}

pub struct AppState {
    pub bluetooth: BluetoothState,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InstalledAppItem {
    pub name: String,
    pub exec: String,
    pub icon: Option<String>,
    pub categories: Option<String>,
    pub no_display: bool,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RunningAppItem {
    pub main_pid: i32,
    pub pid: i32,
    pub name: String,
    pub exe: String,
    pub cpu_usage: f32,
    pub memory_mb: f64,
    pub command_line: String,
    pub process_count: usize,
    pub pids: Vec<i32>,
    pub has_visible_window: bool,
}

#[derive(Clone)]
pub struct RunningProcessSnapshot {
    pub pid: u32,
    pub ppid: u32,
    pub name: String,
    pub exe: String,
    pub cpu_usage: f32,
    pub memory_mb: f64,
    pub command_line: String,
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

#[derive(Serialize)]
pub struct NativeLocation {
    pub latitude: f64,
    pub longitude: f64,
    pub accuracy_meters: Option<f64>,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct LinuxBluetoothDevice {
    pub name: String,
    pub address: String,
    pub connected: bool,
    pub paired: bool,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompatibilityReport {
    pub sdptool_installed: bool,
    pub bluez_compat_mode: bool,
    pub can_open_socket: bool,
}

#[derive(Serialize)]
pub struct ElevationStatus {
    pub is_root: bool,
}
