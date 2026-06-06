use tokio::sync::mpsc;
use bluer::Session;
use std::sync::Mutex as StdMutex;

#[derive(Clone, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BluetoothMessage {
    pub id: String,
    pub sender: String,
    pub content: String,
    pub is_file: bool,
    pub file_name: Option<String>,
    pub base64_data: Option<String>,
    pub file_path: Option<String>,
    pub sent_at: u64,
}

#[derive(Clone, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RecentChat {
    pub peer_id: String,
    pub last_message: String,
    pub last_msg_at: u64,
}

#[derive(Clone, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LinuxBluetoothDevice {
    pub name: String,
    pub address: String,
    pub connected: bool,
    pub paired: bool,
}

pub struct ConnectedClient {
    pub peer_id: String,
    pub peer_address: String,
    pub tx: mpsc::UnboundedSender<String>,
}

pub struct BluetoothState {
    pub session: StdMutex<Option<Session>>,
    pub tx: StdMutex<Option<mpsc::UnboundedSender<String>>>,
    pub connected_device_id: StdMutex<Option<String>>,
    pub connected_device_address: StdMutex<Option<String>>,
    pub messages: StdMutex<Vec<BluetoothMessage>>,
    pub password: StdMutex<Option<String>>,
    pub clients: StdMutex<std::collections::HashMap<String, ConnectedClient>>,
}

impl BluetoothState {
    pub fn new() -> Self {
        Self {
            session: StdMutex::new(None),
            tx: StdMutex::new(None),
            connected_device_id: StdMutex::new(None),
            connected_device_address: StdMutex::new(None),
            messages: StdMutex::new(Vec::new()),
            password: StdMutex::new(None),
            clients: StdMutex::new(std::collections::HashMap::new()),
        }
    }
}

pub struct AppState {
    pub bluetooth: BluetoothState,
}

