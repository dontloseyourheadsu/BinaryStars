use bluer::{rfcomm::{Profile, Role, Stream}, Session};
use futures::StreamExt;
use serde::{Deserialize, Serialize};
use std::io::{BufRead, Write};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader as TokioBufReader};

#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "type", content = "payload")]
enum Command {
    Listen { uuid: String },
    Connect { address: String, uuid: String },
    Send { data_base64: String },
    Disconnect,
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "type", content = "payload")]
enum Event {
    State { state: String },
    Message { data_base64: String },
    Error { message: String },
    Connected { address: String },
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let session = Session::new().await?;
    
    let stdin = std::io::stdin();
    let (tx, mut rx) = tokio::sync::mpsc::channel::<Command>(32);

    std::thread::spawn(move || {
        for line in stdin.lock().lines() {
            if let Ok(l) = line {
                if let Ok(cmd) = serde_json::from_str::<Command>(&l) {
                    let _ = tx.blocking_send(cmd);
                }
            }
        }
    });

    while let Some(cmd) = rx.recv().await {
        match cmd {
            Command::Listen { uuid } => {
                let uuid_parsed = match uuid.parse() {
                    Ok(u) => u,
                    Err(e) => {
                        send_event(Event::Error { message: format!("Invalid UUID: {}", e) });
                        continue;
                    }
                };
                let profile = Profile {
                    uuid: uuid_parsed,
                    role: Some(Role::Server),
                    name: Some("BinaryStarsSPP".to_string()),
                    channel: Some(1),
                    ..Default::default()
                };
                send_event(Event::State { state: "Listening".to_string() });
                let mut profile_handle = match session.register_profile(profile).await {
                    Ok(h) => h,
                    Err(e) => {
                        send_event(Event::Error { message: e.to_string() });
                        continue;
                    }
                };

                if let Some(req) = profile_handle.next().await {
                    if let Ok(stream) = req.accept() {
                        let peer_addr = stream.peer_addr().map(|a| a.addr.to_string()).unwrap_or_default();
                        send_event(Event::Connected { address: peer_addr });
                        handle_connection(stream, &mut rx).await;
                    }
                }
            }
            Command::Connect { address, uuid: _ } => {
                let mut clean_addr = address.replace(":", "").replace("-", "");
                if clean_addr.len() == 12 {
                    let mut formatted = String::new();
                    for (i, c) in clean_addr.chars().enumerate() {
                        if i > 0 && i % 2 == 0 { formatted.push(':'); }
                        formatted.push(c);
                    }
                    clean_addr = formatted;
                }

                let addr = match clean_addr.parse::<bluer::Address>() {
                    Ok(a) => a,
                    Err(e) => {
                        send_event(Event::Error { message: format!("Invalid Address {}: {}", clean_addr, e) });
                        continue;
                    }
                };

                send_event(Event::State { state: "Connecting".to_string() });
                
                match bluer::rfcomm::Stream::connect(bluer::rfcomm::SocketAddr { addr, channel: 1 }).await {
                    Ok(stream) => {
                        send_event(Event::Connected { address: address.clone() });
                        handle_connection(stream, &mut rx).await;
                    }
                    Err(e) => {
                        send_event(Event::Error { message: e.to_string() });
                    }
                }
            }
            _ => {}
        }
    }

    Ok(())
}

async fn handle_connection(stream: Stream, cmd_rx: &mut tokio::sync::mpsc::Receiver<Command>) {
    let (reader, mut writer) = stream.into_split();
    let mut reader = TokioBufReader::new(reader);
    let mut line = String::new();
    
    loop {
        tokio::select! {
            res = reader.read_line(&mut line) => {
                match res {
                    Ok(0) | Err(_) => break,
                    Ok(_) => {
                       let trimmed = line.trim_end_matches(['\r', '\n']);
                       send_event(Event::Message { data_base64: base64::Engine::encode(&base64::engine::general_purpose::STANDARD, trimmed.as_bytes()) });
                       line.clear();
                    }
                }
            }
            cmd = cmd_rx.recv() => {
                match cmd {
                    Some(Command::Send { data_base64 }) => {
                        if let Ok(data) = base64::Engine::decode(&base64::engine::general_purpose::STANDARD, data_base64) {
                            if writer.write_all(&data).await.is_err() { break; }
                            if writer.write_all(b"\n").await.is_err() { break; }
                            let _ = writer.flush().await;
                        }
                    }
                    Some(Command::Disconnect) | None => break,
                    _ => {}
                }
            }
        }
    }
    send_event(Event::State { state: "Disconnected".to_string() });
}

fn send_event(ev: Event) {
    if let Ok(json) = serde_json::to_string(&ev) {
        println!("{}", json);
        let _ = std::io::stdout().flush();
    }
}
